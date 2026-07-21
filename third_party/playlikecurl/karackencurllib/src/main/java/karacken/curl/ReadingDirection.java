package karacken.curl;

/** Maps physical touch input onto the reader's canonical logical page direction. */
public enum ReadingDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT;

    public float toLogicalX(float physicalX, float width) {
        return this == RIGHT_TO_LEFT ? width - physicalX : physicalX;
    }

    public PageChange pageChangeForVelocity(float velocityX) {
        PageChange physicalChange = velocityX < 0f
                ? PageChange.NEXT
                : PageChange.PREVIOUS;
        if (this == LEFT_TO_RIGHT) {
            return physicalChange;
        }
        return physicalChange == PageChange.NEXT
                ? PageChange.PREVIOUS
                : PageChange.NEXT;
    }
}
