package org.stickynotefx.ui.playful;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlayfulSettingsStoreTest {
    @TempDir Path directory;

    @Test
    void profileSidecarPreservesIndependentNotesAndTheme() throws Exception {
        Path path = directory.resolve("visuals.properties");
        var profile = new org.stickynotefx.ui.preferences.ProfilePreferences(path);
        assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.LIGHT, profile.theme());
        assertFalse(profile.note(1, PlayfulSettings.DEFAULT).enabled());
        profile.setTheme(org.stickynotefx.theme.ThemeRegistry.Theme.DARK);
        profile.setNote(1, new org.stickynotefx.ui.preferences.ProfilePreferences.NoteVisual(true, PlayfulMode.EGG, true));
        profile.setNote(2, new org.stickynotefx.ui.preferences.ProfilePreferences.NoteVisual(true, PlayfulMode.TOASTER, false));
        profile = new org.stickynotefx.ui.preferences.ProfilePreferences(path);
        assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.DARK, profile.theme());
        assertTrue(profile.note(1, PlayfulSettings.DEFAULT).expired());
        assertEquals(PlayfulMode.TOASTER, profile.note(2, PlayfulSettings.DEFAULT).mode());
        assertFalse(profile.note(2, PlayfulSettings.DEFAULT).expired());
        assertFalse(new org.stickynotefx.ui.preferences.ProfilePreferences(directory.resolve("other/visuals.properties"))
                .note(1, PlayfulSettings.DEFAULT).enabled());
        profile.removeNote(1);
        assertEquals(PlayfulMode.NONE, profile.note(1, PlayfulSettings.DEFAULT).mode());
        assertEquals(PlayfulMode.TOASTER, profile.note(2, PlayfulSettings.DEFAULT).mode());
    }

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