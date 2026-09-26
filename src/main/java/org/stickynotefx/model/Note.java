package org.stickynotefx.model;

public record Note(long id, String content, double x, double y, double width, double height,
                   String color, Long reminderTimestamp, String theme) {
    public static final String DEFAULT_COLOR = "#FFF176";

    public Note {
        content = content == null ? "" : content;
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            color = DEFAULT_COLOR;
        }
        if (!Double.isFinite(x)) x = 100;
        if (!Double.isFinite(y)) y = 100;
        width = Double.isFinite(width) ? Math.max(260, width) : 320;
        height = Double.isFinite(height) ? Math.max(220, height) : 300;
    }

    public static Note blank(double x, double y) {
        return new Note(0, "", x, y, 320, 300, DEFAULT_COLOR, null, null);
    }

    public Note withId(long value) {
        return new Note(value, content, x, y, width, height, color, reminderTimestamp, theme);
    }

    public Note withContent(String value) {
        return new Note(id, value, x, y, width, height, color, reminderTimestamp, theme);
    }

    public Note withBounds(double left, double top, double newWidth, double newHeight) {
        return new Note(id, content, left, top, newWidth, newHeight, color, reminderTimestamp, theme);
    }

    public Note withColor(String value) {
        return new Note(id, content, x, y, width, height, value, reminderTimestamp, theme);
    }

    public Note withReminder(Long value) {
        return new Note(id, content, x, y, width, height, color, value, theme);
    }
}