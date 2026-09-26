package org.stickynotefx.persistence;

import org.stickynotefx.model.Note;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class NoteRepository {
    private final Connection connection;

    public NoteRepository(DatabaseProvider provider) { connection = provider.connection(); }

    public synchronized Note create(Note note) {
        try (var statement = connection.prepareStatement("""
                INSERT INTO notes (content, x, y, width, height, color, reminder_timestamp, theme)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """)) {
            bind(statement, note);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("No ID returned for new note");
                return note.withId(result.getLong(1));
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    public synchronized void update(Note note) {
        try (var statement = connection.prepareStatement("""
                UPDATE notes SET content=?, x=?, y=?, width=?, height=?, color=?,
                    reminder_timestamp=?, theme=? WHERE id=?
                """)) {
            bind(statement, note);
            statement.setLong(9, note.id());
            if (statement.executeUpdate() != 1) throw new SQLException("Note no longer exists: " + note.id());
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    public synchronized List<Note> findAll() {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM notes ORDER BY id")) {
            List<Note> notes = new ArrayList<>();
            while (result.next()) notes.add(read(result));
            return notes;
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    public synchronized void delete(long id) {
        try (var statement = connection.prepareStatement("DELETE FROM notes WHERE id=?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    public synchronized boolean clearReminder(long id, long expectedTimestamp) {
        try (var statement = connection.prepareStatement("""
                UPDATE notes SET reminder_timestamp=NULL WHERE id=? AND reminder_timestamp=?
                """)) {
            statement.setLong(1, id);
            statement.setLong(2, expectedTimestamp);
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    private static void bind(PreparedStatement statement, Note note) throws SQLException {
        statement.setString(1, note.content());
        statement.setLong(2, Math.round(note.x()));
        statement.setLong(3, Math.round(note.y()));
        statement.setLong(4, Math.round(note.width()));
        statement.setLong(5, Math.round(note.height()));
        statement.setString(6, note.color());
        if (note.reminderTimestamp() == null) statement.setNull(7, Types.BIGINT);
        else statement.setLong(7, note.reminderTimestamp());
        statement.setString(8, note.theme());
    }

    private static Note read(ResultSet result) throws SQLException {
        long reminder = result.getLong("reminder_timestamp");
        Long timestamp = result.wasNull() ? null : reminder;
        return new Note(result.getLong("id"), result.getString("content"),
                result.getDouble("x"), result.getDouble("y"), result.getDouble("width"),
                result.getDouble("height"), result.getString("color"), timestamp, result.getString("theme"));
    }

    private static IllegalStateException storageFailure(SQLException failure) {
        return new IllegalStateException("Could not access the notes database", failure);
    }
}