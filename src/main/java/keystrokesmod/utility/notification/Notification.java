package keystrokesmod.utility.notification;

public class Notification {
    public enum Type {
        ENABLE,
        DISABLE,
        INFO
    }

    private final String title;
    private final String message;
    private final Type type;
    private final long createdAt;
    private final long displayDuration;
    private final long fadeInDuration;
    private final long fadeOutDuration;

    public Notification(String title, String message, Type type, long displayDuration) {
        this(title, message, type, displayDuration, 200, 300);
    }

    public Notification(String title, String message, Type type, long displayDuration, long fadeInDuration, long fadeOutDuration) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.displayDuration = displayDuration;
        this.fadeInDuration = fadeInDuration;
        this.fadeOutDuration = fadeOutDuration;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Type getType() {
        return type;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getDisplayDuration() {
        return displayDuration;
    }

    public long getFadeInDuration() {
        return fadeInDuration;
    }

    public long getFadeOutDuration() {
        return fadeOutDuration;
    }

    public long getElapsed() {
        return System.currentTimeMillis() - createdAt;
    }

    public boolean isExpired() {
        return getElapsed() > displayDuration + fadeInDuration + fadeOutDuration;
    }

    public float getAlpha() {
        long elapsed = getElapsed();
        if (elapsed < fadeInDuration) {
            return (float) elapsed / fadeInDuration;
        }
        if (elapsed < fadeInDuration + displayDuration) {
            return 1.0f;
        }
        long fadeOutElapsed = elapsed - fadeInDuration - displayDuration;
        return 1.0f - ((float) fadeOutElapsed / fadeOutDuration);
    }
}
