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
    private static boolean toolkitStarted;

    @org.junit.jupiter.api.BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        toolkitStarted = true;
    }

    @org.junit.jupiter.api.AfterAll
    static void stopToolkit() { if (toolkitStarted) Platform.exit(); }

    @Test
    void editsBoundsColorsRemindersAndDeletionSurviveLifecycle() throws Exception {
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
                    MenuButton options = first.view().optionsMenu();
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
        Platform.runLater(exercise);
        exercise.get(30, TimeUnit.SECONDS);
        verifyDebouncedAutosave();
        verifyPlayfulAnimations();
        verifyScheduledPlayfulDelivery();
    }

    @Test
    void lightThemePreservesNoteColorAndDefaultOptOut() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.LIGHT,
                    org.stickynotefx.theme.ThemeRegistry.current(note.view().stage().getScene().getRoot()));
            assertNull(note.view().playfulOverlay());
            var pin = note.view().pinButton();
            assertTrue(note.view().dragBar().getChildren().contains(pin));
                assertEquals(javafx.stage.StageStyle.UNDECORATED, note.view().stage().getStyle());
                assertEquals(note.view().reminderButton(), note.view().dragBar().getChildren().get(3));
                assertEquals(pin, note.view().dragBar().getChildren().get(2));
                assertEquals(1, note.view().dragBar().getChildren().stream()
                    .filter(node -> node.getId() != null && node.getId().equals("close-note")).count());
            assertFalse(pin.isSelected());
            pin.fire();
            assertTrue(note.view().stage().isAlwaysOnTop());
            assertEquals("Unpin note", pin.getTooltip().getText());
            pin.fire();
            assertFalse(note.view().stage().isAlwaysOnTop());
            note.view().stage().setAlwaysOnTop(true);
            assertTrue(pin.isSelected());
            note.view().stage().setAlwaysOnTop(false);
            assertFalse(pin.isSelected());
            note.view().closeButton().fire();
            assertFalse(note.view().stage().isShowing());
            assertFalse(fixture.manager().controller(note.note().id()).view().stage().isShowing());
                assertEquals(java.util.List.of(32.0, 64.0, 128.0, 256.0),
                    note.view().stage().getIcons().stream().map(javafx.scene.image.Image::getWidth).toList());
                assertTrue(note.view().stage().getIcons().stream().noneMatch(javafx.scene.image.Image::isError));
            note.view().stage().getScene().getRoot().applyCss();
            var paper = (javafx.scene.layout.Region) note.view().stage().getScene().lookup(".paper");
            assertEquals(javafx.scene.paint.Color.web(note.note().color()), paper.getBackground().getFills().getFirst().getFill());
            long timestamp = System.currentTimeMillis() + 60_000;
            note.setReminder(timestamp);
            fixture.manager().reminderDelivered(note.note().id(), timestamp);
            assertFalse(Files.exists(directory.resolve("visuals.properties")));
            assertNull(note.view().playfulOverlay());
        });
    }

    @Test
    void darkThemeStylesEditorReminderDialogAndPreferences() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            note.view().editor().setText("High contrast note");
            fixture.manager().setTheme(org.stickynotefx.theme.ThemeRegistry.Theme.DARK);
            note.view().stage().getScene().getRoot().applyCss();
            var paper = (javafx.scene.layout.Region) note.view().stage().getScene().lookup(".paper");
            assertEquals(javafx.scene.paint.Color.web("#202225"), paper.getBackground().getFills().getFirst().getFill());
            var text = (javafx.scene.text.Text) note.view().editor().lookup(".text");
            assertEquals(javafx.scene.paint.Color.WHITE, text.getFill());
            var reminder = new ReminderDialog(note.view().stage(), null);
            assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.DARK,
                    org.stickynotefx.theme.ThemeRegistry.current(reminder.getDialogPane()));
            reminder.show();
            capture(reminder.getDialogPane(), "dark-reminder.png");
            reminder.close();
            var preferences = new PlayfulPreferencesDialog(note.view().stage(), PlayfulSettings.DEFAULT, settings -> { });
            preferences.addThemeSelector(org.stickynotefx.theme.ThemeRegistry.Theme.DARK, fixture.manager()::setTheme);
            preferences.addPlacementToggle(false, fixture.manager()::setSmartPlacement);
            preferences.show();
            assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.DARK,
                    org.stickynotefx.theme.ThemeRegistry.current(preferences.getDialogPane()));
            capture(preferences.getDialogPane(), "theme-preferences.png");
                assertEquals(javafx.stage.StageStyle.UNDECORATED,
                    ((Stage) preferences.getDialogPane().getScene().getWindow()).getStyle());
                assertEquals(1, preferences.getDialogPane().lookupAll("#close-preferences").size());
                ((Button) preferences.getDialogPane().lookup("#close-preferences")).fire();
                assertFalse(preferences.isShowing());
                assertTrue(note.view().stage().isShowing());
            fixture.manager().setNotePlayfulSettings(note.note().id(), new PlayfulSettings(true, PlayfulMode.BOMB));
            note.view().reminderFired();
            note.view().playfulOverlay().showExpired();
            capture(note, "theme-dark.png");
        });
    }

    @Test
    void paperThemeUsesFlatSurfaceAndKeepsArtworkUnclipped() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            fixture.manager().setTheme(org.stickynotefx.theme.ThemeRegistry.Theme.PAPER);
            note.view().editor().setText("Paper, without a new storage format");
            fixture.manager().setNotePlayfulSettings(note.note().id(), new PlayfulSettings(true, PlayfulMode.TOASTER));
            note.view().reminderFired();
            note.view().playfulOverlay().showExpired();
            note.view().stage().getScene().getRoot().applyCss();
            var paper = (javafx.scene.layout.Region) note.view().stage().getScene().lookup(".paper");
            assertEquals(javafx.geometry.Insets.EMPTY, ((javafx.scene.layout.StackPane) note.view().stage().getScene().getRoot()).getPadding());
            assertNull(paper.getEffect());
            assertTrue(paper.getBackground().getImages().isEmpty());
            assertEquals(javafx.scene.paint.Color.web("#e8eee3"), paper.getBackground().getFills().getFirst().getFill());
            assertTrue(note.view().playfulOverlay().isVisible());
            capture(note, "theme-paper.png");
        });
    }

    @Test
    void newWindowsInheritThemeAndThemeChangesFlushPendingText() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            first.view().editor().setText("Saved along with theme change");
            fixture.manager().setTheme(org.stickynotefx.theme.ThemeRegistry.Theme.DARK);
            var second = fixture.manager().createNote();
            fixture.manager().flush();
            assertEquals("Saved along with theme change", fixture.repository().findAll().getFirst().content());
            assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.DARK,
                    org.stickynotefx.theme.ThemeRegistry.current(second.view().stage().getScene().getRoot()));
            assertEquals(org.stickynotefx.theme.ThemeRegistry.Theme.DARK,
                    new org.stickynotefx.ui.preferences.ProfilePreferences(directory.resolve("visuals.properties")).theme());
        });
    }

    @Test
    void perNoteModesRemainIndependentOfGlobalDefaults() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            fixture.manager().setNotePlayfulSettings(first.note().id(), new PlayfulSettings(true, PlayfulMode.EGG));
            fixture.manager().setNotePlayfulSettings(second.note().id(), new PlayfulSettings(true, PlayfulMode.TOASTER));
            fixture.manager().setPlayfulSettings(new PlayfulSettings(true, PlayfulMode.BOMB));
            assertEquals(PlayfulMode.EGG, first.view().playfulSettings().activeMode());
            assertEquals(PlayfulMode.TOASTER, second.view().playfulSettings().activeMode());
            assertEquals(PlayfulMode.BOMB, fixture.manager().createNote().view().playfulSettings().activeMode());
        });
    }

    @Test
    void expiredVisualSurvivesHideAndProfileRestart() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            fixture.manager().setNotePlayfulSettings(note.note().id(), new PlayfulSettings(true, PlayfulMode.EGG));
            long due = System.currentTimeMillis() + 60_000;
            note.setReminder(due);
            fixture.manager().reminderDelivered(note.note().id(), due);
            note.view().stage().hide();
            note.show();
            assertEquals(PlayfulReminderOverlay.State.FIRED, note.view().playfulOverlay().state());
            assertFalse(note.view().playfulOverlay().isAnimating());
            long id = note.note().id();
            fixture.manager().close();
            var restored = new NoteManager(fixture.repository(), new Stage(),
                    new PlayfulSettingsStore(directory.resolve("preferences.properties")));
            try {
                restored.restore();
                assertTrue(restored.controller(id).view().playfulExpired());
                assertEquals(PlayfulReminderOverlay.State.FIRED, restored.controller(id).view().playfulOverlay().state());
                assertNull(restored.controller(id).note().reminderTimestamp());
                assertFalse(restored.controller(id).view().playfulOverlay().isAnimating());
            } finally {
                restored.close();
            }
        });
    }

    @Test
    void snoozeAndClearResetOnlyTheSelectedNotesExpiredState() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            fixture.manager().setNotePlayfulSettings(note.note().id(), new PlayfulSettings(true, PlayfulMode.BOMB));
            long due = System.currentTimeMillis() + 60_000;
            note.setReminder(due);
            fixture.manager().reminderDelivered(note.note().id(), due);
            long before = System.currentTimeMillis();
            note.view().snoozeMenu().getItems().getFirst().fire();
            assertTrue(note.note().reminderTimestamp() >= before + 300_000);
            assertTrue(note.note().reminderTimestamp() <= System.currentTimeMillis() + 300_000);
            assertFalse(note.view().playfulExpired());
            assertEquals(PlayfulReminderOverlay.State.SCHEDULED, note.view().playfulOverlay().state());
            note.view().clearReminderItem().fire();
            assertNull(note.note().reminderTimestamp());
            assertFalse(note.view().playfulOverlay().isVisible());
            assertFalse(new org.stickynotefx.ui.preferences.ProfilePreferences(directory.resolve("visuals.properties"))
                    .note(note.note().id(), PlayfulSettings.DEFAULT).expired());
        });
    }

    @Test
    void quickPresetsAndZonePreviewDoNotChangeSchedulingZone() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            var dialog = new ReminderDialog(note.view().stage(), null);
            dialog.show();
            @SuppressWarnings("unchecked")
            var preset = (javafx.scene.control.ComboBox<Object>) dialog.getDialogPane().lookup("#reminder-preset");
            for (int minutes : new int[]{5, 10, 30, 120}) assertTrue(preset.getItems().contains(new ReminderDialog.QuickPreset(minutes)));
            assertTrue(preset.getItems().contains(org.stickynotefx.reminder.ReminderPreset.ONE_HOUR));
            preset.setValue(new ReminderDialog.QuickPreset(10));
            @SuppressWarnings("unchecked")
            var zone = (javafx.scene.control.ComboBox<String>) dialog.getDialogPane().lookup("#preview-zone");
            zone.setValue("Asia/Tokyo");
            assertFalse(((javafx.scene.control.Label) dialog.getDialogPane().lookup("#zone-preview")).getText().isBlank());
            long before = System.currentTimeMillis();
            var set = dialog.getDialogPane().getButtonTypes().stream().filter(button -> button.getText().equals("Set reminder")).findFirst().orElseThrow();
            ((Button) dialog.getDialogPane().lookupButton(set)).fire();
            assertTrue(dialog.getResult().timestamp() >= before + 600_000);
            assertTrue(dialog.getResult().timestamp() <= System.currentTimeMillis() + 600_000);
            assertNull(note.note().reminderTimestamp());
        });
    }

    @Test
    void initialPlacementAvoidsOverlapAndKeepsWindowsOnScreen() throws Exception {
        withManager(fixture -> {
            fixture.manager().setSmartPlacement(true);
            var firstNote = fixture.manager().createNote();
            var secondNote = fixture.manager().createNote();
            var desktop = javafx.stage.Screen.getPrimary().getVisualBounds();
            if (desktop.getWidth() >= 648 && desktop.getHeight() >= 300) {
            var firstBounds = new javafx.geometry.Rectangle2D(firstNote.note().x(), firstNote.note().y(),
                firstNote.note().width(), firstNote.note().height());
            assertFalse(firstBounds.intersects(secondNote.note().x(), secondNote.note().y(),
                secondNote.note().width(), secondNote.note().height()));
            }
            var screen = new javafx.geometry.Rectangle2D(-1200, 0, 1200, 800);
            var first = WindowPlacement.choose(screen, java.util.List.of());
            var second = WindowPlacement.choose(screen, java.util.List.of(first));
            assertFalse(first.intersects(second));
            assertTrue(screen.contains(first));
            assertTrue(screen.contains(second));
            var small = new javafx.geometry.Rectangle2D(0, 0, 200, 150);
            assertTrue(small.contains(WindowPlacement.choose(small, java.util.List.of())));
        });
    }

    @Test
    void nativeMovesResizeAndOptionalSnapUseExistingMetadata() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            assertFalse(note.view().snapToGridItem().isSelected());
            var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            note.view().stage().setX(bounds.getMinX() + 93);
            note.view().stage().setY(bounds.getMinY() + 107);
            note.view().stage().setWidth(390);
            note.flush();
            assertEquals(bounds.getMinX() + 93, fixture.repository().findAll().getFirst().x());
            assertEquals(390, fixture.repository().findAll().getFirst().width());
            note.view().snapToGridItem().setSelected(true);
            mouse(note.view().dragBar().getChildren().get(1), MouseEvent.MOUSE_RELEASED, 0, 0);
            note.flush();
            assertEquals(bounds.getMinX() + 100, fixture.repository().findAll().getFirst().x());
            assertEquals(bounds.getMinY() + 100, fixture.repository().findAll().getFirst().y());
        });
    }

    @Test
    void themeSwitchPreservesOverlayAndReminderDeadline() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            fixture.manager().setNotePlayfulSettings(note.note().id(), new PlayfulSettings(true, PlayfulMode.EGG));
            long timestamp = System.currentTimeMillis() + 3_600_000;
            note.setReminder(timestamp);
            var overlay = note.view().playfulOverlay();
            for (var theme : org.stickynotefx.theme.ThemeRegistry.Theme.values()) {
                fixture.manager().setTheme(theme);
                fixture.manager().flush();
                assertSame(overlay, note.view().playfulOverlay());
                assertEquals(timestamp, fixture.repository().findAll().getFirst().reminderTimestamp());
            }
        });
    }

    private record Fixture(NoteManager manager, NoteRepository repository) { }
    @FunctionalInterface private interface FxCheck { void run(Fixture fixture) throws Exception; }

    private void withManager(FxCheck check) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            try (var database = new DatabaseProvider(directory.resolve("notes.db"))) {
                var repository = new NoteRepository(database);
                Stage primary = new Stage();
                var manager = new NoteManager(repository, primary,
                        new PlayfulSettingsStore(directory.resolve("preferences.properties")));
                try {
                    check.run(new Fixture(manager, repository));
                } finally {
                    manager.close();
                    primary.hide();
                    for (var window : java.util.List.copyOf(javafx.stage.Window.getWindows())) window.hide();
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(20, TimeUnit.SECONDS);
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
            manager.setNotePlayfulSettings(first.note().id(), new PlayfulSettings(true, selected));
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
            manager.setNotePlayfulSettings(first.note().id(), PlayfulSettings.DEFAULT);
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
                                    assertEquals(PlayfulReminderOverlay.State.FIRED, overlay.state());
                                    assertFalse(overlay.isAnimating());
                                    assertTrue(overlay.isVisible());
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