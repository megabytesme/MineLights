package megabytesme.minelights.runtime;

final class AngleMath {
    private AngleMath() { }

    /** Wraps a finite heading to (-180, 180], or returns NaN for an invalid heading. */
    static double normalizeYaw(double yaw) {
        if (!Double.isFinite(yaw)) {
            return Double.NaN;
        }
        double wrapped = yaw % 360.0D;
        if (wrapped <= -180.0D) {
            return wrapped + 360.0D;
        }
        if (wrapped > 180.0D) {
            return wrapped - 360.0D;
        }
        return wrapped;
    }
}
