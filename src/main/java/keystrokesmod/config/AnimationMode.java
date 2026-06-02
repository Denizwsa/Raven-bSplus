package keystrokesmod.config;

public enum AnimationMode {
    VANILLA,
    EXHIBITION,
    ETB,
    SIGMA,
    DORTWARE,
    PLAIN,
    SPIN,
    AVATAR,
    SWONG,
    SWANG,
    SWANK,
    STYLES,
    NUDGE,
    PUNCH,
    JIGSAW,
    SLIDE;

    public static AnimationMode fromName(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (NullPointerException | IllegalArgumentException ex) {
            return VANILLA;
        }
    }
}
