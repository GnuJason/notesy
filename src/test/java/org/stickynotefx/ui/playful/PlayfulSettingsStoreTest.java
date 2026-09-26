package org.stickynotefx.ui.playful;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlayfulSettingsStoreTest {
    @TempDir Path directory;

    @Test
    void missingAndOlderProfilesDefaultToStandardReminders() throws Exception {
        Path path = directory.resolve("preferences.properties");
        var store = new PlayfulSettingsStore(path);
        assertEquals(PlayfulSettings.DEFAULT, store.load());
        Files.writeString(path, "unrelatedPreference=true\n");
        assertEquals(PlayfulSettings.DEFAULT, store.load());
        Files.writeString(path, "playfulModeEnabled=true\nplayfulModeType=FUTURE\n");
        assertEquals(PlayfulSettings.DEFAULT, store.load());
    }

    @Test
    void everyModeAndDisabledSelectionSurviveRestart() throws Exception {
        Path path = directory.resolve("notesy/preferences.properties");
        for (PlayfulMode mode : PlayfulMode.values()) {
            for (boolean enabled : new boolean[]{true, false}) {
                var settings = new PlayfulSettings(enabled, mode);
                new PlayfulSettingsStore(path).save(settings);
                assertEquals(settings, new PlayfulSettingsStore(path).load());
                assertEquals(enabled ? mode : PlayfulMode.NONE, settings.activeMode());
            }
        }
        try (var files = Files.list(path.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void failedSaveDoesNotReplaceExistingPreferences() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "existing content");
        var store = new PlayfulSettingsStore(parent.resolve("preferences.properties"));
        assertThrows(java.io.IOException.class, () -> store.save(PlayfulSettings.DEFAULT));
        assertEquals("existing content", Files.readString(parent));
    }
}