package org.stickynotefx.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.stickynotefx.persistence.DatabaseProvider;
import org.stickynotefx.persistence.NoteRepository;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "STICKYNOTEFX_UI_TEST", matches = "1")
class StickyNoteUiTest {
    @TempDir Path directory;

    @Test
    void editsBoundsColorsRemindersAndDeletionSurviveLifecycle() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        FutureTask<Void> exercise = new FutureTask<>(() -> {
            try (var database = new DatabaseProvider(directory.resolve("notes.db"))) {
                var repository = new NoteRepository(database);
                Stage primary = new Stage();
                var manager = new NoteManager(repository, primary);
                try {
                    var first = manager.createNote();
                    first.view().editor().setText("Remember the tea");
                    first.setReminder(System.currentTimeMillis() + 3_600_000);
                    assertEquals("Remember the tea", repository.findAll().getFirst().content());
                    assertNotNull(repository.findAll().getFirst().reminderTimestamp());
                    first.setReminder(null);
                    assertNull(repository.findAll().getFirst().reminderTimestamp());
                    first.view().stage().getScene().getRoot().applyCss();
                    first.view().stage().getScene().getRoot().layout();
                    assertTrue(first.view().editor().getWidth() > 200);
                    assertTrue(first.view().editor().getHeight() > 100);
                    Node drag = first.view().dragBar().getChildren().get(1);
                    double originalX = first.view().stage().getX();
                    double originalY = first.view().stage().getY();
                    mouse(drag, MouseEvent.MOUSE_PRESSED, originalX + 60, originalY + 20);
                    mouse(drag, MouseEvent.MOUSE_DRAGGED, originalX + 140, originalY + 70);
                    assertEquals(originalX + 80, first.view().stage().getX(), 1);
                    assertEquals(originalY + 50, first.view().stage().getY(), 1);
                    Node grip = first.view().resizeGrip();
                    mouse(grip, MouseEvent.MOUSE_PRESSED, 400, 400);
                    mouse(grip, MouseEvent.MOUSE_DRAGGED, 500, 450);
                    assertEquals(420, first.view().stage().getWidth(), 1);
                    assertEquals(350, first.view().stage().getHeight(), 1);
                    MenuButton options = (MenuButton) first.view().dragBar().getChildren().getLast();
                    HBox colors = (HBox) ((CustomMenuItem) options.getItems().getFirst()).getContent();
                    ((Button) colors.getChildren().get(1)).fire();
                    first.flush();
                    assertEquals("#B8E0D2", repository.findAll().getFirst().color());
                    assertEquals(420, repository.findAll().getFirst().width());
                    capture(first, "note-large.png");
                    mouse(grip, MouseEvent.MOUSE_PRESSED, 500, 450);
                    mouse(grip, MouseEvent.MOUSE_DRAGGED, 0, 0);
                    assertEquals(260, first.view().stage().getWidth(), 1);
                    assertEquals(220, first.view().stage().getHeight(), 1);
                    ((Button) colors.getChildren().getFirst()).fire();
                    capture(first, "note-minimum.png");
                    var second = manager.createNote();
                    second.view().editor().setText("Pending edit must not resurrect deletion");
                    manager.delete(second.note().id());
                    assertEquals(1, repository.findAll().size());
                    first.view().editor().setText("Saved on close");
                    long id = first.note().id();
                    manager.close();
                    assertEquals("Saved on close", repository.findAll().getFirst().content());
                    var restored = new StickyNoteController(repository.findAll().getFirst(), repository::update);
                    assertEquals("Saved on close", restored.view().editor().getText());
                    assertEquals(id, restored.note().id());
                    restored.dispose();
                } finally {
                    manager.close();
                    primary.hide();
                }
            }
            return null;
        });
        try {
            Platform.runLater(exercise);
            exercise.get(30, TimeUnit.SECONDS);
            verifyDebouncedAutosave();
        } finally {
            Platform.exit();
        }
    }

    private void verifyDebouncedAutosave() throws Exception {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                var database = new DatabaseProvider(directory.resolve("autosave.db"));
                var repository = new NoteRepository(database);
                var controller = new StickyNoteController(repository.create(org.stickynotefx.model.Note.blank(0, 0)), repository::update);
                controller.view().editor().setText("Saved by the debounce timer");
                var check = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
                check.setOnFinished(event -> {
                    try {
                        assertEquals("Saved by the debounce timer", repository.findAll().getFirst().content());
                        saved.complete(null);
                    } catch (Throwable failure) {
                        saved.completeExceptionally(failure);
                    } finally {
                        controller.dispose();
                        try {
                            database.close();
                        } catch (Exception failure) {
                            saved.completeExceptionally(failure);
                        }
                    }
                });
                check.play();
            } catch (Throwable failure) {
                saved.completeExceptionally(failure);
            }
        });
        saved.get(10, TimeUnit.SECONDS);
    }

    private static void mouse(Node target, javafx.event.EventType<MouseEvent> type, double screenX, double screenY) {
        target.fireEvent(new MouseEvent(type, 10, 10, screenX, screenY, MouseButton.PRIMARY,
                1, false, false, false, false, true, false, false, false, false, false,
                new PickResult(target, 10, 10)));
    }

    private static void capture(StickyNoteController controller, String name) throws Exception {
        var root = controller.view().stage().getScene().getRoot();
        root.applyCss();
        root.layout();
        var image = root.snapshot(null, null);
        BufferedImage pixels = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < pixels.getHeight(); row++) {
            for (int column = 0; column < pixels.getWidth(); column++) {
                pixels.setRGB(column, row, image.getPixelReader().getArgb(column, row));
            }
        }
        Path destination = Path.of("target", "ui-snapshots", name);
        Files.createDirectories(destination.getParent());
        ImageIO.write(pixels, "png", destination.toFile());
    }
}