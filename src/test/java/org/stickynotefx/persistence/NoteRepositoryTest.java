package org.stickynotefx.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.stickynotefx.model.Note;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class NoteRepositoryTest {
    @TempDir Path directory;

    @Test
    void historicalV10DatabaseAndSidecarRemainBackwardCompatible() throws Exception {
        Path file = directory.resolve("notes.db");
        try (var fixture = getClass().getResourceAsStream("/fixtures/notes-v1.0.db")) {
            assertNotNull(fixture);
            Files.copy(fixture, file);
        }
        String originalSchema = schema(file);
        try (var database = new DatabaseProvider(file)) {
            var repository = new NoteRepository(database);
            var notes = repository.findAll();
            assertEquals(2, notes.size());
            assertEquals(new Note(1, "v1.0 reminder\nOriginal content", -100, 80, 360, 280,
                    "#B8E0D2", 1800000000000L, "legacy-theme"), notes.getFirst());
            assertNull(notes.get(1).theme());
            assertNull(notes.get(1).reminderTimestamp());
            repository.update(notes.getFirst().withContent("Updated by staged features"));
            var profile = new org.stickynotefx.ui.preferences.ProfilePreferences(directory.resolve("visuals.properties"));
            profile.setTheme(org.stickynotefx.theme.ThemeRegistry.Theme.DARK);
            profile.setNote(1, new org.stickynotefx.ui.preferences.ProfilePreferences.NoteVisual(
                    true, org.stickynotefx.ui.playful.PlayfulMode.EGG, true));
        }
        assertEquals(originalSchema, schema(file));
        try (var database = new DatabaseProvider(file)) {
            var repository = new NoteRepository(database);
            assertEquals("Updated by staged features", repository.findAll().getFirst().content());
            assertEquals("legacy-theme", repository.findAll().getFirst().theme());
            try (var statement = database.connection().createStatement();
                 var result = statement.executeQuery("PRAGMA integrity_check")) {
                assertTrue(result.next());
                assertEquals("ok", result.getString(1));
            }
        }
        Path output = Path.of("target/compatibility/notes-v1.0-updated.db");
        Files.createDirectories(output.getParent());
        Files.copy(file, output, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(directory.resolve("visuals.properties"), output.resolveSibling("visuals.properties"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private String schema(Path file) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT sql FROM sqlite_master WHERE name='notes'")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    @Test
    void roundTripsAllFieldsAcrossRestart() throws Exception {
        Path file = directory.resolve("notes.db");
        Note saved;
        try (var database = new DatabaseProvider(file)) {
            var repository = new NoteRepository(database);
            saved = repository.create(new Note(0, "Tea\n'quoted' <text>", -100, 80, 360, 280,
                    "#B8E0D2", 1800000000000L, "future-theme"));
            assertTrue(saved.id() > 0);
        }
        try (var database = new DatabaseProvider(file)) {
            assertEquals(saved, new NoteRepository(database).findAll().getFirst());
        }
    }

    @Test
    void updatesAndDeletesWithoutResurrectingNotes() throws Exception {
        try (var database = new DatabaseProvider(directory.resolve("notes.db"))) {
            var repository = new NoteRepository(database);
            Note saved = repository.create(Note.blank(100, 120));
            Note changed = saved.withContent("Updated").withColor("#F6BBC6").withReminder(123L);
            repository.update(changed);
            assertEquals(changed, repository.findAll().getFirst());
            repository.delete(saved.id());
            assertTrue(repository.findAll().isEmpty());
            assertThrows(IllegalStateException.class, () -> repository.update(changed));
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void staleReminderCannotClearReplacement() throws Exception {
        try (var database = new DatabaseProvider(directory.resolve("notes.db"))) {
            var repository = new NoteRepository(database);
            Note saved = repository.create(Note.blank(0, 0).withReminder(200L));
            assertFalse(repository.clearReminder(saved.id(), 100L));
            assertEquals(200L, repository.findAll().getFirst().reminderTimestamp());
            assertTrue(repository.clearReminder(saved.id(), 200L));
            assertNull(repository.findAll().getFirst().reminderTimestamp());
            assertFalse(repository.clearReminder(saved.id(), 200L));
        }
    }
}