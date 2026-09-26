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
import org.stickynotefx.model.Note;
import org.stickynotefx.ui.playful.PlayfulMode;
import org.stickynotefx.ui.playful.PlayfulPreferencesDialog;
import org.stickynotefx.ui.playful.PlayfulReminderOverlay;
import org.stickynotefx.ui.playful.PlayfulSettings;
import org.stickynotefx.ui.playful.PlayfulSettingsStore;

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
                var manager = new NoteManager(repository, primary,
                    new PlayfulSettingsStore(directory.resolve("preferences.properties")));
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
                    verifyPlayfulControls(manager, first, repository);
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
            verifyPlayfulAnimations();
            verifyScheduledPlayfulDelivery();
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

    private void verifyPlayfulControls(NoteManager manager, StickyNoteController first,
                                       NoteRepository repository) throws Exception {
        assertNull(first.view().playfulOverlay());
        var dialog = new PlayfulPreferencesDialog(first.view().stage(), PlayfulSettings.DEFAULT,
                manager::setPlayfulSettings);
        dialog.show();
        var fields = (javafx.scene.layout.VBox) dialog.getDialogPane().getContent();
        var enabled = (javafx.scene.control.CheckBox) fields.getChildren().get(0);
        @SuppressWarnings("unchecked")
        var mode = (javafx.scene.control.ComboBox<PlayfulMode>) fields.getChildren().get(1);
        assertFalse(enabled.isSelected());
        assertTrue(mode.isDisabled());
        enabled.fire();
        assertEquals(PlayfulMode.EGG, first.view().playfulSettings().activeMode());
        for (PlayfulMode selected : new PlayfulMode[]{PlayfulMode.EGG, PlayfulMode.BOMB, PlayfulMode.TOASTER}) {
            mode.setValue(selected);
            assertEquals(selected, first.view().playfulSettings().activeMode());
            assertEquals(selected, new PlayfulSettingsStore(directory.resolve("preferences.properties")).load().activeMode());
        }
        capture(dialog.getDialogPane(), "playful-preferences.png");
        dialog.close();
        for (PlayfulMode selected : new PlayfulMode[]{PlayfulMode.EGG, PlayfulMode.BOMB, PlayfulMode.TOASTER}) {
            long timestamp = System.currentTimeMillis() + 3_600_000;
            first.setReminder(timestamp);
            manager.setPlayfulSettings(new PlayfulSettings(true, selected));
            var overlay = first.view().playfulOverlay();
            assertTrue(overlay.isMouseTransparent());
            assertFalse(overlay.isFocusTraversable());
            assertEquals(PlayfulReminderOverlay.State.SCHEDULED, overlay.state());
            String content = first.view().editor().getText();
            manager.reminderDelivered(first.note().id(), timestamp + 1);
            assertEquals(PlayfulReminderOverlay.State.SCHEDULED, overlay.state());
            assertEquals(timestamp, first.note().reminderTimestamp());
            capture(first, selected.name().toLowerCase(java.util.Locale.ROOT) + "-scheduled.png");
            var reminder = new ReminderDialog(first.view().stage(), timestamp);
            reminder.show();
            assertTrue(reminder.isShowing());
            reminder.close();
            var another = manager.createNote();
            assertEquals(selected, another.view().playfulSettings().activeMode());
            manager.reminderDelivered(first.note().id(), timestamp);
            assertNull(repository.findAll().stream().filter(note -> note.id() == first.note().id())
                    .findFirst().orElseThrow().reminderTimestamp());
            assertEquals(content, first.view().editor().getText());
            assertEquals(PlayfulReminderOverlay.State.FIRED, overlay.state());
            assertTrue(overlay.isAnimating());
            manager.setPlayfulSettings(PlayfulSettings.DEFAULT);
            assertNull(first.view().playfulOverlay());
            assertNull(another.view().playfulOverlay());
            assertFalse(overlay.isAnimating());
            assertFalse(overlay.isVisible());
            manager.delete(another.note().id());
        }
        var standard = new ReminderDialog(first.view().stage(), null);
        capture(first, "standard-note.png");
        standard.show();
        capture(standard.getDialogPane(), "reminder-dialog.png");
        Path snapshots = Path.of("target", "ui-snapshots");
        BufferedImage noteImage = ImageIO.read(snapshots.resolve("standard-note.png").toFile());
        BufferedImage dialogImage = ImageIO.read(snapshots.resolve("reminder-dialog.png").toFile());
        BufferedImage combined = new BufferedImage(noteImage.getWidth() + dialogImage.getWidth() + 24,
                Math.max(noteImage.getHeight(), dialogImage.getHeight()) + 24, BufferedImage.TYPE_INT_ARGB);
        var graphics = combined.createGraphics();
        try {
            graphics.setColor(new java.awt.Color(241, 243, 245));
            graphics.fillRect(0, 0, combined.getWidth(), combined.getHeight());
            graphics.drawImage(noteImage, 8, 12, null);
            graphics.drawImage(dialogImage, noteImage.getWidth() + 16, 12, null);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(combined, "png", snapshots.resolve("standard-reminder.png").toFile());
        standard.close();
        var failed = new PlayfulPreferencesDialog(first.view().stage(), PlayfulSettings.DEFAULT,
                settings -> { throw new java.io.UncheckedIOException(new java.io.IOException("Read-only profile")); });
        var failedFields = (javafx.scene.layout.VBox) failed.getDialogPane().getContent();
        var failedToggle = (javafx.scene.control.CheckBox) failedFields.getChildren().get(0);
        failedToggle.fire();
        assertFalse(failedToggle.isSelected());
        assertFalse(((javafx.scene.control.Label) failedFields.getChildren().get(2)).getText().isEmpty());
    }

    private void verifyPlayfulAnimations() throws Exception {
        for (PlayfulMode mode : new PlayfulMode[]{PlayfulMode.EGG, PlayfulMode.BOMB, PlayfulMode.TOASTER}) {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            Platform.runLater(() -> {
                var controller = new StickyNoteController(Note.blank(120, 120), note -> { });
                try {
                    controller.view().editor().setText("Tea break\nTake a moment, then come back refreshed.");
                    controller.view().setPlayfulSettings(new PlayfulSettings(true, mode));
                    controller.show();
                    controller.view().editor().requestFocus();
                    controller.setReminder(System.currentTimeMillis() + 10_000);
                    var overlay = controller.view().playfulOverlay();
                    var idleImage = overlay.snapshot(null, null);
                    var pending = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
                    pending.setOnFinished(event -> {
                        try {
                            assertEquals(PlayfulReminderOverlay.State.PENDING, overlay.state());
                            controller.setReminder(null);
                            assertFalse(overlay.isAnimating());
                            Node focus = controller.view().stage().getScene().getFocusOwner();
                            assertSame(controller.view().editor(), focus);
                            controller.view().reminderFired();
                            var appearance = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
                            appearance.setOnFinished(visible -> {
                                try {
                                    assertEquals(PlayfulReminderOverlay.State.FIRED, overlay.state());
                                    assertSame(focus, controller.view().stage().getScene().getFocusOwner());
                                    var bounds = overlay.getChildren().getFirst().getBoundsInParent();
                                    assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= 72);
                                    assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 48);
                                    var firedImage = overlay.snapshot(null, null);
                                    int changedPixels = 0;
                                    for (int row = 0; row < 48; row++) {
                                        for (int column = 0; column < 72; column++) {
                                            if (idleImage.getPixelReader().getArgb(column, row)
                                                    != firedImage.getPixelReader().getArgb(column, row)) changedPixels++;
                                        }
                                    }
                                    assertTrue(changedPixels > 30, "Fired artwork must visibly change");
                                    capture(controller, mode.name().toLowerCase(java.util.Locale.ROOT) + "-fired.png");
                                } catch (Throwable failure) {
                                    completed.completeExceptionally(failure);
                                    controller.dispose();
                                }
                            });
                            appearance.play();
                            var finished = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3.3));
                            finished.setOnFinished(done -> {
                                try {
                                    assertEquals(PlayfulReminderOverlay.State.NONE, overlay.state());
                                    assertFalse(overlay.isAnimating());
                                    assertFalse(overlay.isVisible());
                                    controller.setReminder(System.currentTimeMillis() + 3_600_000);
                                    controller.view().stage().hide();
                                    assertFalse(overlay.isAnimating());
                                    controller.show();
                                    assertEquals(PlayfulReminderOverlay.State.SCHEDULED, overlay.state());
                                    controller.dispose();
                                    assertFalse(overlay.isAnimating());
                                    completed.complete(null);
                                } catch (Throwable failure) {
                                    completed.completeExceptionally(failure);
                                    controller.dispose();
                                }
                            });
                            finished.play();
                        } catch (Throwable failure) {
                            completed.completeExceptionally(failure);
                            controller.dispose();
                        }
                    });
                    pending.play();
                } catch (Throwable failure) {
                    completed.completeExceptionally(failure);
                    controller.dispose();
                }
            });
            completed.get(10, TimeUnit.SECONDS);
        }
    }

    private void verifyScheduledPlayfulDelivery() throws Exception {
        CompletableFuture<Void> delivered = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                var database = new DatabaseProvider(directory.resolve("delivery.db"));
                var repository = new NoteRepository(database);
                var store = new PlayfulSettingsStore(directory.resolve("delivery.properties"));
                store.save(new PlayfulSettings(true, PlayfulMode.EGG));
                Stage primary = new Stage();
                var manager = new NoteManager(repository, primary, store);
                var controller = manager.createNote();
                controller.view().editor().setText("Notesy visual integration test");
                assertEquals(PlayfulMode.EGG, controller.view().playfulSettings().activeMode());
                controller.setReminder(System.currentTimeMillis() + 500);
                var poll = new javafx.animation.Timeline();
                Runnable cleanup = () -> {
                    poll.stop();
                    manager.close();
                    primary.hide();
                    try {
                        database.close();
                    } catch (Exception failure) {
                        delivered.completeExceptionally(failure);
                    }
                };
                poll.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(100), event -> {
                    try {
                        if (controller.note().reminderTimestamp() != null) return;
                        assertNull(repository.findAll().getFirst().reminderTimestamp());
                        assertEquals(PlayfulReminderOverlay.State.FIRED, controller.view().playfulOverlay().state());
                        assertEquals("Notesy visual integration test", controller.view().editor().getText());
                        cleanup.run();
                        delivered.complete(null);
                    } catch (Throwable failure) {
                        delivered.completeExceptionally(failure);
                        cleanup.run();
                    }
                }));
                poll.setCycleCount(80);
                poll.setOnFinished(event -> {
                    cleanup.run();
                    delivered.completeExceptionally(new AssertionError("Desktop reminder was not delivered within eight seconds"));
                });
                poll.play();
            } catch (Throwable failure) {
                delivered.completeExceptionally(failure);
            }
        });
        delivered.get(12, TimeUnit.SECONDS);
    }

    private static void mouse(Node target, javafx.event.EventType<MouseEvent> type, double screenX, double screenY) {
        target.fireEvent(new MouseEvent(type, 10, 10, screenX, screenY, MouseButton.PRIMARY,
                1, false, false, false, false, true, false, false, false, false, false,
                new PickResult(target, 10, 10)));
    }

    private static void capture(StickyNoteController controller, String name) throws Exception {
        capture(controller.view().stage().getScene().getRoot(), name);
    }

    private static void capture(javafx.scene.Parent root, String name) throws Exception {
        root.applyCss();
        root.layout();
        var image = root.snapshot(null, null);
        BufferedImage pixels = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var colors = new java.util.HashSet<Integer>();
        for (int row = 0; row < pixels.getHeight(); row++) {
            for (int column = 0; column < pixels.getWidth(); column++) {
                pixels.setRGB(column, row, image.getPixelReader().getArgb(column, row));
                colors.add(pixels.getRGB(column, row));
            }
        }
        Path destination = Path.of("target", "ui-snapshots", name);
        assertTrue(colors.size() > 8, "Snapshot must contain rendered content: " + name);
        Files.createDirectories(destination.getParent());
        ImageIO.write(pixels, "png", destination.toFile());
    }
}