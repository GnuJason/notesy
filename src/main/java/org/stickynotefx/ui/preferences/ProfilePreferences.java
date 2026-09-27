package org.stickynotefx.ui.preferences;

import org.stickynotefx.theme.ThemeRegistry.Theme;
import org.stickynotefx.ui.playful.PlayfulMode;
import org.stickynotefx.ui.playful.PlayfulSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;

public final class ProfilePreferences {
    public record NoteVisual(boolean enabled, PlayfulMode mode, boolean expired) {
        public PlayfulSettings settings() { return new PlayfulSettings(enabled, mode); }
        public NoteVisual withExpired(boolean value) { return new NoteVisual(enabled, mode, value); }
    }

    private final Path file;
    private Properties values = new Properties();

    public ProfilePreferences(Path file) {
        this.file = file.toAbsolutePath();
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) {
                values.load(input);
            } catch (IOException | IllegalArgumentException failure) {
                values.clear();
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                        "Visual preferences unavailable; using defaults", failure);
            }
        }
    }

    public Theme theme() {
        try {
            return Theme.valueOf(values.getProperty("theme", "LIGHT"));
        } catch (IllegalArgumentException invalid) {
            return Theme.LIGHT;
        }
    }

    public NoteVisual note(long id, PlayfulSettings defaults) {
        String prefix = "note." + id + ".";
        try {
            return new NoteVisual(Boolean.parseBoolean(values.getProperty(prefix + "playfulModeEnabled",
                    Boolean.toString(defaults.playfulModeEnabled()))),
                    PlayfulMode.valueOf(values.getProperty(prefix + "playfulModeType", defaults.playfulModeType().name())),
                    Boolean.parseBoolean(values.getProperty(prefix + "playfulExpiredState", "false")));
        } catch (IllegalArgumentException invalid) {
            return new NoteVisual(false, PlayfulMode.NONE, false);
        }
    }

    public void setTheme(Theme theme) {
        Properties next = copy();
        next.setProperty("theme", theme.name());
        commit(next);
    }

    public void setNote(long id, NoteVisual visual) {
        Properties next = copy();
        String prefix = "note." + id + ".";
        next.setProperty(prefix + "playfulModeEnabled", Boolean.toString(visual.enabled()));
        next.setProperty(prefix + "playfulModeType", visual.mode().name());
        next.setProperty(prefix + "playfulExpiredState", Boolean.toString(visual.expired()));
        commit(next);
    }

    public void removeNote(long id) {
        if (!hasNote(id)) return;
        Properties next = copy();
        next.keySet().removeIf(key -> key.toString().startsWith("note." + id + "."));
        try {
            commit(next);
        } finally {
            values = next;
        }
    }

    public boolean hasNote(long id) { return values.containsKey("note." + id + ".playfulModeType"); }

    private Properties copy() {
        Properties copy = new Properties();
        copy.putAll(values);
        return copy;
    }

    private void commit(Properties next) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "visuals-", ".tmp");
            try {
                try (var output = Files.newOutputStream(temporary)) {
                    next.store(output, "Notesy profile visuals");
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                values = next;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}