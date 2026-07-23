package karacken.curl;

public final class PageSurfaceOwnershipSnapshot {
    public interface Callback {
        void onSnapshot(PageSurfaceOwnershipSnapshot snapshot);
    }

    private final int activeDeckLeases;
    private final int activeDeckLeaseLimit;
    private final int pendingDeckLeases;
    private final int pendingDeckLeaseLimit;
    private final int releaseInFlightDeckLeases;
    private final int releaseInFlightDeckLeaseLimit;
    private final int orphanDeckLeases;
    private final int orphanDeckLeaseLimit;
    private final int textures;
    private final int textureLimit;

    public PageSurfaceOwnershipSnapshot(
            int activeDeckLeases,
            int activeDeckLeaseLimit,
            int pendingDeckLeases,
            int pendingDeckLeaseLimit,
            int releaseInFlightDeckLeases,
            int releaseInFlightDeckLeaseLimit,
            int orphanDeckLeases,
            int orphanDeckLeaseLimit,
            int textures,
            int textureLimit) {
        this.activeDeckLeases = activeDeckLeases;
        this.activeDeckLeaseLimit = activeDeckLeaseLimit;
        this.pendingDeckLeases = pendingDeckLeases;
        this.pendingDeckLeaseLimit = pendingDeckLeaseLimit;
        this.releaseInFlightDeckLeases = releaseInFlightDeckLeases;
        this.releaseInFlightDeckLeaseLimit = releaseInFlightDeckLeaseLimit;
        this.orphanDeckLeases = orphanDeckLeases;
        this.orphanDeckLeaseLimit = orphanDeckLeaseLimit;
        this.textures = textures;
        this.textureLimit = textureLimit;
    }

    public int getActiveDeckLeases() {
        return activeDeckLeases;
    }

    public int getActiveDeckLeaseLimit() {
        return activeDeckLeaseLimit;
    }

    public int getPendingDeckLeases() {
        return pendingDeckLeases;
    }

    public int getPendingDeckLeaseLimit() {
        return pendingDeckLeaseLimit;
    }

    public int getReleaseInFlightDeckLeases() {
        return releaseInFlightDeckLeases;
    }

    public int getReleaseInFlightDeckLeaseLimit() {
        return releaseInFlightDeckLeaseLimit;
    }

    public int getOrphanDeckLeases() {
        return orphanDeckLeases;
    }

    public int getOrphanDeckLeaseLimit() {
        return orphanDeckLeaseLimit;
    }

    public int getTextures() {
        return textures;
    }

    public int getTextureLimit() {
        return textureLimit;
    }
}
