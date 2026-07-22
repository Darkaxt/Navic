package karacken.curl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Production page-curl surface.
 *
 * <p>The client owns bitmap decoding and page identity. This view owns only interaction,
 * settlement, and GL resource lifecycle. Public mutations must run on the Android main thread.
 */
public class PageSurfaceView extends GLSurfaceView {
    private static final float FLING_THRESHOLD_PX_PER_SECOND = 200f;
    public static final long NO_GESTURE_ID = -1L;
    static final long NO_BOUNDARY_FRAME_TOKEN = 0L;
    private static final PageSurfaceListener NO_OP_LISTENER = new PageSurfaceListener() {};

    private final PageDeckCoordinator<Bitmap> deckCoordinator = new PageDeckCoordinator<>();
    private final DeckLeaseRegistry leaseRegistry = new DeckLeaseRegistry();
    private final BoundaryRestorationProtocol boundaryRestorationProtocol =
            new BoundaryRestorationProtocol();
    private final Set<Long> preparedGenerations = new LinkedHashSet<>();
    private final PageRenderer renderer;
    private final int touchSlop;

    private PageSurfaceListener pageSurfaceListener = NO_OP_LISTENER;
    private RenderCapabilities renderCapabilities;
    private ValueAnimator settlementAnimator;
    private SettlementContext activeSettlementContext;
    private Settlement boundaryRestorationResult;
    private VelocityTracker velocityTracker;
    private ReadingDirection readingDirection = ReadingDirection.LEFT_TO_RIGHT;
    private ReadingDirection activeGestureReadingDirection = ReadingDirection.LEFT_TO_RIGHT;
    private boolean settlementRunning;
    private boolean boundaryRestorationRunning;
    private boolean gestureAccepted;
    private boolean gestureMoved;
    private boolean surfaceVisible = true;
    private boolean attached;
    private boolean disposed;
    private long activeGestureId = NO_GESTURE_ID;
    private float gestureDownX;
    private float gestureDownY;
    private OnPageChangeListener onPageChangeListener;

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
                leaseRegistry.markReleaseRequested(generationId, reason);
                post(() -> handleDeckReleased(generationId, reason));
            }

            @Override
            public void onRenderFailure(RenderFailure failure) {
                post(() -> handleRenderFailure(failure));
            }
        });

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
                long frameToken = boundaryRestorationProtocol.armedToken();
                if (frameToken == NO_BOUNDARY_FRAME_TOKEN) {
                    return;
                }
                post(() -> postOnAnimation(() -> handleRenderedFrame(frameToken)));
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
        if (disposed || attached) {
            return;
        }
        attached = true;
        onResume();
        requestRender();
    }

    /** Cancels interaction and pauses frame production without releasing the active deck. */
    public void detach() {
        requireMainThread();
        if (!attached) {
            return;
        }
        attached = false;
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
        requireMainThread();
        if (deck == null) {
            throw new IllegalArgumentException("deck must not be null");
        }
        if (disposed) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.DISPOSED);
            return;
        }
        if (!attached) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.SESSION_DETACHED);
            return;
        }
        if (renderCapabilities == null) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.CAPABILITIES_UNAVAILABLE);
            return;
        }
        PageDeckCoordinator.Offer<Bitmap> offer = deckCoordinator.offer(deck);
        if (offer.getPlacement() == PageDeckCoordinator.Placement.REJECTED) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(), offer.getRejectionReason());
            return;
        }
        if (offer.getPlacement() == PageDeckCoordinator.Placement.UNCHANGED) {
            return;
        }
        leaseRegistry.acquire(deck.getGenerationId(), pageSurfaceListener);
        for (PageDeckCoordinator.Release<Bitmap> release : offer.getReleases()) {
            queueDeckRelease(release);
        }
        boolean activateWhenPrepared =
                offer.getPlacement() == PageDeckCoordinator.Placement.ACTIVE;
        preparedGenerations.remove(deck.getGenerationId());
        queueEvent(() -> renderer.prepareDeck(deck, activateWhenPrepared));
        requestRender();
    }

    /** Returns whether a replacement submission would enter the pending settlement slot. */
    public boolean isSettlementRunning() {
        requireMainThread();
        return deckCoordinator.isSettling();
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
            cancelGesture();
        } else {
            requestRender();
        }
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
            rejectGesture(gestureId, GestureRejectionReason.BOUNDARY);
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
        preparedGenerations.remove(generationId);
        PageDeckCoordinator.Release<Bitmap> release =
                deckCoordinator.release(generationId);
        queueDeckRelease(release);
        requestRender();
    }

    /** Idempotently releases renderer, gesture, and deck state. */
    public void dispose() {
        requireMainThread();
        if (disposed) {
            return;
        }
        disposed = true;
        SettlementContext cancelledSettlement = cancelSettlementAnimator();
        recycleVelocityTracker();
        preparedGenerations.clear();
        deckCoordinator.dispose();
        if (attached) {
            attached = false;
            setPreserveEGLContextOnPause(false);
            queueEvent(renderer::dispose);
            requestRender();
            onPause();
        }
        renderer.abandonClientState();
        notifySettlementCancelled(cancelledSettlement);
        releaseAllOutstandingLeases();
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
                obtainVelocityTracker().addMovement(event);
                float logicalDownX = activeGestureReadingDirection.toLogicalX(
                        gestureDownX, Math.max(1f, getWidth()));
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
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
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
        trimPreparedGenerations();
        owner.onDeckPrepared(generationId);
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
        pageSurfaceListener.onCapabilitiesAvailable(capabilities);
    }

    private void handleDeckReleased(
            long generationId,
            DeckReleaseReason reason) {
        preparedGenerations.remove(generationId);
        deckCoordinator.release(generationId);
        DeckLeaseRegistry.Lease lease = leaseRegistry.release(generationId);
        if (lease != null) {
            DeckReleaseReason effectiveReason =
                    lease.getReleaseReason() == null ? reason : lease.getReleaseReason();
            lease.getListener().onDeckReleased(generationId, effectiveReason);
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
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        long generationId = active == null ? -1L : active.getGenerationId();
        listenerFor(generationId).onGestureRejected(gestureId, generationId, reason);
    }

    private void dragInteraction(float physicalX) {
        float surfaceWidth = Math.max(1f, getWidth());
        float logicalX = activeGestureReadingDirection.toLogicalX(physicalX, surfaceWidth);
        float gestureWidth = activeDeckIsLandscape()
                ? Math.max(1f, surfaceWidth / 2f)
                : surfaceWidth;
        if (landscapeModelOrNull() != null) {
            landscapeModelOrNull().dragTo(logicalX, gestureWidth);
        } else {
            interactionModelOrNull().dragTo(logicalX, gestureWidth);
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
        SettlementContext context = settlementContext(PageChange.NONE);
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
                    GestureRejectionReason.BOUNDARY);
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

    private void releaseAllOutstandingLeases() {
        for (DeckLeaseRegistry.Lease lease :
                leaseRegistry.releaseAll(DeckReleaseReason.DISPOSED)) {
            lease.getListener().onDeckReleased(
                    lease.getGenerationId(),
                    lease.getReleaseReason());
        }
    }

    private void queueDeckRelease(PageDeckCoordinator.Release<Bitmap> release) {
        if (release == null) {
            return;
        }
        long generationId = release.getDeck().getGenerationId();
        preparedGenerations.remove(generationId);
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
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        PageImage<Bitmap> source = deck.getSettlementPage(PageChange.NONE);
        PageImage<Bitmap> target = deck.getSettlementPage(pageChange);
        return SettlementContext.from(activeGestureId, source, target);
    }

    private boolean activeDeckIsLandscape() {
        return deckCoordinator.getActiveDeck() instanceof LandscapePageDeck;
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

        private SettlementContext(
                long gestureId,
                long generationId,
                String sourceLogicalPageId,
                String targetLogicalPageId,
                int targetOrdinal) {
            this.gestureId = gestureId;
            this.generationId = generationId;
            this.sourceLogicalPageId = sourceLogicalPageId;
            this.targetLogicalPageId = targetLogicalPageId;
            this.targetOrdinal = targetOrdinal;
        }

        static SettlementContext from(
                long gestureId,
                PageImage<Bitmap> source,
                PageImage<Bitmap> target) {
            return new SettlementContext(
                    gestureId,
                    source.getGenerationId(),
                    source.getLogicalPageId(),
                    target.getLogicalPageId(),
                    target.getOrdinal());
        }
    }
}
