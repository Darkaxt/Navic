package karacken.curl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Production page-curl surface.
 *
 * <p>The client owns bitmap decoding and page identity. This view owns only interaction,
 * settlement, and GL resource lifecycle. Public mutations must run on the Android main thread.
 */
public class PageSurfaceView extends GLSurfaceView {
    private static final String TAG = "PageSurfaceView";
    private static final float FLING_THRESHOLD_PX_PER_SECOND = 200f;
    private static final long NO_GENERATION_ID = -1L;
    private static final int REQUIRED_DISPOSAL_CALLBACK_LIMIT = 1;
    private static final int AUXILIARY_DISPOSAL_CALLBACK_LIMIT = 2;
    private static final int OWNERSHIP_CALLBACK_LIMIT = 4;
    private static final int PRESENTED_FRAME_CALLBACK_LIMIT = 1;
    private static final int PAGE_OVERLAY_UPDATE_LIMIT = 1;
    private static final int MAIN_TERMINAL_ACTION_LIMIT =
            OWNERSHIP_CALLBACK_LIMIT + REQUIRED_DISPOSAL_CALLBACK_LIMIT;
    private static final long GL_QUEUE_ENTRY_WATCHDOG_MILLIS = 5_000L;
    public static final long NO_GESTURE_ID = -1L;
    public static final long NO_PRESENTED_FRAME_REQUEST_ID =
            PresentedFrameRequest.NO_REQUEST_ID;
    static final long NO_BOUNDARY_FRAME_TOKEN = 0L;
    private static final PageSurfaceListener NO_OP_LISTENER = new PageSurfaceListener() {};

    public interface MainTerminalExecutor {
        boolean execute(Runnable action);
    }

    public enum DisposalCallbackRegistration {
        ACCEPTED,
        DELIVERED_TERMINAL,
        CALLBACK_CAPACITY
    }

    private static final class OwnershipLeaseSample
            implements PageSurfaceOwnershipSnapshotCoordinator.MainSample {
        final long epoch;
        final int activeDeckLeases;
        final int pendingDeckLeases;
        final int releaseInFlightDeckLeases;
        final int orphanDeckLeases;
        final int deckLeaseLimit;

        OwnershipLeaseSample(
                long epoch,
                int activeDeckLeases,
                int pendingDeckLeases,
                int releaseInFlightDeckLeases,
                int orphanDeckLeases,
                int deckLeaseLimit) {
            this.epoch = epoch;
            this.activeDeckLeases = activeDeckLeases;
            this.pendingDeckLeases = pendingDeckLeases;
            this.releaseInFlightDeckLeases = releaseInFlightDeckLeases;
            this.orphanDeckLeases = orphanDeckLeases;
            this.deckLeaseLimit = deckLeaseLimit;
        }

        @Override
        public long ownershipEpoch() {
            return epoch;
        }

        @Override
        public PageSurfaceOwnershipSnapshot withTextures(
                int textures,
                int textureLimit) {
            return new PageSurfaceOwnershipSnapshot(
                    activeDeckLeases,
                    1,
                    pendingDeckLeases,
                    1,
                    releaseInFlightDeckLeases,
                    deckLeaseLimit,
                    orphanDeckLeases,
                    0,
                    textures,
                    textureLimit);
        }
    }

    private final PageDeckCoordinator<Bitmap> deckCoordinator =
            new PageDeckCoordinator<>(this::advanceOwnershipEpoch);
    private final DeckLeaseRegistry leaseRegistry =
            new DeckLeaseRegistry(this::advanceOwnershipEpoch);
    private final PageSurfaceDeckSubmissionGate<Bitmap> submissionGate =
            new PageSurfaceDeckSubmissionGate<>(deckCoordinator, leaseRegistry);
    private final BoundaryRestorationProtocol boundaryRestorationProtocol =
            new BoundaryRestorationProtocol();
    private final PresentedFrameRequest presentedFrameRequest =
            new PresentedFrameRequest();
    private final Set<Long> preparedGenerations = new LinkedHashSet<>();
    private final PageRenderer renderer;
    private final int touchSlop;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger pendingMainTerminalActions = new AtomicInteger();
    private final AtomicReference<Runnable> retainedOwnershipMainTerminalAction =
            new AtomicReference<>();
    private final AtomicReference<Runnable> retainedDisposalMainTerminalAction =
            new AtomicReference<>();
    private final AtomicReference<
                    PageOverlayPendingLease<List<PageOverlayImage<Bitmap>>>>
            pendingPageOverlayLease = new AtomicReference<>();
    private final PageSurfaceTerminalDisposalGate terminalDisposalGate =
            new PageSurfaceTerminalDisposalGate();
    private final FailureAccumulator disposalFailure =
            new FailureAccumulator();
    private final PageSurfaceRequiredTerminalCallback<PageSurfaceDisposalResult>
            requiredDisposeCallback = new PageSurfaceRequiredTerminalCallback<>(
                    (callback, failure) -> reportIsolatedCallbackFailure(
                            IsolatedCallbackKind.DISPOSAL,
                            callback,
                            failure));
    private final PageSurfaceTerminalCallbacks<PageSurfaceDisposalResult>
            disposeCallbacks = new PageSurfaceTerminalCallbacks<>(
                    AUXILIARY_DISPOSAL_CALLBACK_LIMIT,
                    (callback, failure) -> reportIsolatedCallbackFailure(
                            IsolatedCallbackKind.DISPOSAL,
                            callback,
                            failure));
    private final PageSurfaceTerminalCallbacks<PageSurfaceOwnershipResult>
            terminalOwnershipCallbacks = new PageSurfaceTerminalCallbacks<>(
                    OWNERSHIP_CALLBACK_LIMIT,
                    (callback, failure) -> reportIsolatedCallbackFailure(
                            IsolatedCallbackKind.OWNERSHIP,
                            callback,
                            failure));
    private final PageSurfaceOwnershipSnapshotCoordinator ownershipSnapshotCoordinator;
    private final PageSurfaceOwnershipRetryEdge ownershipRetryEdge;
    private Runnable ownershipCallbackCapacityListener;

    private PageSurfaceListener pageSurfaceListener = NO_OP_LISTENER;
    private RenderCapabilities renderCapabilities;
    private ValueAnimator settlementAnimator;
    private SettlementContext activeSettlementContext;
    private Settlement boundaryRestorationResult;
    private VelocityTracker velocityTracker;
    private ReadingDirection readingDirection = ReadingDirection.LEFT_TO_RIGHT;
    private ReadingDirection activeGestureReadingDirection = ReadingDirection.LEFT_TO_RIGHT;
    private PageDisplayRect activeGestureDisplayRect;
    private boolean settlementRunning;
    private boolean boundaryRestorationRunning;
    private boolean gestureAccepted;
    private boolean pageOverlayUpdatePending;
    private boolean gestureMoved;
    private boolean surfaceVisible = true;
    private boolean attached;
    private boolean disposed;
    private boolean windowAttached;
    private boolean holderSurfaceAvailable;
    private volatile boolean disposeStarted;
    private boolean resumedForDispose;
    private boolean mainTerminalExecutorRegistered;
    private volatile MainTerminalExecutor mainTerminalExecutor;
    private PageSurfaceDisposalResult disposedResult;
    private PageSurfaceOwnershipSnapshot disposedOwnershipSnapshot;
    private long disposingActiveGenerationId = NO_GENERATION_ID;
    private long disposingPendingGenerationId = NO_GENERATION_ID;
    private long activeGestureId = NO_GESTURE_ID;
    private long ownershipEpoch;
    private float gestureDownX;
    private float gestureDownY;
    private OnPageChangeListener onPageChangeListener;
    private final Runnable ownershipSnapshotCapacityEdge = () ->
            mainHandler.post(this::scheduleOwnershipRetryEdge);

