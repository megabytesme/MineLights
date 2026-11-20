package megabytesme.minelights.runtime;

public final class AngleMathTest {
    private AngleMathTest() { }

    public static void main(String[] args) {
        assertHeading(60.0D, 420.0D);
        assertHeading(-60.0D, -420.0D);
        assertHeading(180.0D, -180.0D);
        assertHeading(180.0D, 180.0D);
        assertHeading(-179.0D, 181.0D);
        for (double invalid : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            if (!Double.isNaN(AngleMath.normalizeYaw(invalid))) {
                throw new AssertionError("Expected invalid heading");
            }
        }
        double large = AngleMath.normalizeYaw(Double.MAX_VALUE);
        if (!(large > -180.0D && large <= 180.0D)) {
            throw new AssertionError("Expected normalized heading, got " + large);
        }
    }

    private static void assertHeading(double expected, double raw) {
        double actual = AngleMath.normalizeYaw(raw);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
