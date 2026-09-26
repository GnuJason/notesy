package org.stickynotefx.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseProvider implements AutoCloseable {
    private final Connection connection;

    public DatabaseProvider(Path file) throws SQLException, IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY,
                        content TEXT NOT NULL DEFAULT '',
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        color TEXT NOT NULL DEFAULT '#FFF176',
                        reminder_timestamp INTEGER NULL,
                        theme TEXT NULL
                    )
                    """);
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
    }

    public static Path defaultPath() {
        String override = System.getProperty("stickynotefx.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).resolve("notes.db");
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute()
                ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("sticky-notefx").resolve("notes.db");
    }

    Connection connection() { return connection; }

    @Override
    public void close() throws SQLException { connection.close(); }
}