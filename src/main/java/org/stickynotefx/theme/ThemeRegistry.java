package org.stickynotefx.theme;

import java.util.Map;

public final class ThemeRegistry {
    public enum Theme {
        LIGHT, DARK, PAPER;

        @Override public String toString() {
            return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    public Map<String, ThemeEngine> themes() {
        return Map.of("light", root -> apply(root, Theme.LIGHT),
                "dark", root -> apply(root, Theme.DARK), "paper", root -> apply(root, Theme.PAPER));
    }

    public static void apply(javafx.scene.Parent root, Theme theme) {
        for (String stylesheet : new String[]{"notes.css", "themes.css"}) {
            String url = ThemeRegistry.class.getResource("/org/stickynotefx/" + stylesheet).toExternalForm();
            if (!root.getStylesheets().contains(url)) root.getStylesheets().add(url);
        }
        root.getStyleClass().removeAll("theme-light", "theme-dark", "theme-paper");
        root.getStyleClass().add("theme-" + theme.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Theme current(javafx.scene.Parent root) {
        for (Theme theme : Theme.values()) {
            if (root.getStyleClass().contains("theme-" + theme.name().toLowerCase(java.util.Locale.ROOT))) return theme;
        }
        return Theme.LIGHT;
    }
}