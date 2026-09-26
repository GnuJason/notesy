package org.stickynotefx.ui.playful;

public enum PlayfulMode {
    NONE, EGG, BOMB, TOASTER;

    @Override public String toString() {
        String name = name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}