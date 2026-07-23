package karacken.curl;

import java.util.List;
import java.util.Objects;

final class PageSurfaceOwnershipSnapshotCoordinator {
    enum MainPostStatus {
        ACCEPTED,
        TERMINALIZED
    }

    interface MainSample {
        long ownershipEpoch();

        PageSurfaceOwnershipSnapshot withTextures(
                int textures,
                int textureLimit);
    }

    interface Host {
        void requireMainThread();

        MainSample captureMainSample();

        long currentOwnershipEpoch();

        int captureTextureCount();

        int captureTextureLimit();

        boolean queueGl(Runnable action);

        MainPostStatus postMain(Runnable action);

        void deliver(
                PageSurfaceOwnershipResult.Callback callback,
                PageSurfaceOwnershipResult result);
    }

    private final Host host;
    private final PageSurfaceOwnershipRequestRegistry requests;

    PageSurfaceOwnershipSnapshotCoordinator(Host host, int capacity) {
        this.host = Objects.requireNonNull(host, "host");
        requests = new PageSurfaceOwnershipRequestRegistry(capacity);
    }

    void setCapacityAvailableListener(Runnable listener) {
        host.requireMainThread();
        requests.setCapacityAvailableListener(listener);
    }

    void clearCapacityAvailableListener(Runnable listener) {
        host.requireMainThread();
        requests.clearCapacityAvailableListener(listener);
    }

    PageSurfaceOwnershipRequestRegistry.Registration request(
            PageSurfaceOwnershipResult.Callback callback) {
        host.requireMainThread();
        PageSurfaceOwnershipRequestRegistry.Registration registration =
                requests.register(callback);
        if (registration.status()
                == PageSurfaceOwnershipRequestRegistry.Registration.Status.ACCEPTED) {
            queueAttempt(registration.token());
        }
        return registration;
    }

    private void queueAttempt(long token) {
        host.requireMainThread();
        MainSample sample = host.captureMainSample();
        PageSurfaceOwnershipRequestRegistry.Attempt attempt =
                requests.beginAttempt(token, sample.ownershipEpoch());
        if (attempt == null) {
            return;
        }
        boolean accepted;
        try {
            accepted = host.queueGl(() -> captureGl(attempt, sample));
        } catch (Throwable failure) {
            accepted = false;
        }
        if (!accepted) {
            rejectOnMain(
                    token,
                    PageSurfaceOwnershipResult.Status.QUEUE_REJECTED);
        }
    }

    private void captureGl(
            PageSurfaceOwnershipRequestRegistry.Attempt attempt,
            MainSample sample) {
        final int textures;
        final int textureLimit;
        try {
            textures = host.captureTextureCount();
            textureLimit = host.captureTextureLimit();
        } catch (Throwable failure) {
            postRejectionToMain(
                    attempt.token(),
                    PageSurfaceOwnershipResult.Status.QUEUE_REJECTED);
            return;
        }
        MainPostStatus postStatus = host.postMain(() -> finish(
                attempt,
                sample,
                textures,
                textureLimit));
        requireAcceptedOrTerminalized(attempt.token(), postStatus);
    }

    private void postRejectionToMain(
            long token,
            PageSurfaceOwnershipResult.Status status) {
        MainPostStatus postStatus =
                host.postMain(() -> rejectOnMain(token, status));
        requireAcceptedOrTerminalized(token, postStatus);
    }

    private void requireAcceptedOrTerminalized(
            long token,
            MainPostStatus postStatus) {
        if (postStatus == MainPostStatus.ACCEPTED) {
            return;
        }
        if (postStatus == MainPostStatus.TERMINALIZED
                && !requests.contains(token)) {
            return;
        }
        throw new IllegalStateException(
                "Main terminal executor rejected a live ownership request");
    }

    private void finish(
            PageSurfaceOwnershipRequestRegistry.Attempt attempt,
            MainSample sample,
            int textures,
            int textureLimit) {
        host.requireMainThread();
        PageSurfaceOwnershipRequestRegistry.Completion completion =
                requests.finishAttempt(
                        attempt,
                        host.currentOwnershipEpoch());
        switch (completion.status()) {
            case MISSING:
                return;
            case RETRY:
                queueAttempt(attempt.token());
                return;
            case COMPLETE:
                host.deliver(
                        completion.callback(),
                        PageSurfaceOwnershipResult.available(
                                sample.withTextures(
                                        textures,
                                        textureLimit)));
                return;
            default:
                throw new AssertionError("Unhandled ownership completion");
        }
    }

    private void rejectOnMain(
            long token,
            PageSurfaceOwnershipResult.Status status) {
        host.requireMainThread();
        PageSurfaceOwnershipResult.Callback callback = requests.take(token);
        if (callback != null) {
            host.deliver(
                    callback,
                    PageSurfaceOwnershipResult.unavailable(status));
        }
    }

    List<PageSurfaceOwnershipResult.Callback> drain() {
        host.requireMainThread();
        return requests.drain();
    }

    int size() {
        host.requireMainThread();
        return requests.size();
    }

    int capacity() {
        return requests.capacity();
    }
}
