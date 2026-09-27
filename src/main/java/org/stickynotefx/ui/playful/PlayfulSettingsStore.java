package org.stickynotefx.ui.playful;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class PlayfulSettingsStore {
    private final Path path;

    public PlayfulSettingsStore(Path path) {
        this.path = path.toAbsolutePath();
    }

    public Path sidecarPath() { return path.resolveSibling("visuals.properties"); }

    public static PlayfulSettingsStore userSettings() {
        String configured = System.getenv("XDG_CONFIG_HOME");
        Path base = configured != null && Path.of(configured).isAbsolute()
                ? Path.of(configured) : Path.of(System.getProperty("user.home"), ".config");
        return new PlayfulSettingsStore(base.resolve("notesy/preferences.properties"));
    }

    public PlayfulSettings load() {
        if (!Files.exists(path)) return PlayfulSettings.DEFAULT;
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
            boolean enabled = Boolean.parseBoolean(properties.getProperty("playfulModeEnabled", "false"));
            PlayfulMode mode = PlayfulMode.valueOf(properties.getProperty("playfulModeType", "NONE"));
            return new PlayfulSettings(enabled, mode);
        } catch (IOException | IllegalArgumentException failure) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Could not load playful preferences; using standard reminders", failure);
            return PlayfulSettings.DEFAULT;
        }
    }

    public void save(PlayfulSettings settings) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "preferences-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("playfulModeEnabled", Boolean.toString(settings.playfulModeEnabled()));
            properties.setProperty("playfulModeType", settings.playfulModeType().name());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "Notesy preferences");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}