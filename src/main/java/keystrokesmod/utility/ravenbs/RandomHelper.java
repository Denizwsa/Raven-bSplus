package keystrokesmod.utility.ravenbs;

import keystrokesmod.utility.Utils;

public final class RandomHelper {
    private RandomHelper() {
    }

    public static double nextDouble(double min, double max) {
        return Utils.randomizeDouble(min, max);
    }

    public static float nextFloat(float min, float max) {
        return (float) Utils.randomizeDouble(min, max);
    }
}
