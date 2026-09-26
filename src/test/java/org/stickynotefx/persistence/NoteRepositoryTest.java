package org.stickynotefx.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.stickynotefx.model.Note;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoteRepositoryTest {
    @TempDir Path directory;

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