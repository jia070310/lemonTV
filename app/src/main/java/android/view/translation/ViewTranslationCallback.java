package android.view.translation;

/**
 * Compatibility shim for ViewTranslationCallback on Android < 12
 * This empty interface prevents ClassNotFoundException on older Android versions
 */
public interface ViewTranslationCallback {
    // Empty interface - not used on Android 9
}
