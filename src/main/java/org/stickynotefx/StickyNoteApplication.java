package org.stickynotefx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.stickynotefx.persistence.DatabaseProvider;
import org.stickynotefx.persistence.NoteRepository;
import org.stickynotefx.ui.NoteManager;

public final class StickyNoteApplication extends Application {
    private DatabaseProvider database;
    private NoteManager manager;
    private ApplicationInstance instance;
    private boolean showingError;

    @Override public void start(Stage primary) {
        Platform.setImplicitExit(false);
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> showError(failure));
        try {
            var path = DatabaseProvider.defaultPath();
                instance = ApplicationInstance.open(path.toAbsolutePath().getParent(),
                    () -> Platform.runLater(() -> {
                    if (manager != null) manager.showManager();
                    }));
                if (!instance.isPrimary()) {
                Platform.exit();
                return;
            }
            database = new DatabaseProvider(path);
            manager = new NoteManager(new NoteRepository(database), primary);
            manager.restore();
            if (getParameters().getRaw().contains("--all-notes")) manager.showManager();
        } catch (Exception failure) {
            showError(failure);
            Platform.exit();
        }
    }

    private void showError(Throwable failure) {
        failure.printStackTrace();
        if (showingError) return;
        showingError = true;
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Your changes may not have been saved. Check available disk space and database permissions.\n\n"
                            + failure.getMessage());
            alert.setHeaderText("Notesy could not complete the operation");
            alert.showAndWait();
        } finally {
            showingError = false;
        }
    }

    @Override public void stop() throws Exception {
        try {
            if (manager != null) manager.close();
        } finally {
            try {
                if (database != null) database.close();
            } finally {
                if (instance != null) instance.close();
            }
        }
    }

    public static void main(String[] args) { launch(args); }
}