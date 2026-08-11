package karacken.curl;

/** Synchronous admission result for one atomic page-overlay replacement. */
public enum PageOverlayUpdateResult {
    ACCEPTED,
    INTERACTION_ACTIVE,
    UPDATE_PENDING,
    STALE_TARGET,
    SURFACE_UNAVAILABLE,
    INVALID_CONTENT
}