    public PageSurfaceView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        renderer = new PageRenderer(new PageRenderer.Events() {
            @Override
            public void onCapabilitiesAvailable(RenderCapabilities capabilities) {
                post(() -> handleCapabilitiesAvailable(capabilities));
            }

            @Override
            public void onDeckPrepared(long generationId) {
                post(() -> handleDeckPrepared(generationId));
            }

            @Override
            public void onDeckReleased(
                    long generationId,
                    DeckReleaseReason reason) {
                mainHandler.post(() -> handleDeckReleased(generationId, reason));
            }

            @Override
            public void onPageOverlayUpdateCompleted(
                    long generationId,
                    boolean applied) {
                mainHandler.post(() -> handlePageOverlayUpdateCompleted(
                        generationId,
                        applied));
            }

            @Override
            public void onRenderFailure(RenderFailure failure) {
                post(() -> handleRenderFailure(failure));
            }
        });
        ownershipSnapshotCoordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(
                        new PageSurfaceOwnershipSnapshotCoordinator.Host() {
                            @Override
                            public void requireMainThread() {
                                PageSurfaceView.this.requireMainThread();
                            }

                            @Override
                            public PageSurfaceOwnershipSnapshotCoordinator.MainSample
                                    captureMainSample() {
                                return captureOwnershipLeaseSample();
                            }

                            @Override
                            public long currentOwnershipEpoch() {
                                return ownershipEpoch;
                            }

                            @Override
                            public int captureTextureCount() {
                                return renderer.textureCount();
                            }

                            @Override
                            public int captureTextureLimit() {
                                return renderer.textureLimit();
                            }

                            @Override
                            public boolean queueGl(Runnable action) {
                                try {
                                    queueEvent(action);
                                    return true;
                                } catch (Throwable queueFailure) {
                                    reportIsolatedCallbackFailure(
                                            IsolatedCallbackKind.OWNERSHIP,
                                            action,
                                            queueFailure);
                                    return false;
                                }
                            }

                            @Override
                            public PageSurfaceOwnershipSnapshotCoordinator.MainPostStatus
                                    postMain(Runnable action) {
                                if (disposeStarted) {
                                    return PageSurfaceOwnershipSnapshotCoordinator
                                            .MainPostStatus.TERMINALIZED;
                                }
                                return enqueueOrRetainMainTerminal(action, false)
                                        ? PageSurfaceOwnershipSnapshotCoordinator
                                                .MainPostStatus.ACCEPTED
                                        : PageSurfaceOwnershipSnapshotCoordinator
                                                .MainPostStatus.TERMINALIZED;
                            }

                            @Override
                            public void deliver(
                                    PageSurfaceOwnershipResult.Callback callback,
                                    PageSurfaceOwnershipResult result) {
                                notifyOwnershipCallback(callback, result);
                            }
                        },
                        OWNERSHIP_CALLBACK_LIMIT);
        ownershipRetryEdge = new PageSurfaceOwnershipRetryEdge(
                new PageSurfaceOwnershipRetryEdge.Host() {
                    @Override
                    public boolean isOwnershipAvailable() {
                        return !disposeStarted
                                && attached
                                && windowAttached
                                && holderSurfaceAvailable;
                    }

                    @Override
                    public Runnable ownershipRetryListener() {
                        return ownershipCallbackCapacityListener;
                    }

                    @Override
                    public boolean post(Runnable action) {
                        return mainHandler.post(action);
                    }
                });

        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        GLSurfaceView.Renderer renderer = new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                PageSurfaceView.this.renderer.onSurfaceCreated(gl, config);
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                PageSurfaceView.this.renderer.onSurfaceChanged(gl, width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                boolean frameRendered = PageSurfaceView.this.renderer.drawFrame(gl);
                if (!frameRendered) {
                    return;
                }
                long presentedRequestId = presentedFrameRequest.markRendered();
                if (presentedRequestId != NO_PRESENTED_FRAME_REQUEST_ID) {
                    boolean posted = post(() -> postOnAnimation(
                            () -> handlePresentedFrame(presentedRequestId)));
                    if (!posted) {
                        presentedFrameRequest.cancel(presentedRequestId);
                    }
                }
                long frameToken = boundaryRestorationProtocol.armedToken();
                if (frameToken != NO_BOUNDARY_FRAME_TOKEN) {
                    post(() -> postOnAnimation(() -> handleRenderedFrame(frameToken)));
                }
            }
        };
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);
        setClickable(true);
        setFocusable(true);
    }

    /** Marks the surface active for interaction and resumes its GL thread. */
    public void attach() {
        requireMainThread();
        drainRetainedMainTerminal();
        if (disposed || attached) {
            return;
        }
        attached = true;
        advanceOwnershipEpoch();
        onResume();
        requestRender();
        scheduleOwnershipRetryEdge();
    }

    /** Cancels interaction and pauses frame production without releasing the active deck. */
    public void detach() {
        requireMainThread();
        if (!attached) {
            return;
        }
        attached = false;
        advanceOwnershipEpoch();
        if (!disposeStarted) {
            failLiveOwnershipRequests();
        }
        cancelGesture();
        queueDeckRelease(deckCoordinator.releasePending(
                DeckReleaseReason.SESSION_DETACHED));
        requestRender();
        onPause();
    }

    /**
     * Submits one complete interaction window.
     *
     * <p>An idle submission becomes active after upload. During settlement, exactly one
     * replacement deck is retained and prepared. Acceptance starts a bitmap lease: every bitmap
     * in the deck must remain immutable and unrecycled until
     * {@link PageSurfaceListener#onDeckReleased(long, DeckReleaseReason)} reports the generation.
     * Rejected decks are never acquired.
     */
    public void submitDeck(PageDeck<Bitmap> deck) {
        submitDeckWithResult(deck);
    }

    public PageSurfaceDeckSubmissionResult submitDeckWithResult(
            PageDeck<Bitmap> deck) {
        return submitDeckWithResult(deck, () -> {});
    }

    /**
     * Submits a deck and synchronously reports the instant its bitmap lease transfers.
     *
     * <p>For a fresh accepted submission, {@code onOwnershipTransferred} runs after one renderer
     * command has accepted the deck transaction and before the render request. Queue rejection is
     * rolled back before transfer. Once the callback runs, the surface owns the deck even if this
     * method later throws; the caller must release that generation through {@link #releaseDeck(long)}.
     */
    public PageSurfaceDeckSubmissionResult submitDeckWithResult(
            PageDeck<Bitmap> deck,
            Runnable onOwnershipTransferred) {
        requireMainThread();
        if (deck == null) {
            throw new IllegalArgumentException("deck must not be null");
        }
        if (onOwnershipTransferred == null) {
            throw new IllegalArgumentException(
                    "onOwnershipTransferred must not be null");
        }
        if (disposed) {
            return rejectDeck(deck, DeckRejectionReason.DISPOSED);
        }
        if (!attached) {
            return rejectDeck(deck, DeckRejectionReason.SESSION_DETACHED);
        }
        if (renderCapabilities == null) {
            return rejectDeck(
                    deck,
                    DeckRejectionReason.CAPABILITIES_UNAVAILABLE);
        }
        if (!isSupportedDeckType(deck)) {
            return rejectDeck(deck, DeckRejectionReason.INVALID_CONTENT);
        }

        PageSurfaceDeckSubmissionGate.Result<Bitmap> gated =
                submissionGate.submit(deck, pageSurfaceListener);
        PageSurfaceDeckSubmissionResult result = gated.publicResult();
        if (result.getStatus()
                == PageSurfaceDeckSubmissionResult.Status.REJECTED) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    result.getRejectionReason());
            return result;
        }
        PageDeckCoordinator.Offer<Bitmap> offer = gated.offer();
        if (result.getStatus()
                == PageSurfaceDeckSubmissionResult.Status.UNCHANGED) {
            return result;
        }
        boolean activateWhenPrepared =
                offer.getPlacement() == PageDeckCoordinator.Placement.ACTIVE;
        try {
            queueEvent(() -> {
                for (PageDeckCoordinator.Release<Bitmap> release :
                        offer.getReleases()) {
                    renderer.releaseDeck(
                            release.getDeck().getGenerationId(),
                            release.getReason());
                }
                renderer.prepareDeck(deck, activateWhenPrepared);
            });
        } catch (RuntimeException | Error queueFailure) {
            try {
                submissionGate.rollbackAccepted(deck, gated);
            } catch (Throwable rollbackFailure) {
                if (rollbackFailure != queueFailure) {
                    queueFailure.addSuppressed(rollbackFailure);
                }
            }
            throw queueFailure;
        }
        for (PageDeckCoordinator.Release<Bitmap> release :
                offer.getReleases()) {
            long releasedGenerationId = release.getDeck().getGenerationId();
            if (preparedGenerations.remove(releasedGenerationId)) {
                advanceOwnershipEpoch();
            }
            leaseRegistry.markReleaseRequested(
                    releasedGenerationId,
                    release.getReason());
        }
        if (preparedGenerations.remove(deck.getGenerationId())) {
            advanceOwnershipEpoch();
        }
        onOwnershipTransferred.run();
        requestRender();
        return result;
    }

    private static boolean isSupportedDeckType(PageDeck<?> deck) {
        return deck instanceof PortraitPageDeck<?>
                || deck instanceof LandscapePageDeck<?>;
    }

    private PageSurfaceDeckSubmissionResult rejectDeck(
            PageDeck<Bitmap> deck,
            DeckRejectionReason reason) {
        pageSurfaceListener.onDeckRejected(deck.getGenerationId(), reason);
        return PageSurfaceDeckSubmissionResult.rejected(reason);
    }

    /** Returns whether a replacement submission would enter the pending settlement slot. */
    public boolean isSettlementRunning() {
        requireMainThread();
        return deckCoordinator.isSettling();
    }

    /**
     * Atomically replaces transparent overlays for the current page or spread.
     *
     * <p>An accepted update transfers bitmap ownership to the renderer. Rejected updates leave
     * ownership with the caller. Updates are suspended while a gesture, settlement, or prior
     * texture upload owns the presentation.
     */
    public PageOverlayUpdateResult replacePageOverlays(
            long generationId,
            List<PageOverlayImage<Bitmap>> overlays) {
        requireMainThread();
        if (overlays == null) {
            return PageOverlayUpdateResult.INVALID_CONTENT;
        }
        List<PageOverlayImage<Bitmap>> accepted = new ArrayList<>(overlays);
        List<Integer> ordinals = new ArrayList<>(accepted.size());
        for (PageOverlayImage<Bitmap> overlay : accepted) {
            if (overlay == null) {
                return PageOverlayUpdateResult.INVALID_CONTENT;
            }
            ordinals.add(overlay.getOrdinal());
        }
        PageDeck<Bitmap> activeDeck = deckCoordinator.getActiveDeck();
        PageOverlayUpdateResult admission = PageOverlayUpdateGate.evaluate(
                activeDeck,
                preparedGenerations,
                !disposed && attached && surfaceVisible,
                gestureAccepted || settlementRunning,
                pageOverlayUpdatePending,
                generationId,
                ordinals);
        if (admission != PageOverlayUpdateResult.ACCEPTED) {
            return admission;
        }
        for (PageOverlayImage<Bitmap> overlay : accepted) {
            PageImage<?> target = PageOverlayUpdateGate.currentPage(
                    activeDeck,
                    overlay.getOrdinal());
            if (!isValidOverlayBitmap(target, overlay.getContent())) {
                return PageOverlayUpdateResult.INVALID_CONTENT;
            }
        }
        PageOverlayPendingLease<List<PageOverlayImage<Bitmap>>> lease =
                new PageOverlayPendingLease<>(
                        accepted,
                        PageSurfaceView::recycleOverlayInputs);
        if (!pendingPageOverlayLease.compareAndSet(null, lease)) {
            throw new IllegalStateException(
                    "A page overlay input lease is already pending");
        }
        pageOverlayUpdatePending = true;
        try {
            queueEvent(() -> {
                List<PageOverlayImage<Bitmap>> claimed = lease.claim();
                pendingPageOverlayLease.compareAndSet(lease, null);
                if (claimed != null) {
                    renderer.replacePageOverlays(generationId, claimed);
                }
            });
        } catch (RuntimeException | Error queueFailure) {
            pendingPageOverlayLease.compareAndSet(lease, null);
            lease.withdraw();
            pageOverlayUpdatePending = false;
            throw queueFailure;
        }
        requestRender();
        return PageOverlayUpdateResult.ACCEPTED;
    }

    private static boolean isValidOverlayBitmap(PageImage<?> page, Bitmap bitmap) {
        return page != null
                && !bitmap.isRecycled()
                && bitmap.getWidth() == page.getWidthPx()
                && bitmap.getHeight() == page.getHeightPx()
                && bitmap.getConfig() == Bitmap.Config.ARGB_8888
                && bitmap.isPremultiplied()
                && bitmap.hasAlpha();
    }

    private static void recycleOverlayInputs(
            List<PageOverlayImage<Bitmap>> overlays) {
        for (PageOverlayImage<Bitmap> overlay : overlays) {
            Bitmap bitmap = overlay.getContent();
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    /** Supplies the current pixel viewport without changing page layout. */
    public void setViewport(int widthPx, int heightPx) {
        requireMainThread();
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        queueEvent(() -> renderer.setViewport(widthPx, heightPx));
        requestRender();
    }

    /**
     * Changes the active-plus-pending GPU budget before any deck is acquired.
     */
    public void setGpuBudgetBytes(long gpuBudgetBytes) {
        requireMainThread();
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        if (deckCoordinator.getActiveDeck() != null
                || deckCoordinator.getPendingDeck() != null) {
            throw new IllegalStateException(
                    "GPU budget cannot change while a deck is retained");
        }
        queueEvent(() -> renderer.setGpuBudgetBytes(gpuBudgetBytes));
        requestRender();
    }

    /** Controls rendering and gesture eligibility without discarding the prepared deck. */
    public void setVisible(boolean visible) {
        requireMainThread();
        surfaceVisible = visible;
        if (!visible) {
            presentedFrameRequest.cancelAll();
            cancelGesture();
        } else {
            requestRender();
        }
    }

    /**
     * Arms a one-shot callback for the next complete frame rendered after this request.
     *
     * <p>The callback runs on the Android main thread after the following animation pulse, so a
     * hidden surface can update its buffer before the client reveals it. At most one request may
     * be pending. The returned identifier can be cancelled when the associated gesture ends.
     */
    public long requestNextPresentedFrame(Runnable callback) {
        requireMainThread();
        Objects.requireNonNull(callback, "callback");
        if (disposed || !attached || !surfaceVisible) {
            return NO_PRESENTED_FRAME_REQUEST_ID;
        }
        long requestId = presentedFrameRequest.request(callback);
        try {
            queueEvent(() -> {
                if (presentedFrameRequest.arm(requestId)) {
                    requestRender();
                }
            });
        } catch (RuntimeException | Error queueFailure) {
            presentedFrameRequest.cancel(requestId);
            throw queueFailure;
        }
        return requestId;
    }

    /** Cancels an outstanding frame-presentation callback without invoking it. */
    public boolean cancelPresentedFrameRequest(long requestId) {
        requireMainThread();
        return presentedFrameRequest.cancel(requestId);
    }

    /** Sets the logical reading direction used by future gestures and turns. */
    public void setReadingDirection(ReadingDirection readingDirection) {
        requireMainThread();
        this.readingDirection = Objects.requireNonNull(readingDirection, "readingDirection");
        if (!gestureAccepted && !settlementRunning) {
            activeGestureReadingDirection = readingDirection;
            renderer.setReadingDirection(readingDirection);
            requestRender();
        }
    }

    /** Cancels any partial gesture or settlement without navigating. */
    public void cancelGesture() {
        cancelGesture(activeGestureId);
    }

    /** Cancels the identified gesture or settlement without navigating. */
    public void cancelGesture(long gestureId) {
        requireMainThread();
        presentedFrameRequest.cancelAll();
        SettlementContext cancelledSettlement = cancelSettlementAnimator();
        long cancelledGestureId = activeGestureId != NO_GESTURE_ID
                ? activeGestureId
                : gestureId;
        long generationId = activeGenerationId();
        boolean cancelledGesture = gestureAccepted;
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction != null) {
            interaction.cancelGesture();
        }
        deckCoordinator.cancelSettlement();
        gestureAccepted = false;
        gestureMoved = false;
        activeGestureDisplayRect = null;
        recycleVelocityTracker();
        requestRender();
        notifySettlementCancelled(cancelledSettlement);
        if (cancelledSettlement == null && cancelledGesture) {
            notifyGestureCancelled(cancelledGestureId, generationId);
        }
        activeGestureId = NO_GESTURE_ID;
    }

    /**
     * Starts the same reference settlement used by a completed edge drag.
     *
     * @return {@code false} only after exactly one synchronous gesture-rejection callback
     * @throws IllegalArgumentException when {@code pageChange} is not PREVIOUS or NEXT
     */
    public boolean turn(PageChange pageChange) {
        return turn(pageChange, NO_GESTURE_ID);
    }

    public boolean turn(PageChange pageChange, long gestureId) {
        requireMainThread();
        if (pageChange != PageChange.PREVIOUS && pageChange != PageChange.NEXT) {
            throw new IllegalArgumentException("pageChange must be PREVIOUS or NEXT");
        }
        activeGestureId = gestureId;
        if (!gestureReady()) {
            rejectGesture(gestureId);
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            rejectGesture(gestureId, GestureRejectionReason.DECK_NOT_PREPARED);
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        if (!canSettle(pageChange)) {
            rejectGesture(gestureId, GestureRejectionReason.BOUNDARY, pageChange);
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        activeGestureReadingDirection = readingDirection;
        renderer.setReadingDirection(activeGestureReadingDirection);
        Settlement settlement = interaction.turn(pageChange);
        if (settlement.getPageChange() == PageChange.NONE) {
            interaction.cancelGesture();
            requestRender();
            rejectGesture(gestureId, GestureRejectionReason.MODEL_REJECTED);
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        settle(settlement);
        return true;
    }

    /** Explicitly activates a prepared replacement after an idle generation change. */
    public void activatePendingDeck() {
        requireMainThread();
        PageDeckCoordinator.Promotion<Bitmap> promotion =
                deckCoordinator.activatePending();
        PageDeck<Bitmap> activated = promotion.getActivatedDeck();
        if (activated == null) {
            return;
        }
        markPromotionRelease(promotion);
        queueEvent(() -> renderer.activateDeck(activated.getGenerationId()));
        requestRender();
    }

    /** Releases a retained active or replacement deck and its GL textures. */
    public void releaseDeck(long generationId) {
        requireMainThread();
        PageDeck<Bitmap> activeDeck = deckCoordinator.getActiveDeck();
        if (activeDeck != null
                && activeDeck.getGenerationId() == generationId) {
            cancelGesture();
        }
        if (preparedGenerations.remove(generationId)) {
            advanceOwnershipEpoch();
        }
        PageDeckCoordinator.Release<Bitmap> release =
                deckCoordinator.release(generationId);
        queueDeckRelease(release);
        requestRender();
    }

    /** Idempotently releases renderer, gesture, and deck state. */
    public void dispose() {
        requireMainThread();
        startDisposeIfNeeded();
    }

    public void disposeForLifecycleOwner(
            PageSurfaceDisposalResult.Callback callback) {
        requireMainThread();
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        requiredDisposeCallback.register(callback::onComplete);
        startDisposeIfNeeded();
    }

    public DisposalCallbackRegistration dispose(
            PageSurfaceDisposalResult.Callback callback) {
        requireMainThread();
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        PageSurfaceTerminalCallbacks.AddResult registration =
                disposeCallbacks.add(callback::onComplete);
        startDisposeIfNeeded();
        switch (registration) {
            case ACCEPTED:
                return DisposalCallbackRegistration.ACCEPTED;
            case DELIVERED_TERMINAL:
                return DisposalCallbackRegistration.DELIVERED_TERMINAL;
            case CALLBACK_CAPACITY:
                return DisposalCallbackRegistration.CALLBACK_CAPACITY;
            default:
                throw new AssertionError("Unhandled callback registration");
        }
    }

    public void requestOwnershipSnapshot(
            PageSurfaceOwnershipResult.Callback callback) {
        requireMainThread();
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (disposedOwnershipSnapshot != null) {
            notifyOwnershipCallback(
                    callback,
                    PageSurfaceOwnershipResult.available(
                            disposedOwnershipSnapshot));
            return;
        }
        if (disposeStarted) {
            PageSurfaceTerminalCallbacks.AddResult registration =
                    terminalOwnershipCallbacks.add(callback::onResult);
            if (registration
                    == PageSurfaceTerminalCallbacks.AddResult.CALLBACK_CAPACITY) {
                notifyOwnershipCallback(
                        callback,
                        PageSurfaceOwnershipResult.unavailable(
                                PageSurfaceOwnershipResult.Status
                                        .CALLBACK_CAPACITY));
            }
            return;
        }
        if (!attached || !windowAttached || !holderSurfaceAvailable) {
            notifyOwnershipCallback(
                    callback,
                    PageSurfaceOwnershipResult.unavailable(
                            PageSurfaceOwnershipResult.Status
                                    .SURFACE_UNAVAILABLE));
            return;
        }
        PageSurfaceOwnershipRequestRegistry.Registration registration =
                ownershipSnapshotCoordinator.request(callback);
        if (registration.status()
                == PageSurfaceOwnershipRequestRegistry.Registration.Status
                        .CALLBACK_CAPACITY) {
            notifyOwnershipCallback(
                    callback,
                    PageSurfaceOwnershipResult.unavailable(
                            PageSurfaceOwnershipResult.Status
                                    .CALLBACK_CAPACITY));
        }
    }

    private void scheduleOwnershipRetryEdge() {
        requireMainThread();
        ownershipRetryEdge.schedule();
    }

    public void setOwnershipCallbackCapacityListener(Runnable listener) {
        requireMainThread();
        Runnable exact = Objects.requireNonNull(listener, "listener");
        if (ownershipCallbackCapacityListener == exact) {
            return;
        }
        ownershipCallbackCapacityListener = exact;
        ownershipSnapshotCoordinator.setCapacityAvailableListener(
                ownershipSnapshotCapacityEdge);
        scheduleOwnershipRetryEdge();
    }

    public void clearOwnershipCallbackCapacityListener(Runnable listener) {
        requireMainThread();
        if (ownershipCallbackCapacityListener != listener) {
            return;
        }
        ownershipCallbackCapacityListener = null;
        ownershipSnapshotCoordinator.clearCapacityAvailableListener(
                ownershipSnapshotCapacityEdge);
    }

    public int getPendingCallbackCount() {
        requireMainThread();
        return requiredDisposeCallback.pendingCount()
                + disposeCallbacks.pendingCount()
                + ownershipSnapshotCoordinator.size()
                + terminalOwnershipCallbacks.pendingCount()
                + presentedFrameRequest.pendingCount()
                + pendingMainTerminalActions.get()
                + (pageOverlayUpdatePending ? 1 : 0);
    }

    public int getPendingCallbackLimit() {
        return REQUIRED_DISPOSAL_CALLBACK_LIMIT
                + AUXILIARY_DISPOSAL_CALLBACK_LIMIT
                + OWNERSHIP_CALLBACK_LIMIT
                + PRESENTED_FRAME_CALLBACK_LIMIT
                + MAIN_TERMINAL_ACTION_LIMIT
                + PAGE_OVERLAY_UPDATE_LIMIT;
    }

    public int getDeckLeaseLimit() {
        return leaseRegistry.capacity();
    }

    public void registerMainTerminalExecutor(MainTerminalExecutor executor) {
        requireMainThread();
        Objects.requireNonNull(executor, "executor");
        if (mainTerminalExecutorRegistered) {
            throw new IllegalStateException(
                    "Main terminal executor is already registered");
        }
        mainTerminalExecutorRegistered = true;
        mainTerminalExecutor = executor;
        drainRetainedMainTerminal();
    }

    public int getMainTerminalActionLimit() {
        return MAIN_TERMINAL_ACTION_LIMIT;
    }

    public int getPendingMainTerminalActionCount() {
        return pendingMainTerminalActions.get();
    }

    private void abandonPendingPageOverlayUpdate() {
        PageOverlayPendingLease<List<PageOverlayImage<Bitmap>>> lease =
                pendingPageOverlayLease.getAndSet(null);
        if (lease != null) {
            lease.abandon();
        }
        pageOverlayUpdatePending = false;
    }

    private void startDisposeIfNeeded() {
        requireMainThread();
        if (disposedResult != null || disposeStarted) {
            return;
        }
        presentedFrameRequest.cancelAll();
        ownershipCallbackCapacityListener = null;
        ownershipSnapshotCoordinator.clearCapacityAvailableListener(
                ownershipSnapshotCapacityEdge);
        List<PageSurfaceOwnershipResult.Callback> ownershipCallbacks =
                ownershipSnapshotCoordinator.drain();
        disposeStarted = true;
        disposed = true;
        abandonPendingPageOverlayUpdate();
        try {
            for (PageSurfaceOwnershipResult.Callback callback :
                    ownershipCallbacks) {
                PageSurfaceTerminalCallbacks.AddResult registration =
                        terminalOwnershipCallbacks.add(callback::onResult);
                if (registration
                        != PageSurfaceTerminalCallbacks.AddResult.ACCEPTED) {
                    throw new IllegalStateException(
                            "Terminal ownership callback transfer exceeded capacity");
                }
            }
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        try {
            submissionGate.close();
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        boolean terminalExecutorUnavailable = mainTerminalExecutor == null;
        if (terminalExecutorUnavailable) {
            recordSetupFailure(
                    new IllegalStateException(
                            "Required main terminal executor is not registered"));
        }

        SettlementContext cancelledSettlement = null;
        try {
            cancelledSettlement = cancelSettlementAnimator();
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        try {
            recycleVelocityTracker();
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        try {
            preparedGenerations.clear();
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        try {
            disposingActiveGenerationId = generationIdOf(
                    deckCoordinator.getActiveDeck());
            disposingPendingGenerationId = generationIdOf(
                    deckCoordinator.getPendingDeck());
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        try {
            for (PageDeckCoordinator.Release<Bitmap> release :
                    deckCoordinator.dispose()) {
                try {
                    leaseRegistry.markReleaseRequested(
                            release.getDeck().getGenerationId(),
                            DeckReleaseReason.DISPOSED);
                } catch (Throwable setupFailure) {
                    recordSetupFailure(setupFailure);
                }
            }
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        notifySettlementCancelledForDispose(cancelledSettlement);
        try {
            setPreserveEGLContextOnPause(false);
        } catch (Throwable setupFailure) {
            recordSetupFailure(setupFailure);
        }
        if (terminalExecutorUnavailable) {
            finishDetachedDisposal();
            return;
        }

        terminalDisposalGate.start(
                new PageSurfaceTerminalDisposalGate.Host() {
                    @Override
                    public boolean isWindowAttached() {
                        return windowAttached;
                    }

                    @Override
                    public boolean isHolderSurfaceAvailable() {
                        return holderSurfaceAvailable;
                    }

                    @Override
                    public boolean isLogicallyAttached() {
                        return attached;
                    }

                    @Override
                    public void resumeForTerminalWork() {
                        resumedForDispose = true;
                        onResume();
                    }

                    @Override
                    public void queueTerminalWork(Runnable action) {
                        queueEvent(action);
                    }
                },
                timeoutAction -> {
                    if (!mainHandler.postDelayed(
                            timeoutAction,
                            GL_QUEUE_ENTRY_WATCHDOG_MILLIS)) {
                        throw new IllegalStateException(
                                "Main thread rejected the GL queue-entry watchdog");
                    }
                    return () -> mainHandler.removeCallbacks(timeoutAction);
                },
                this::disposeOnGlThread,
                this::finishTerminalFallback);
    }

    private void recordSetupFailure(Throwable failure) {
        disposalFailure.record(
                PageSurfaceDisposalStage.PRE_GL_SETUP,
                failure);
    }

    private void finishTerminalFallback(
            PageSurfaceTerminalDisposalGate.FailureKind kind,
            Throwable failure) {
        requireMainThread();
        boolean expectedDetachedFallback =
                PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                        kind,
                        holderSurfaceAvailable,
                        attached);
        if (!expectedDetachedFallback) {
            PageSurfaceDisposalStage stage =
                    kind == PageSurfaceTerminalDisposalGate.FailureKind.RESUME
                            ? PageSurfaceDisposalStage.SURFACE_RESUME
                            : PageSurfaceDisposalStage.GL_QUEUE_UNAVAILABLE;
            disposalFailure.record(stage, failure);
        }
        finishDetachedDisposal();
    }

    private void disposeOnGlThread() {
        try {
            renderer.dispose();
        } catch (Throwable rendererFailure) {
            disposalFailure.record(
                    PageSurfaceDisposalStage.GL_RENDERER_DISPOSE,
                    rendererFailure);
        } finally {
            int textures = renderer.textureCount();
            int textureLimit = renderer.textureLimit();
            if (!terminalDisposalGate.completeGlExecution()) {
                disposalFailure.record(
                        PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                        new IllegalStateException(
                                "GL disposal ownership was not active at completion"));
            }
            enqueueOrRetainMainTerminal(
                    () -> finishGlDisposalOnMain(
                            textures,
                            textureLimit),
                    true);
        }
    }

    private void finishGlDisposalOnMain(
            int textures,
            int textureLimit) {
        try {
            finishDisposeAfterGl(
                    textures,
                    textureLimit,
                    disposalFailure,
                    false,
                    true);
        } catch (Throwable publicationFailure) {
            if (!terminalDisposalGate.abandonGlPublication()) {
                disposalFailure.record(
                        PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                        new IllegalStateException(
                                "GL disposal publication ownership was not active",
                                publicationFailure));
            }
            if (publicationFailure instanceof RuntimeException) {
                throw (RuntimeException) publicationFailure;
            }
            if (publicationFailure instanceof Error) {
                throw (Error) publicationFailure;
            }
            throw new IllegalStateException(
                    "GL disposal publication failed",
                    publicationFailure);
        }
        if (!terminalDisposalGate.completeGlPublication()) {
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    new IllegalStateException(
                            "GL disposal publication ownership was not active at completion"));
        }
    }

    private boolean enqueueMainTerminal(Runnable action) {
        MainTerminalExecutor executor = mainTerminalExecutor;
        if (executor == null) {
            return false;
        }
        int pending = pendingMainTerminalActions.incrementAndGet();
        if (pending > MAIN_TERMINAL_ACTION_LIMIT) {
            pendingMainTerminalActions.decrementAndGet();
            return false;
        }
        AtomicBoolean ownershipClaimed = new AtomicBoolean();
        Runnable counted = countedMainTerminalAction(action, ownershipClaimed);
        final boolean accepted;
        try {
            accepted = executor.execute(counted);
        } catch (Throwable executionFailure) {
            if (ownershipClaimed.compareAndSet(false, true)) {
                releaseMainTerminalActionCount();
                disposalFailure.record(
                        PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                        executionFailure);
                return false;
            }
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    executionFailure);
            return true;
        }
        if (!accepted && ownershipClaimed.compareAndSet(false, true)) {
            releaseMainTerminalActionCount();
            return false;
        }
        if (!accepted) {
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    new IllegalStateException(
                            "Main terminal executor ran an action before rejecting it"));
        }
        return true;
    }

    private boolean enqueueMainHandlerTerminal(Runnable action) {
        int pending = pendingMainTerminalActions.incrementAndGet();
        if (pending > MAIN_TERMINAL_ACTION_LIMIT) {
            pendingMainTerminalActions.decrementAndGet();
            return false;
        }
        AtomicBoolean ownershipClaimed = new AtomicBoolean();
        Runnable counted = countedMainTerminalAction(action, ownershipClaimed);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            counted.run();
            return true;
        }
        final boolean accepted;
        try {
            accepted = mainHandler.post(counted);
        } catch (Throwable handlerFailure) {
            if (ownershipClaimed.compareAndSet(false, true)) {
                releaseMainTerminalActionCount();
            }
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    handlerFailure);
            return false;
        }
        if (!accepted && ownershipClaimed.compareAndSet(false, true)) {
            releaseMainTerminalActionCount();
            return false;
        }
        return true;
    }

    private Runnable countedMainTerminalAction(
            Runnable action,
            AtomicBoolean ownershipClaimed) {
        return () -> {
            if (!ownershipClaimed.compareAndSet(false, true)) {
                return;
            }
            releaseMainTerminalActionCount();
            try {
                action.run();
            } catch (Throwable actionFailure) {
                disposalFailure.record(
                        PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                        actionFailure);
                if (disposeStarted && disposedResult == null) {
                    finishDetachedDisposal();
                } else if (!disposeStarted) {
                    failLiveOwnershipRequests();
                }
            }
        };
    }

    private void releaseMainTerminalActionCount() {
        int remaining = pendingMainTerminalActions.decrementAndGet();
        if (remaining < 0) {
            pendingMainTerminalActions.set(0);
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    new IllegalStateException(
                            "Main terminal action ownership underflow"));
        }
    }

    private boolean enqueueOrRetainMainTerminal(
            Runnable action,
            boolean requiredDisposal) {
        if (enqueueMainTerminal(action)) {
            return true;
        }
        if (enqueueMainHandlerTerminal(action)) {
            return true;
        }
        AtomicReference<Runnable> retained = requiredDisposal
                ? retainedDisposalMainTerminalAction
                : retainedOwnershipMainTerminalAction;
        int pending = pendingMainTerminalActions.incrementAndGet();
        if (pending > MAIN_TERMINAL_ACTION_LIMIT) {
            pendingMainTerminalActions.decrementAndGet();
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    new IllegalStateException(
                            "Main terminal action capacity is exhausted"));
            return false;
        }
        Runnable counted = () -> {
            releaseMainTerminalActionCount();
            action.run();
        };
        if (!retained.compareAndSet(null, counted)) {
            releaseMainTerminalActionCount();
            disposalFailure.record(
                    PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                    new IllegalStateException(
                            "A main-thread terminal action is already retained"));
            return false;
        }
        disposalFailure.record(
                PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR,
                new IllegalStateException(
                        "Required main terminal executor rejected completion"));
        return true;
    }

    private void drainRetainedMainTerminal() {
        requireMainThread();
        runRetainedMainTerminal(retainedDisposalMainTerminalAction);
        runRetainedMainTerminal(retainedOwnershipMainTerminalAction);
    }

    private void runRetainedMainTerminal(
            AtomicReference<Runnable> retained) {
        Runnable action = retained.getAndSet(null);
        if (action != null) {
            action.run();
        }
    }

    private void discardRetainedMainTerminal(
            AtomicReference<Runnable> retained) {
        if (retained.getAndSet(null) != null) {
            releaseMainTerminalActionCount();
        }
    }

    private void finishDetachedDisposal() {
        requireMainThread();
        if (disposedResult != null || terminalDisposalGate.glOwnsExecution()) {
            return;
        }
        boolean glPaused = pauseForDetachedDisposal();
        int textureLimit = renderer.textureLimit();
        int textures = textureLimit;
        boolean clientOwnershipReleased = false;
        if (glPaused) {
            try {
                renderer.abandonClientState();
                textures = renderer.textureCount();
                clientOwnershipReleased = true;
            } catch (Throwable abandonFailure) {
                disposalFailure.record(
                        PageSurfaceDisposalStage.GL_RENDERER_DISPOSE,
                        abandonFailure);
            }
        }
        finishDisposeAfterGl(
                textures,
                textureLimit,
                disposalFailure,
                true,
                clientOwnershipReleased);
    }

    private boolean pauseForDetachedDisposal() {
        boolean alreadyPaused = !attached && !resumedForDispose;
        attached = false;
        resumedForDispose = false;
        if (!windowAttached) {
            return true;
        }
        if (alreadyPaused) {
            try {
                onResume();
            } catch (Throwable resumeFailure) {
                disposalFailure.record(
                        PageSurfaceDisposalStage.SURFACE_RESUME,
                        resumeFailure);
                return false;
            }
        }
        try {
            onPause();
            return true;
        } catch (Throwable pauseFailure) {
            disposalFailure.record(
                    PageSurfaceDisposalStage.SURFACE_PAUSE,
                    pauseFailure);
            return false;
        }
    }

    private void finishDisposeAfterGl(
            int textures,
            int textureLimit,
            FailureAccumulator failure,
            boolean detachedFallback,
            boolean releaseDeckLeases) {
        requireMainThread();
        if (disposedResult != null) {
            return;
        }
        if (releaseDeckLeases) {
            for (DeckLeaseRegistry.Lease lease :
                    leaseRegistry.releaseAll(DeckReleaseReason.DISPOSED)) {
                notifyDeckReleased(
                        lease.getListener(),
                        lease.getGenerationId(),
                        lease.getReleaseReason());
            }
        }
        int activeDeckLeases = leaseCount(disposingActiveGenerationId);
        int pendingDeckLeases =
                disposingPendingGenerationId == disposingActiveGenerationId
                        ? 0
                        : leaseCount(disposingPendingGenerationId);
        int releaseInFlightDeckLeases =
                leaseRegistry.releaseInFlightCount(
                        disposingActiveGenerationId,
                        disposingPendingGenerationId);
        int orphanDeckLeases = leaseRegistry.size()
                - activeDeckLeases
                - pendingDeckLeases
                - releaseInFlightDeckLeases;
        PageSurfaceOwnershipSnapshot snapshot =
                new PageSurfaceOwnershipSnapshot(
                        activeDeckLeases,
                        1,
                        pendingDeckLeases,
                        1,
                        releaseInFlightDeckLeases,
                        leaseRegistry.capacity(),
                        orphanDeckLeases,
                        0,
                        textures,
                        textureLimit);
        if (activeDeckLeases != 0
                || pendingDeckLeases != 0
                || releaseInFlightDeckLeases != 0
                || orphanDeckLeases != 0
                || textures != 0) {
            failure.record(
                    PageSurfaceDisposalStage.OWNERSHIP_RETAINED,
                    new IllegalStateException(
                            "Renderer disposal retained ownership"));
        }

        if (attached || resumedForDispose) {
            attached = false;
            resumedForDispose = false;
            try {
                onPause();
            } catch (Throwable pauseFailure) {
                failure.record(
                        PageSurfaceDisposalStage.SURFACE_PAUSE,
                        pauseFailure);
            }
        }

        disposedOwnershipSnapshot = snapshot;
        disposingActiveGenerationId = NO_GENERATION_ID;
        disposingPendingGenerationId = NO_GENERATION_ID;
        PageSurfaceDisposalResult result = new PageSurfaceDisposalResult(
                snapshot,
                failure.stage(),
                failure.failure(),
                failure.suppressedFailureCount(),
                detachedFallback);
        disposedResult = result;

        discardRetainedMainTerminal(retainedOwnershipMainTerminalAction);
        terminalOwnershipCallbacks.complete(
                PageSurfaceOwnershipResult.available(snapshot));
        requiredDisposeCallback.complete(result);
        disposeCallbacks.complete(result);
        mainTerminalExecutor = null;
    }

    private OwnershipLeaseSample captureOwnershipLeaseSample() {
        long activeGenerationId = generationIdOf(deckCoordinator.getActiveDeck());
        long pendingGenerationId = generationIdOf(deckCoordinator.getPendingDeck());
        int activeDeckLeases = leaseCount(activeGenerationId);
        int pendingDeckLeases = activeGenerationId == pendingGenerationId
                ? 0
                : leaseCount(pendingGenerationId);
        int releaseInFlightDeckLeases = leaseRegistry.releaseInFlightCount(
                activeGenerationId,
                pendingGenerationId);
        int orphanDeckLeases = leaseRegistry.size()
                - activeDeckLeases
                - pendingDeckLeases
                - releaseInFlightDeckLeases;
        return new OwnershipLeaseSample(
                ownershipEpoch,
                activeDeckLeases,
                pendingDeckLeases,
                releaseInFlightDeckLeases,
                orphanDeckLeases,
                leaseRegistry.capacity());
    }

    private void failLiveOwnershipRequests() {
        discardRetainedMainTerminal(retainedOwnershipMainTerminalAction);
        PageSurfaceOwnershipResult unavailable =
                PageSurfaceOwnershipResult.unavailable(
                        PageSurfaceOwnershipResult.Status.SURFACE_UNAVAILABLE);
        for (PageSurfaceOwnershipResult.Callback callback :
                ownershipSnapshotCoordinator.drain()) {
            notifyOwnershipCallback(callback, unavailable);
        }
    }

    private void advanceOwnershipEpoch() {
        ownershipEpoch += 1L;
    }

    private int leaseCount(long generationId) {
        return generationId == NO_GENERATION_ID
                || !leaseRegistry.contains(generationId) ? 0 : 1;
    }

    private static long generationIdOf(PageDeck<?> deck) {
        return deck == null ? NO_GENERATION_ID : deck.getGenerationId();
    }

    private void notifySettlementCancelledForDispose(
            SettlementContext context) {
        if (context == null) {
            return;
        }
        PageSurfaceListener listener = null;
        try {
            listener = listenerFor(context.generationId);
            listener.onSettlementCancelled(
                    context.gestureId,
                    context.generationId,
                    context.sourceLogicalPageId);
        } catch (Throwable callbackFailure) {
            disposalFailure.record(
                    PageSurfaceDisposalStage.SETTLEMENT_CANCEL_CALLBACK,
                    callbackFailure);
            reportIsolatedCallbackFailure(
                    IsolatedCallbackKind.SETTLEMENT_CANCEL,
                    listener,
                    callbackFailure);
        }
    }

    private void notifyDeckReleased(
            PageSurfaceListener listener,
            long generationId,
            DeckReleaseReason reason) {
        try {
            listener.onDeckReleased(generationId, reason);
        } catch (Throwable callbackFailure) {
            if (disposeStarted && disposedResult == null) {
                disposalFailure.record(
                        PageSurfaceDisposalStage.DECK_RELEASE_CALLBACK,
                        callbackFailure);
            }
            reportIsolatedCallbackFailure(
                    IsolatedCallbackKind.DECK_RELEASE,
                    listener,
                    callbackFailure);
        }
    }

    private void notifyDeckSubmissionCapacityAvailable(
            PageSurfaceListener listener) {
        try {
            listener.onDeckSubmissionCapacityAvailable();
        } catch (Throwable callbackFailure) {
            reportIsolatedCallbackFailure(
                    IsolatedCallbackKind.DECK_SUBMISSION_CAPACITY,
                    listener,
                    callbackFailure);
        }
    }

    private static void notifyOwnershipCallback(
            PageSurfaceOwnershipResult.Callback callback,
            PageSurfaceOwnershipResult result) {
        try {
            callback.onResult(result);
        } catch (Throwable callbackFailure) {
            reportIsolatedCallbackFailure(
                    IsolatedCallbackKind.OWNERSHIP,
                    callback,
                    callbackFailure);
        }
    }

    private static void reportIsolatedCallbackFailure(
            IsolatedCallbackKind kind,
            Object callback,
            Throwable failure) {
        try {
            Log.e(
                    TAG,
                    "callback-failed kind=" + kind
                            + " callbackClass="
                            + (callback == null
                                    ? "unresolved"
                                    : callback.getClass().getName())
                            + " failureClass="
                            + failure.getClass().getName());
        } catch (Throwable ignored) {
            // Callback isolation must not depend on diagnostics.
        }
    }

    private enum IsolatedCallbackKind {
        SETTLEMENT_CANCEL,
        DECK_RELEASE,
        DECK_SUBMISSION_CAPACITY,
        OWNERSHIP,
        PRESENTED_FRAME,
        DISPOSAL
    }

    private static final class FailureAccumulator {
        private PageSurfaceDisposalStage stage =
                PageSurfaceDisposalStage.NONE;
        private Throwable failure;

        synchronized void record(
                PageSurfaceDisposalStage nextStage,
                Throwable nextFailure) {
            if (failure == null) {
                stage = nextStage;
                failure = nextFailure;
            } else if (nextFailure != failure) {
                failure.addSuppressed(nextFailure);
            }
        }

        synchronized PageSurfaceDisposalStage stage() {
            return stage;
        }

        synchronized Throwable failure() {
            return failure;
        }

        synchronized int suppressedFailureCount() {
            return failure == null ? 0 : failure.getSuppressed().length;
        }
    }

    /**
     * Installs the main-thread callback boundary for future decks and capability events.
     *
     * <p>An accepted generation remains bound to the listener that acquired its bitmap lease,
     * even if this listener is replaced before that generation is released.
     */
    public void setPageSurfaceListener(PageSurfaceListener listener) {
        requireMainThread();
        pageSurfaceListener = listener == null ? NO_OP_LISTENER : listener;
        if (renderCapabilities != null) {
            pageSurfaceListener.onCapabilitiesAvailable(renderCapabilities);
        }
    }

    public PageSurfaceListener getPageSurfaceListener() {
        return pageSurfaceListener;
    }

    public RenderCapabilities getRenderCapabilities() {
        return renderCapabilities;
    }

    public int getCurrentPosition() {
        return currentPosition(deckCoordinator.getActiveDeck());
    }

    static int currentPosition(PageDeck<?> deck) {
        return deck == null
                ? -1
                : deck.getSettlementPage(PageChange.NONE).getOrdinal();
    }

    static PageDisplayRect gestureDisplayRect(
            PageDeck<?> deck,
            float physicalX,
            int surfaceWidth,
            int surfaceHeight) {
        int width = Math.max(1, surfaceWidth);
        int height = Math.max(1, surfaceHeight);
        PageDisplayRect full = new PageDisplayRect(0, 0, width, height);
        if (deck instanceof PortraitPageDeck<?>) {
            PageImage<?> current = ((PortraitPageDeck<?>) deck).getCurrent();
            return current.hasExplicitDisplayRect() ? current.getDisplayRect() : full;
        }
        if (!(deck instanceof LandscapePageDeck<?>) || width < 2) {
            return full;
        }

        LandscapePageDeck<?> spread = (LandscapePageDeck<?>) deck;
        int split = width / 2;
        PageDisplayRect leftFallback = new PageDisplayRect(0, 0, split, height);
        PageDisplayRect rightFallback = new PageDisplayRect(split, 0, width, height);
        PageDisplayRect left = spread.getCurrentLeft().hasExplicitDisplayRect()
                ? spread.getCurrentLeft().getDisplayRect()
                : leftFallback;
        PageDisplayRect right = spread.getCurrentRight().hasExplicitDisplayRect()
                ? spread.getCurrentRight().getDisplayRect()
                : rightFallback;
        float divider = (left.getRightPx() + right.getLeftPx()) / 2f;
        return physicalX < divider ? left : right;
    }

    static float logicalGestureX(
            float physicalX,
            PageDisplayRect displayRect,
            ReadingDirection readingDirection) {
        Objects.requireNonNull(displayRect, "displayRect");
        Objects.requireNonNull(readingDirection, "readingDirection");
        float localX = physicalX - displayRect.getLeftPx();
        return readingDirection.toLogicalX(localX, displayRect.getWidthPx());
    }

    public OnPageChangeListener getOnPageChangeListener() {
        return onPageChangeListener;
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        requireMainThread();
        onPageChangeListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handlePageTouchEvent(event, NO_GESTURE_ID);
    }

    private boolean handlePageTouchEvent(MotionEvent event, long gestureId) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            activeGestureId = gestureId;
            activeGestureDisplayRect = null;
            gestureAccepted = gestureReady();
            gestureMoved = false;
            recycleVelocityTracker();
            if (!gestureAccepted) {
                rejectGesture(activeGestureId);
                activeGestureId = NO_GESTURE_ID;
                return true;
            }
        } else if (!gestureAccepted) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureAccepted = false;
                gestureMoved = false;
                recycleVelocityTracker();
                activeGestureId = NO_GESTURE_ID;
            }
            return true;
        }
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            gestureAccepted = false;
            gestureMoved = false;
            recycleVelocityTracker();
            notifyGestureCancelled(activeGestureId, activeGenerationId());
            activeGestureId = NO_GESTURE_ID;
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                gestureDownX = event.getX();
                gestureDownY = event.getY();
                activeGestureReadingDirection = readingDirection;
                renderer.setReadingDirection(activeGestureReadingDirection);
                activeGestureDisplayRect = gestureDisplayRect(
                        deckCoordinator.getActiveDeck(),
                        gestureDownX,
                        getWidth(),
                        getHeight());
                obtainVelocityTracker().addMovement(event);
                float logicalDownX = logicalGestureX(
                        gestureDownX,
                        activeGestureDisplayRect,
                        activeGestureReadingDirection);
                interaction.beginGesture(logicalDownX);
                return true;
            case MotionEvent.ACTION_MOVE:
                obtainVelocityTracker().addMovement(event);
                float deltaX = event.getX() - gestureDownX;
                float deltaY = event.getY() - gestureDownY;
                if (!gestureMoved
                        && Math.hypot(deltaX, deltaY) >= touchSlop) {
                    gestureMoved = true;
                }
                if (gestureMoved) {
                    dragInteraction(event.getX());
                    requestRender();
                }
                return true;
            case MotionEvent.ACTION_UP:
                obtainVelocityTracker().addMovement(event);
                if (!gestureMoved) {
                    gestureAccepted = false;
                    interaction.cancelGesture();
                    recycleVelocityTracker();
                    requestRender();
                    performClick();
                    notifyGestureCancelled(activeGestureId, activeGenerationId());
                    activeGestureId = NO_GESTURE_ID;
                    return true;
                }
                dragInteraction(event.getX());
                VelocityTracker tracker = velocityTracker;
                tracker.computeCurrentVelocity(1000);
                float velocityX = tracker.getXVelocity();
                recycleVelocityTracker();
                Settlement settlement;
                if (Math.abs(velocityX) >= FLING_THRESHOLD_PX_PER_SECOND) {
                    PageChange flingChange = activeGestureReadingDirection
                            .pageChangeForVelocity(velocityX);
                    settlement = flingChange == PageChange.NEXT
                            ? interaction.flingTowardNext()
                            : interaction.flingTowardPrevious();
                } else {
                    settlement = interaction.release();
                }
                gestureAccepted = false;
                settle(settlement);
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestureAccepted = false;
                interaction.cancelGesture();
                gestureMoved = false;
                recycleVelocityTracker();
                requestRender();
                notifyGestureCancelled(activeGestureId, activeGenerationId());
                activeGestureId = NO_GESTURE_ID;
                return true;
            default:
                return true;
        }
    }

    public boolean onPageTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    public boolean onPageTouchEvent(MotionEvent event, long gestureId) {
        return handlePageTouchEvent(event, gestureId);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        windowAttached = true;
        advanceOwnershipEpoch();
        drainRetainedMainTerminal();
        scheduleOwnershipRetryEdge();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
        holderSurfaceAvailable = true;
        advanceOwnershipEpoch();
        drainRetainedMainTerminal();
        scheduleOwnershipRetryEdge();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        presentedFrameRequest.cancelAll();
        holderSurfaceAvailable = false;
        advanceOwnershipEpoch();
        drainRetainedMainTerminal();
        if (!disposeStarted) {
            failLiveOwnershipRequests();
        }
        if (disposeStarted && disposedResult == null) {
            terminalDisposalGate.onSurfaceUnavailable(
                    new IllegalStateException(
                            "GL holder surface was destroyed before terminal disposal"),
                    this::finishTerminalFallback);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
        windowAttached = false;
        advanceOwnershipEpoch();
        drainRetainedMainTerminal();
        if (!disposeStarted) {
            failLiveOwnershipRequests();
        }
        if (disposeStarted && disposedResult == null) {
            terminalDisposalGate.onSurfaceUnavailable(
                    new IllegalStateException(
                            "GL window detached before terminal disposal"),
                    this::finishTerminalFallback);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) {
            setViewport(width, height);
        }
    }

    private void handleDeckPrepared(long generationId) {
        if (disposed) {
            return;
        }
        PageSurfaceListener owner = leaseRegistry.ownerFor(generationId);
        if (owner == null) {
            return;
        }
        if (!preparedGenerations.add(generationId)) {
            return;
        }
        advanceOwnershipEpoch();
        trimPreparedGenerations();
        owner.onDeckPrepared(generationId);
        requestRender();
    }

    private void handlePageOverlayUpdateCompleted(
            long generationId,
            boolean applied) {
        if (!pageOverlayUpdatePending) {
            return;
        }
        pageOverlayUpdatePending = false;
        listenerFor(generationId).onPageOverlayUpdateCapacityAvailable(applied);
        requestRender();
    }

    private void handleRenderFailure(RenderFailure failure) {
        if (disposed) {
            return;
        }
        long generationId = failure.getGenerationId();
        PageSurfaceListener owner = generationId < 0
                ? pageSurfaceListener
                : leaseRegistry.ownerFor(generationId);
        if (owner != null) {
            owner.onRenderFailure(failure);
        }
    }

    private void handleCapabilitiesAvailable(RenderCapabilities capabilities) {
        if (disposed) {
            return;
        }
        renderCapabilities = capabilities;
        advanceOwnershipEpoch();
        pageSurfaceListener.onCapabilitiesAvailable(capabilities);
    }

    private void handleDeckReleased(
            long generationId,
            DeckReleaseReason reason) {
        if (preparedGenerations.remove(generationId)) {
            advanceOwnershipEpoch();
        }
        leaseRegistry.markReleaseRequested(generationId, reason);
        PageDeck<Bitmap> activeDeck = deckCoordinator.getActiveDeck();
        if (activeDeck != null
                && activeDeck.getGenerationId() == generationId) {
            cancelGesture();
        }
        deckCoordinator.release(generationId);
        DeckLeaseRegistry.Lease lease =
                leaseRegistry.release(generationId);
        if (lease == null) {
            return;
        }
        DeckReleaseReason effectiveReason =
                lease.getReleaseReason() == null
                        ? reason
                        : lease.getReleaseReason();
        notifyDeckReleased(
                lease.getListener(),
                generationId,
                effectiveReason);
        boolean capacityAvailable =
                submissionGate.takeCapacityAvailableSignal(generationId);
        if (capacityAvailable) {
            notifyDeckSubmissionCapacityAvailable(pageSurfaceListener);
        }
    }

    private void trimPreparedGenerations() {
        while (preparedGenerations.size() > 2) {
            Iterator<Long> iterator = preparedGenerations.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private boolean gestureReady() {
        if (disposed || !attached || !surfaceVisible || settlementRunning) {
            return false;
        }
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active != null && preparedGenerations.contains(active.getGenerationId());
    }

    private boolean canSettle(PageChange pageChange) {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active != null && active.canTurn(pageChange);
    }

    private void rejectGesture(long gestureId) {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        long generationId = active == null ? -1L : active.getGenerationId();
        GestureRejectionReason reason;
        if (disposed) {
            reason = GestureRejectionReason.DISPOSED;
        } else if (!attached) {
            reason = GestureRejectionReason.SESSION_DETACHED;
        } else if (!surfaceVisible) {
            reason = GestureRejectionReason.NOT_VISIBLE;
        } else if (settlementRunning) {
            reason = GestureRejectionReason.SETTLEMENT_RUNNING;
        } else {
            reason = GestureRejectionReason.DECK_NOT_PREPARED;
        }
        rejectGesture(gestureId, reason);
    }

    private void rejectGesture(long gestureId, GestureRejectionReason reason) {
        rejectGesture(gestureId, reason, PageChange.NONE);
    }

    private void rejectGesture(
            long gestureId,
            GestureRejectionReason reason,
            PageChange pageChange) {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        long generationId = active == null ? -1L : active.getGenerationId();
        listenerFor(generationId).onGestureRejected(
                gestureId,
                generationId,
                reason,
                pageChange);
    }

    private void dragInteraction(float physicalX) {
        PageDisplayRect displayRect = activeGestureDisplayRect;
        if (displayRect == null) {
            displayRect = gestureDisplayRect(
                    deckCoordinator.getActiveDeck(),
                    gestureDownX,
                    getWidth(),
                    getHeight());
            activeGestureDisplayRect = displayRect;
        }
        float logicalX = logicalGestureX(
                physicalX, displayRect, activeGestureReadingDirection);
        float gestureWidth = displayRect.getWidthPx();
        LandscapeSpreadModel landscape = landscapeModelOrNull();
        if (landscape != null) {
            landscape.dragTo(logicalX, gestureWidth);
        } else {
            PlayLikeCurlModel interaction = interactionModelOrNull();
            if (interaction != null) {
                interaction.dragTo(logicalX, gestureWidth);
            }
        }
    }

    private void settle(Settlement settlement) {
        if (settlementRunning) {
            return;
        }
        if (settlement.getPageChange() != PageChange.NONE
                && !canSettle(settlement.getPageChange())) {
            startBoundaryRestoration(settlement);
            return;
        }
        SettlementContext context = settlementContext(settlement.getPageChange());
        activeGestureId = NO_GESTURE_ID;
        activeSettlementContext = context;
        deckCoordinator.beginSettlement();
        settlementRunning = true;
        listenerFor(context.generationId).onSettlementStarted(
                context.gestureId,
                context.generationId,
                context.sourceLogicalPageId,
                context.targetLogicalPageId,
                settlement.getPageChange());
        startSettlementAnimation(
                settlement,
                () -> completeSettlement(settlement, context));
    }

    private void startBoundaryRestoration(Settlement rejectedSettlement) {
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            throw new IllegalStateException("Boundary restoration requires an interaction model");
        }
        if (settlementRunning) {
            throw new IllegalStateException("Boundary restoration cannot replace a settlement");
        }
        SettlementContext context = settlementContext(
                PageChange.NONE,
                rejectedSettlement.getPageChange());
        Settlement restoration = boundaryRestoreSettlement(interaction);
        activeGestureId = NO_GESTURE_ID;
        activeSettlementContext = context;
        boundaryRestorationResult = restoration;
        boundaryRestorationRunning = true;
        deckCoordinator.beginSettlement();
        settlementRunning = true;
        startSettlementAnimation(restoration, this::completeBoundaryRestorationAnimation);
    }

    static Settlement boundaryRestoreSettlement(PlayLikeCurlModel model) {
        Objects.requireNonNull(model, "model");
        int targetPercent = model.getActivePage() == ActivePage.LEFT
                ? PlayLikeCurlModel.RIGHT_ENDPOINT_PERCENT
                : PlayLikeCurlModel.LEFT_ENDPOINT_PERCENT;
        return new Settlement(
                targetPercent,
                PlayLikeCurlModel.SETTLEMENT_DURATION_MILLIS,
                SettlementInterpolator.ACCELERATE_DECELERATE,
                PageChange.NONE);
    }

    private void startSettlementAnimation(Settlement settlement, Runnable completion) {
        float startPercent = currentPagePercent();
        if (startPercent == settlement.getTargetPercent()) {
            completion.run();
            return;
        }
        settlementAnimator =
                ValueAnimator.ofFloat(startPercent, settlement.getTargetPercent());
        settlementAnimator.setDuration(settlement.getDurationMillis());
        settlementAnimator.setInterpolator(
                toAndroidInterpolator(settlement.getInterpolator()));
        settlementAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            LandscapeSpreadModel spread = landscapeModelOrNull();
            if (spread != null) {
                spread.updateSettlement(value);
            } else {
                interactionModelOrNull().updateSettlement(value);
            }
            requestRender();
        });
        settlementAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    completion.run();
                }
            }
        });
        settlementAnimator.start();
    }

    private void completeBoundaryRestorationAnimation() {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        if (spread != null) {
            spread.completeSettlement(boundaryRestorationResult);
        } else {
            interactionModelOrNull().completeSettlement(boundaryRestorationResult);
        }
        settlementAnimator = null;
        gestureMoved = false;
        long frameToken = boundaryRestorationProtocol.beginAwaitingFrame();
        queueEvent(() -> {
            if (boundaryRestorationProtocol.arm(frameToken)) {
                requestRender();
            }
        });
    }

    private void handlePresentedFrame(long requestId) {
        Runnable callback = presentedFrameRequest.complete(requestId);
        if (callback == null) {
            return;
        }
        try {
            callback.run();
        } catch (Throwable callbackFailure) {
            reportIsolatedCallbackFailure(
                    IsolatedCallbackKind.PRESENTED_FRAME,
                    callback,
                    callbackFailure);
        }
    }

    private void handleRenderedFrame(long frameToken) {
        boolean pendingDeckPresent = deckCoordinator.getPendingDeck() != null;
        BoundaryRestorationProtocol.Completion completion =
                boundaryRestorationProtocol.complete(
                        frameToken, true, true, pendingDeckPresent);
        if (completion == null || !boundaryRestorationRunning) {
            return;
        }
        SettlementContext context = activeSettlementContext;
        boundaryRestorationRunning = false;
        boundaryRestorationResult = null;
        settlementRunning = false;
        settlementAnimator = null;
        activeSettlementContext = null;
        gestureMoved = false;
        deckCoordinator.cancelSettlement();
        if (completion.shouldReleasePending()) {
            queueDeckRelease(deckCoordinator.releasePending(DeckReleaseReason.REPLACED));
        }
        if (completion.shouldPublishBoundary()) {
            listenerFor(context.generationId).onGestureRejected(
                    context.gestureId,
                    context.generationId,
                    GestureRejectionReason.BOUNDARY,
                    context.pageChange);
        }
    }

    private void completeSettlement(Settlement settlement, SettlementContext context) {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        if (spread != null) {
            spread.completeSettlement(settlement);
        } else {
            interactionModelOrNull().completeSettlement(settlement);
        }
        settlementRunning = false;
        settlementAnimator = null;
        activeSettlementContext = null;
        gestureMoved = false;

        PageDeckCoordinator.Promotion<Bitmap> promotion =
                deckCoordinator.completeSettlement();
        PageDeck<Bitmap> promoted = promotion.getActivatedDeck();
        if (promoted != null) {
            markPromotionRelease(promotion);
            queueEvent(() -> renderer.activateDeck(promoted.getGenerationId()));
        } else if (settlement.getPageChange() != PageChange.NONE) {
            preparedGenerations.remove(context.generationId);
        }
        requestRender();

        listenerFor(context.generationId).onSettlementCompleted(
                context.gestureId,
                context.generationId,
                context.targetLogicalPageId,
                context.targetOrdinal,
                settlement.getPageChange());
        if (settlement.getPageChange() != PageChange.NONE
                && onPageChangeListener != null) {
            onPageChangeListener.onPageChanged(context.targetOrdinal);
        }
    }

    private SettlementContext cancelSettlementAnimator() {
        SettlementContext cancelledContext =
                settlementRunning ? activeSettlementContext : null;
        if (settlementAnimator != null) {
            settlementAnimator.removeAllListeners();
            settlementAnimator.cancel();
            settlementAnimator = null;
        }
        long boundaryToken = boundaryRestorationProtocol.pendingToken();
        boundaryRestorationProtocol.cancel(boundaryToken);
        boundaryRestorationRunning = false;
        boundaryRestorationResult = null;
        settlementRunning = false;
        activeSettlementContext = null;
        return cancelledContext;
    }

    private void notifySettlementCancelled(SettlementContext context) {
        if (context != null) {
            listenerFor(context.generationId).onSettlementCancelled(
                    context.gestureId,
                    context.generationId,
                    context.sourceLogicalPageId);
        }
    }

    private PageSurfaceListener listenerFor(long generationId) {
        return leaseRegistry.listenerFor(generationId, pageSurfaceListener);
    }

    private long activeGenerationId() {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active == null ? -1L : active.getGenerationId();
    }

    private void notifyGestureCancelled(long gestureId, long generationId) {
        if (gestureId != NO_GESTURE_ID) {
            listenerFor(generationId).onGestureCancelled(gestureId, generationId);
        }
    }

    private void queueDeckRelease(PageDeckCoordinator.Release<Bitmap> release) {
        if (release == null) {
            return;
        }
        long generationId = release.getDeck().getGenerationId();
        if (preparedGenerations.remove(generationId)) {
            advanceOwnershipEpoch();
        }
        leaseRegistry.markReleaseRequested(generationId, release.getReason());
        queueEvent(() -> renderer.releaseDeck(generationId, release.getReason()));
    }

    private void markPromotionRelease(
            PageDeckCoordinator.Promotion<Bitmap> promotion) {
        PageDeckCoordinator.Release<Bitmap> release = promotion.getRelease();
        if (release != null) {
            leaseRegistry.markReleaseRequested(
                    release.getDeck().getGenerationId(),
                    release.getReason());
        }
    }

    private float currentPagePercent() {
        PlayLikeCurlModel interaction = interactionModelOrNull();
        PageState activeState;
        if (interaction.getActivePage() == ActivePage.LEFT) {
            activeState = interaction.getLeftPage();
        } else if (interaction.getActivePage() == ActivePage.RIGHT) {
            activeState = interaction.getRightPage();
        } else {
            activeState = interaction.getFrontPage();
        }
        return activeState.getCurlPosition() / PlayLikeCurlModel.GRID * 100f;
    }

    private SettlementContext settlementContext(PageChange pageChange) {
        return settlementContext(pageChange, pageChange);
    }

    private SettlementContext settlementContext(
            PageChange pageChange,
            PageChange reportedPageChange) {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        PageImage<Bitmap> source = deck.getSettlementPage(PageChange.NONE);
        PageImage<Bitmap> target = deck.getSettlementPage(pageChange);
        return SettlementContext.from(
                activeGestureId,
                source,
                target,
                reportedPageChange);
    }

    private PlayLikeCurlModel interactionModelOrNull() {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        if (spread != null) {
            return spread.getMotionModel();
        }
        return rendererPortraitModel();
    }

    private PlayLikeCurlModel rendererPortraitModel() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (!(deck instanceof PortraitPageDeck)) {
            return null;
        }
        return renderer.getPortraitModel();
    }

    private LandscapeSpreadModel landscapeModelOrNull() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (!(deck instanceof LandscapePageDeck)) {
            return null;
        }
        return renderer.getLandscapeSpreadModel();
    }

    private VelocityTracker obtainVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        return velocityTracker;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "PageSurfaceView mutations must run on the main thread");
        }
    }

    private static Interpolator toAndroidInterpolator(
            SettlementInterpolator interpolator) {
        return interpolator == SettlementInterpolator.DECELERATE
                ? new DecelerateInterpolator()
                : new AccelerateDecelerateInterpolator();
    }

    static final class BoundaryRestorationProtocol {
        static final class Completion {
            private final boolean releasePending;
            private final boolean publishBoundary;

            Completion(boolean releasePending, boolean publishBoundary) {
                this.releasePending = releasePending;
                this.publishBoundary = publishBoundary;
            }

            boolean shouldReleasePending() {
                return releasePending;
            }

            boolean shouldPublishBoundary() {
                return publishBoundary;
            }
        }

        private long lastToken;
        private long requestedToken = NO_BOUNDARY_FRAME_TOKEN;
        private long armedToken = NO_BOUNDARY_FRAME_TOKEN;

        synchronized long beginAwaitingFrame() {
            if (requestedToken != NO_BOUNDARY_FRAME_TOKEN) {
                throw new IllegalStateException("A boundary frame is already pending");
            }
            lastToken = Math.incrementExact(lastToken);
            requestedToken = lastToken;
            armedToken = NO_BOUNDARY_FRAME_TOKEN;
            return requestedToken;
        }

        synchronized boolean arm(long token) {
            if (token == NO_BOUNDARY_FRAME_TOKEN
                    || token != requestedToken
                    || armedToken != NO_BOUNDARY_FRAME_TOKEN) {
                return false;
            }
            armedToken = token;
            return true;
        }

        synchronized Completion complete(
                long token,
                boolean everyBaseTextureRendered,
                boolean everyDeclaredOverlayRendered,
                boolean pendingDeckPresent) {
            if (token == NO_BOUNDARY_FRAME_TOKEN
                    || token != requestedToken
                    || token != armedToken
                    || !everyBaseTextureRendered
                    || !everyDeclaredOverlayRendered) {
                return null;
            }
            requestedToken = NO_BOUNDARY_FRAME_TOKEN;
            armedToken = NO_BOUNDARY_FRAME_TOKEN;
            return new Completion(pendingDeckPresent, true);
        }

        synchronized void cancel(long token) {
            if (token == NO_BOUNDARY_FRAME_TOKEN) {
                return;
            }
            if (token == requestedToken || token == armedToken) {
                requestedToken = NO_BOUNDARY_FRAME_TOKEN;
                armedToken = NO_BOUNDARY_FRAME_TOKEN;
            }
        }

        synchronized long armedToken() {
            return armedToken;
        }

        synchronized long pendingToken() {
            return requestedToken;
        }
    }

    public interface OnPageChangeListener {
        void onPageChanged(int position);
    }

    private static final class SettlementContext {
        private final long gestureId;
        private final long generationId;
        private final String sourceLogicalPageId;
        private final String targetLogicalPageId;
        private final int targetOrdinal;
        private final PageChange pageChange;

        private SettlementContext(
                long gestureId,
                long generationId,
                String sourceLogicalPageId,
                String targetLogicalPageId,
                int targetOrdinal,
                PageChange pageChange) {
            this.gestureId = gestureId;
            this.generationId = generationId;
            this.sourceLogicalPageId = sourceLogicalPageId;
            this.targetLogicalPageId = targetLogicalPageId;
            this.targetOrdinal = targetOrdinal;
            this.pageChange = pageChange;
        }

        static SettlementContext from(
                long gestureId,
                PageImage<Bitmap> source,
                PageImage<Bitmap> target,
                PageChange pageChange) {
            return new SettlementContext(
                    gestureId,
                    source.getGenerationId(),
                    source.getLogicalPageId(),
                    target.getLogicalPageId(),
                    target.getOrdinal(),
                    pageChange);
        }
    }
}
