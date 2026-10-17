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
    private static final java.util.Queue<Throwable> uiFailures = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @org.junit.jupiter.api.BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> uiFailures.add(failure));
            ready.countDown();
        });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        toolkitStarted = true;
    }

    @org.junit.jupiter.api.AfterAll
    static void stopToolkit() { if (toolkitStarted) Platform.exit(); }

    @org.junit.jupiter.api.AfterEach
    void rejectUnhandledUiFailures() {
        Throwable first = uiFailures.poll();
        if (first != null) {
            uiFailures.clear();
            throw new AssertionError("Unhandled JavaFX failure", first);
        }
    }

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
                    Node drag = first.view().dragBar().lookup("#note-drag-region");
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
    void startupLoadsEveryNoteIntoOneBrowserWithoutChangingSavedNotes() throws Exception {
        withManager(fixture -> {
            var first = fixture.repository().create(Note.blank(100, 110).withContent("First saved note"));
            var blank = fixture.repository().create(Note.blank(128, 138));
            var last = fixture.repository().create(Note.blank(400, 250).withContent("Another saved note"));
            var saved = fixture.repository().findAll();
            int windows = javafx.stage.Window.getWindows().size();

            fixture.manager().restore();

            var browser = fixture.manager().controller(first.id());
            assertNotNull(browser);
            assertEquals(StickyNoteController.Mode.BROWSER, browser.mode());
            assertTrue(browser.view().stage().isShowing());
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
            assertFalse(fixture.primary().isShowing());
            assertEquals(3, browser.view().sidebar().list().getItems().size());
            assertNull(fixture.manager().controller(blank.id()));
            assertNull(fixture.manager().controller(last.id()));

            browser.view().sidebar().list().getSelectionModel().select(
                    browser.view().sidebar().list().getItems().stream()
                            .filter(session -> session.note().id() == last.id()).findFirst().orElseThrow());
            assertEquals(last.id(), browser.note().id());
            assertEquals(last.content(), browser.view().editor().getText());
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
            fixture.manager().showManager();
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
            fixture.manager().flush();
            assertEquals(saved, fixture.repository().findAll());

            fixture.manager().show(first.id());
            var sticky = fixture.manager().controller(first.id());
            assertEquals(StickyNoteController.Mode.QUICK, sticky.mode());
            assertTrue(sticky.view().stage().isShowing());
            assertEquals(windows + 2, javafx.stage.Window.getWindows().size());
            assertEquals(StickyNoteController.Mode.BROWSER, browser.mode());
        });
    }

    @Test
    void startupBrowserShowsAllStickiesOnlyOnExplicitRequest() throws Exception {
        withManager(fixture -> {
            var first = fixture.repository().create(Note.blank(100, 100));
            var second = fixture.repository().create(Note.blank(128, 128).withContent("Saved note"));
            int windows = javafx.stage.Window.getWindows().size();
            fixture.manager().restore();
            var browser = fixture.manager().controller(first.id());

            browser.view().showAllItem().fire();

            assertEquals(StickyNoteController.Mode.BROWSER, browser.mode());
            assertEquals(windows + 3, javafx.stage.Window.getWindows().size());
            assertEquals(StickyNoteController.Mode.QUICK, fixture.manager().controller(second.id()).mode());
            browser.view().showAllItem().fire();
            assertEquals(windows + 3, javafx.stage.Window.getWindows().size());
            assertEquals(2, fixture.repository().findAll().size());
        });
    }

    @Test
    void emptyProfileStartsInBrowserWithoutCreatingBlankNotes() throws Exception {
        withManager(fixture -> {
            int windows = javafx.stage.Window.getWindows().size();
            fixture.manager().restore();
            assertTrue(fixture.repository().findAll().isEmpty());
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
            var window = javafx.stage.Window.getWindows().getLast();
            var newNote = (Button) window.getScene().lookup("#workspace-new-note");
            assertNotNull(newNote);
            assertTrue(newNote.isVisible());

            newNote.fire();

            var saved = fixture.repository().findAll();
            assertEquals(1, saved.size());
            var browser = fixture.manager().controller(saved.getFirst().id());
            assertEquals(StickyNoteController.Mode.BROWSER, browser.mode());
            assertSame(window, browser.view().stage());
            assertFalse(browser.view().editor().isDisabled());
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
        });
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
                assertTrue(note.view().dragBar().getChildren().contains(note.view().reminderButton()));
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
    void sharedSessionSynchronizesEditorsAndSurvivesClosingOneHost() throws Exception {
        withManager(fixture -> {
            var writes = new java.util.concurrent.atomic.AtomicInteger();
            var saved = new java.util.concurrent.atomic.AtomicReference<Note>();
            var session = new NoteSession(Note.blank(100, 100), note -> {
                writes.incrementAndGet();
                saved.set(note);
            });
            var first = new StickyNoteController(session);
            var second = new StickyNoteController(session);
            try {
                first.view().editor().setText("First edit");
                assertEquals("First edit", second.view().editor().getText());
                second.view().editor().appendText(" then newer edit");
                assertEquals("First edit then newer edit", first.view().editor().getText());
                first.flush();
                assertEquals(1, writes.get());
                assertEquals("First edit then newer edit", saved.get().content());
                first.dispose();
                second.view().editor().setText("Still editable after the other host closes");
                second.setReminder(System.currentTimeMillis() + 3_600_000);
                assertEquals(2, writes.get());
                assertEquals(second.view().editor().getText(), saved.get().content());
                assertNotNull(saved.get().reminderTimestamp());
            } finally {
                first.dispose();
                second.dispose();
                session.close();
            }
        });
    }

    @Test
    void closeSavesWithoutOpeningBrowserAndExplicitNavigationRecoversNotes() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            var browserShows = new java.util.concurrent.atomic.AtomicInteger();
            fixture.primary().setOnShowing(event -> browserShows.incrementAndGet());
            first.view().editor().setText("Saved immediately before X");
            first.view().closeButton().fire();
            assertFalse(first.view().stage().isShowing());
            assertTrue(second.view().stage().isShowing());
            assertFalse(fixture.primary().isShowing());
            assertEquals("Saved immediately before X", fixture.repository().findAll().getFirst().content());
            second.view().editor().setText("Saved by window-manager close");
            second.view().stage().fireEvent(new javafx.stage.WindowEvent(second.view().stage(),
                    javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
            assertFalse(second.view().stage().isShowing());
            assertFalse(fixture.primary().isShowing());
            assertEquals(0, browserShows.get(), "Closing the last visible note must not activate the browser");
            assertEquals("Saved by window-manager close", fixture.repository().findAll().getLast().content());

            fixture.manager().show(first.note().id());
            first.view().notesButton().fire();
            assertTrue(first.view().stage().isShowing(), "Browsing must not close the current note");
            assertEquals(StickyNoteController.Mode.BROWSER, first.mode());
            assertFalse(fixture.primary().isShowing());
            var options = first.view().optionsMenu();
            var showAll = options.getItems().stream()
                    .filter(item -> "show-all-sticky-notes".equals(item.getId())).findFirst().orElseThrow();
            assertEquals("Show all sticky notes", showAll.getText());
            showAll.fire();
            assertTrue(second.view().stage().isShowing());
            first.view().stage().fireEvent(new javafx.stage.WindowEvent(first.view().stage(),
                    javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
            assertFalse(first.view().stage().isShowing());
            first.view().managerItem().fire();
            fixture.manager().showManager();
            assertTrue(first.view().stage().isShowing(), "Legacy activation must reach integrated browsing");
        });
    }

    @Test
    void minimizeSavesOnlyCurrentWindowAndPausesPlayfulVisuals() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            fixture.manager().setNotePlayfulSettings(first.note().id(), new PlayfulSettings(true, PlayfulMode.EGG));
            long due = System.currentTimeMillis() + 3_600_000;
            first.setReminder(due);
            first.view().editor().setText("Saved immediately before minimize");
            first.view().minimizeButton().fire();
            awaitIconified(first.view().stage(), true);
            assertTrue(first.view().stage().isIconified());
            assertTrue(first.view().stage().isShowing());
            assertTrue(second.view().stage().isShowing());
            assertFalse(second.view().stage().isIconified());
            assertFalse(fixture.primary().isShowing());
            assertEquals("Saved immediately before minimize", fixture.repository().findAll().getFirst().content());
            assertEquals(due, fixture.repository().findAll().getFirst().reminderTimestamp());
            assertFalse(first.view().playfulOverlay().isAnimating());

            fixture.manager().reminderDelivered(first.note().id(), due);
            assertTrue(first.view().stage().isIconified(), "Delivery must not restore the note");
            assertFalse(first.view().playfulOverlay().isAnimating());
            first.show();
            awaitIconified(first.view().stage(), false);
            assertFalse(first.view().stage().isIconified());
            assertEquals(PlayfulReminderOverlay.State.FIRED, first.view().playfulOverlay().state());
            assertFalse(first.view().playfulOverlay().isAnimating(), "Restoring must not replay delivery");
            assertNull(fixture.repository().findAll().getFirst().reminderTimestamp());
        });
    }

    @Test
    void failedCloseAndMinimizeKeepTextVisibleAndSuccessfulCloseSavesOnce() throws Exception {
        withManager(fixture -> {
            var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            var controller = new StickyNoteController(Note.blank(100, 100), note -> {
                attempts.incrementAndGet();
                if (fail.get()) throw new IllegalStateException("Read-only test storage");
            });
            try {
                controller.show();
                controller.view().editor().setText("Keep this unsaved text");
                var close = new javafx.stage.WindowEvent(controller.view().stage(),
                        javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST);
                assertThrows(IllegalStateException.class,
                        () -> controller.view().stage().getOnCloseRequest().handle(close));
                assertTrue(close.isConsumed());
                assertThrows(IllegalStateException.class,
                        () -> controller.view().minimizeButton().getOnAction().handle(new javafx.event.ActionEvent()));
                assertTrue(controller.view().stage().isShowing());
                assertFalse(controller.view().stage().isIconified());
                assertEquals("Keep this unsaved text", controller.view().editor().getText());
                assertEquals("Save failed",
                        ((javafx.scene.control.Label) controller.view().stage().getScene()
                                .getRoot().lookup(".note-status")).getText());
                assertFalse(fixture.primary().isShowing());
                fail.set(false);
                controller.view().closeButton().fire();
                assertFalse(controller.view().stage().isShowing());
                assertEquals(3, attempts.get(), "Successful close must not save again after hiding");
            } finally {
                controller.dispose();
            }
        });
    }

    @Test
    void failedQuitRecoversHiddenUnsavedNoteAndDoesNotExit() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            note.view().editor().setText("A failed Quit must retain this text");
            note.view().stage().hide();
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("notes.db"));
                 var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TRIGGER fail_note_update BEFORE UPDATE ON notes
                        BEGIN SELECT RAISE(FAIL, 'Read-only test storage'); END
                        """);
                try {
                    assertThrows(IllegalStateException.class,
                            () -> note.view().quitItem().getOnAction().handle(new javafx.event.ActionEvent()));
                    assertTrue(note.view().stage().isShowing());
                    assertFalse(note.view().stage().isIconified());
                    assertEquals("A failed Quit must retain this text", note.view().editor().getText());
                } finally {
                    statement.execute("DROP TRIGGER fail_note_update");
                }
            }
            note.view().closeButton().fire();
            assertEquals("A failed Quit must retain this text", fixture.repository().findAll().getFirst().content());
        });
    }

    @Test
    void compactHeaderFitsAt260PixelsWithAccessibleNavigationAndWindowControls() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            var view = note.view();
            assertEquals(260, view.stage().getMinWidth());
            view.stage().setWidth(260);
            view.stage().setHeight(220);
            var root = view.stage().getScene().getRoot();
            var controls = java.util.List.of(view.addButton(), view.notesButton(), view.pinButton(),
                    view.reminderButton(), view.optionsMenu(), view.expandButton(),
                    view.minimizeButton(), view.closeButton());
            for (var theme : org.stickynotefx.theme.ThemeRegistry.Theme.values()) {
                fixture.manager().setTheme(theme);
                root.resize(260, 220);
                root.applyCss();
                root.layout();
                assertEquals(260, root.getLayoutBounds().getWidth());
                double previousRight = 0;
                for (var control : controls) {
                    assertFalse(control.getAccessibleText().isBlank());
                    assertNotNull(control.getTooltip());
                    assertFalse(control.getTooltip().getText().isBlank());
                    assertTrue(control.isVisible());
                    assertTrue(control.getWidth() >= 24, "Controls must retain 24-pixel hit targets");
                    assertTrue(control.getHeight() >= 24);
                    var bounds = view.dragBar().sceneToLocal(control.localToScene(control.getLayoutBounds()));
                    assertTrue(bounds.getMinX() >= previousRight, "Controls must not overlap");
                    assertTrue(bounds.getMaxX() <= view.dragBar().getWidth(), "Controls must fit the header");
                    previousRight = bounds.getMaxX();
                    if (control.getGraphic() != null) {
                        var graphic = control.getGraphic();
                        var graphicBounds = control.sceneToLocal(graphic.localToScene(graphic.getBoundsInLocal()));
                        assertTrue(graphicBounds.getWidth() > 0);
                        assertTrue(graphicBounds.getMinX() >= 0 && graphicBounds.getMaxX() <= control.getWidth(),
                                "Control graphics must not be clipped");
                        assertTrue(graphicBounds.getMinY() >= 0 && graphicBounds.getMaxY() <= control.getHeight(),
                                "Control graphics must fit vertically");
                    }
                }
                Node drag = view.dragBar().lookup("#note-drag-region");
                assertTrue(drag.getLayoutBounds().getWidth() >= 36, "Keep a usable drag target at minimum width");
                capture(note, "phase-one-minimum-" + theme.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            }
            var header = view.dragBar().getChildren();
            assertEquals(header.indexOf(view.minimizeButton()) + 1, header.indexOf(view.closeButton()));
            assertFalse(view.expandButton().isDisabled());
            assertEquals("Expand to Notebook", view.expandButton().getTooltip().getText());
            assertFalse(view.notesButton().isDisabled());
        });
    }

    @Test
    void browserSelectsAndCreatesInPlaceWithSharedEditsAndCompactGeometry() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            first.view().editor().setText("First note before browsing");
            second.view().editor().setText("Older note to edit");
            Note compact = first.note();
            int windows = javafx.stage.Window.getWindows().size();
            first.view().notesButton().fire();
            assertEquals(StickyNoteController.Mode.BROWSER, first.mode());
            assertEquals(windows, javafx.stage.Window.getWindows().size());
            assertEquals(compact, first.note(), "Browser geometry must not replace compact bounds");
            assertEquals("First note before browsing", first.view().editor().getText());
            first.view().sidebar().list().getSelectionModel().select(second.session());
            assertSame(second.session(), first.session());
            first.view().editor().setText("Updated without another popup");
            assertEquals("Updated without another popup", second.view().editor().getText());
            assertEquals(windows, javafx.stage.Window.getWindows().size());
            first.flush();
            assertEquals("Updated without another popup", fixture.repository().findAll().getLast().content());
            first.view().sidebar().newNoteButton().fire();
            assertEquals(3, fixture.repository().findAll().size());
            assertEquals(windows, javafx.stage.Window.getWindows().size());
            assertEquals("", first.view().editor().getText());
            assertEquals(StickyNoteController.Mode.BROWSER, first.mode());
            Note newCompact = first.note();
            first.view().notesButton().fire();
            assertEquals(StickyNoteController.Mode.QUICK, first.mode());
            assertEquals(newCompact.width(), first.view().stage().getWidth(), 1);
            assertEquals(newCompact.height(), first.view().stage().getHeight(), 1);
        });
    }

    @Test
    void browserDeletionKeepsEmptyWorkspaceAndActivationDoesNotCreateNotes() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            note.browse();
            fixture.manager().delete(note.note().id());
            assertTrue(note.view().stage().isShowing());
            assertNull(note.note());
            assertTrue(note.view().editor().isDisabled());
            assertTrue(note.view().sidebar().list().getItems().isEmpty());
            int windows = javafx.stage.Window.getWindows().size();
            fixture.manager().showManager();
            assertTrue(fixture.repository().findAll().isEmpty());
            assertEquals(windows, javafx.stage.Window.getWindows().size());
            note.view().sidebar().newNoteButton().fire();
            assertEquals(1, fixture.repository().findAll().size());
            assertFalse(note.view().editor().isDisabled());
            assertEquals(windows, javafx.stage.Window.getWindows().size());
        });
    }

    @Test
    void failedBrowserSelectionRetainsOutgoingSessionAndText() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            first.browse();
            first.view().editor().setText("Must not leave this editor on save failure");
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("notes.db"));
                 var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TRIGGER fail_note_update BEFORE UPDATE ON notes
                        BEGIN SELECT RAISE(FAIL, 'Read-only test storage'); END
                        """);
                try {
                    assertThrows(IllegalStateException.class, () -> first.selectSession(second.session()));
                    assertNotSame(second.session(), first.session());
                    assertEquals("Must not leave this editor on save failure", first.view().editor().getText());
                    assertTrue(first.view().stage().isShowing());
                } finally {
                    statement.execute("DROP TRIGGER fail_note_update");
                }
            }
        });
    }

    @Test
    void notebookExpandsAndRestoresWithoutChangingCompactBoundsSelectionOrPin() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            note.view().editor().setText("A notebook keeps the same selected note");
            note.view().editor().selectRange(2, 12);
            note.view().pinButton().fire();
            Note compact = note.note();
            var editor = note.view().editor();
            var screen = javafx.stage.Screen.getScreensForRectangle(
                    note.view().stage().getX(), note.view().stage().getY(), 1, 1).getFirst().getVisualBounds();
            note.view().expandButton().fire();
            awaitWindowState(() -> Math.abs(screen.getWidth() - note.view().stage().getWidth()) <= 2
                            && Math.abs(screen.getHeight() - note.view().stage().getHeight()) <= 2,
                    "Notebook did not fill the available desktop");
            assertEquals(StickyNoteController.Mode.NOTEBOOK, note.mode());
            assertSame(editor, note.view().editor());
            assertTrue(note.view().sidebar().searchField().getParent().isVisible());
            assertFalse(note.view().stage().isFullScreen());
            assertEquals(screen.getWidth(), note.view().stage().getWidth(), 2);
            assertEquals(screen.getHeight(), note.view().stage().getHeight(), 2);
            assertEquals(compact, note.note());
            assertEquals(2, editor.getSelection().getStart());
            assertEquals(12, editor.getSelection().getEnd());
            assertFalse(note.view().stage().isAlwaysOnTop());
            assertTrue(note.view().pinButton().isSelected());
            assertTrue(note.view().pinButton().isDisabled());
            capture(note, "notebook-workspace.png");
            for (Node node : note.view().sidebar().list().lookupAll(".scroll-bar")) {
                if (node instanceof javafx.scene.control.ScrollBar bar
                        && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL)
                    assertFalse(bar.isVisible(), "Note previews must fit the vertically scrollable collection");
            }
            note.view().expandButton().fire();
            awaitWindowState(() -> Math.abs(compact.width() - note.view().stage().getWidth()) <= 1
                            && Math.abs(compact.height() - note.view().stage().getHeight()) <= 1,
                    "Restore did not recover compact dimensions");
            assertEquals(StickyNoteController.Mode.QUICK, note.mode());
            assertEquals(compact, note.note());
            assertEquals(compact.width(), note.view().stage().getWidth(), 1);
            assertEquals(compact.height(), note.view().stage().getHeight(), 1);
            assertTrue(note.view().stage().isAlwaysOnTop());
            assertFalse(note.view().pinButton().isDisabled());
            note.browse();
            awaitWindowState(() -> note.view().stage().getScene().getWidth() >= 560
                            && Math.abs(note.view().stage().getWidth() - note.view().stage().getScene().getWidth()) <= 1,
                    "Browser scene did not reach its expanded dimensions");
            double browserWidth = note.view().stage().getWidth();
            double browserHeight = note.view().stage().getHeight();
            capture(note, "integrated-browser.png");
            note.expand();
            note.restore();
            awaitWindowState(() -> Math.abs(browserWidth - note.view().stage().getWidth()) <= 1
                            && Math.abs(browserHeight - note.view().stage().getHeight()) <= 1,
                    "Restore did not recover Browser dimensions");
            assertEquals(StickyNoteController.Mode.BROWSER, note.mode());
            assertEquals(browserWidth, note.view().stage().getWidth(), 1);
            assertEquals(browserHeight, note.view().stage().getHeight(), 1);
            assertFalse(note.view().sidebar().searchField().getParent().isManaged());
            assertEquals(compact, note.note());
        });
    }

    @Test
    void notebookSearchFiltersContentWithoutNavigatingAndNewNoteStaysInWorkspace() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            first.view().editor().setText("Alpha\nFind a kiwi in the body");
            second.view().editor().setText("Beta notebook");
            first.expand();
            int windows = javafx.stage.Window.getWindows().size();
            var sidebar = first.view().sidebar();
            sidebar.searchField().setText("KiWi");
            assertEquals(java.util.List.of(first.session()), sidebar.list().getItems());
            sidebar.searchField().setText("bEtA");
            assertEquals(java.util.List.of(second.session()), sidebar.list().getItems());
            assertEquals("Alpha\nFind a kiwi in the body", first.view().editor().getText());
            sidebar.list().getSelectionModel().select(second.session());
            assertSame(second.session(), first.session());
            first.view().editor().setText("No longer matches the search");
            assertTrue(sidebar.list().getItems().isEmpty());
            assertSame(second.session(), first.session(), "Filtering must not replace the editor");
            assertEquals("No longer matches the search", second.view().editor().getText());
            sidebar.searchField().clear();
            assertEquals(2, sidebar.list().getItems().size());
            assertSame(second.session(), sidebar.list().getSelectionModel().getSelectedItem());
            sidebar.searchField().setText("no results anywhere");
            assertTrue(sidebar.list().getItems().isEmpty());
            sidebar.newNoteButton().fire();
            assertEquals("", sidebar.searchField().getText());
            assertEquals(3, sidebar.list().getItems().size());
            assertEquals("", first.view().editor().getText());
            assertEquals(StickyNoteController.Mode.NOTEBOOK, first.mode());
            assertEquals(windows, javafx.stage.Window.getWindows().size());
            assertEquals(3, fixture.repository().findAll().size());
            fixture.manager().showManager();
            assertEquals(StickyNoteController.Mode.NOTEBOOK, first.mode(),
                    "Activation must preserve an existing Notebook workspace");
        });
    }

    @Test
    void failedNotebookTransitionsLeaveCurrentModeAndEditingStateVisible() throws Exception {
        withManager(fixture -> {
            var note = fixture.manager().createNote();
            note.view().editor().setText("Save this before changing workspace");
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("notes.db"));
                 var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TRIGGER fail_note_update BEFORE UPDATE ON notes
                        BEGIN SELECT RAISE(FAIL, 'Read-only test storage'); END
                        """);
                try {
                    assertThrows(IllegalStateException.class, note::expand);
                    assertEquals(StickyNoteController.Mode.QUICK, note.mode());
                    assertTrue(note.view().stage().isShowing());
                } finally {
                    statement.execute("DROP TRIGGER fail_note_update");
                }
                note.expand();
                note.view().editor().setText("Also save before Restore");
                statement.execute("""
                        CREATE TRIGGER fail_note_update BEFORE UPDATE ON notes
                        BEGIN SELECT RAISE(FAIL, 'Read-only test storage'); END
                        """);
                try {
                    assertThrows(IllegalStateException.class, note::restore);
                    assertEquals(StickyNoteController.Mode.NOTEBOOK, note.mode());
                    assertEquals("Also save before Restore", note.view().editor().getText());
                    assertTrue(note.view().stage().isShowing());
                } finally {
                    statement.execute("DROP TRIGGER fail_note_update");
                }
            }
        });
    }

    @Test
    void showAllCreatesCompactHostsWithoutConvertingNotebookOrDuplicatingWriters() throws Exception {
        withManager(fixture -> {
            var notebook = fixture.manager().createNote();
            var other = fixture.manager().createNote();
            long id = notebook.note().id();
            notebook.expand();
            int windows = javafx.stage.Window.getWindows().size();
            fixture.manager().showAll();
            assertEquals(StickyNoteController.Mode.NOTEBOOK, notebook.mode());
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
            other.view().closeButton().fire();
            fixture.manager().showAll();
            assertEquals(windows + 1, javafx.stage.Window.getWindows().size());
            assertTrue(other.view().stage().isShowing());
            notebook.view().editor().setText("Shared between compact and Notebook");
            notebook.flush();
            assertEquals("Shared between compact and Notebook",
                    fixture.repository().findAll().stream().filter(note -> note.id() == id).findFirst().orElseThrow().content());
        });
    }

    @Test
    void notebookRetainsThemesRemindersAndQuietWindowActions() throws Exception {
        withManager(fixture -> {
            var first = fixture.manager().createNote();
            var second = fixture.manager().createNote();
            first.view().editor().setText("Preserve this content in every presentation");
            first.expand();
            for (var theme : org.stickynotefx.theme.ThemeRegistry.Theme.values()) {
                fixture.manager().setTheme(theme);
                assertEquals(theme, org.stickynotefx.theme.ThemeRegistry.current(
                        first.view().stage().getScene().getRoot()));
                for (var mode : new PlayfulMode[]{PlayfulMode.EGG, PlayfulMode.BOMB, PlayfulMode.TOASTER}) {
                    fixture.manager().setNotePlayfulSettings(first.note().id(), new PlayfulSettings(true, mode));
                    long timestamp = System.currentTimeMillis() + 3_600_000;
                    first.setReminder(timestamp);
                    assertEquals(timestamp, first.note().reminderTimestamp());
                    fixture.manager().reminderDelivered(first.note().id(), timestamp);
                    assertTrue(first.view().playfulExpired());
                    assertNull(first.note().reminderTimestamp());
                    assertEquals(PlayfulReminderOverlay.State.FIRED, first.view().playfulOverlay().state());
                }
            }
            first.view().minimizeButton().fire();
            awaitIconified(first.view().stage(), true);
            assertFalse(first.view().playfulOverlay().isAnimating());
            assertFalse(second.view().stage().isIconified());
            first.show();
            awaitIconified(first.view().stage(), false);
            assertEquals(StickyNoteController.Mode.NOTEBOOK, first.mode());
            first.view().closeButton().fire();
            assertFalse(first.view().stage().isShowing());
            assertTrue(second.view().stage().isShowing());
            assertFalse(fixture.primary().isShowing());
            assertEquals("Preserve this content in every presentation",
                    fixture.repository().findAll().getFirst().content());
            fixture.manager().showManager();
            assertTrue(first.view().stage().isShowing());
            assertEquals(StickyNoteController.Mode.NOTEBOOK, first.mode());
        });
    }

    @Test
    void closingEmptyCompatibilityBrowserDoesNotQuit() throws Exception {
        withManager(fixture -> {
            Stage stage = new Stage();
            var quitCalls = new java.util.concurrent.atomic.AtomicInteger();
            try {
                var browser = new MainWindow(stage, () -> { }, () -> { }, id -> { }, quitCalls::incrementAndGet);
                browser.show();
                stage.fireEvent(new javafx.stage.WindowEvent(stage,
                        javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertFalse(stage.isShowing());
                assertEquals(0, quitCalls.get());
                browser.show();
                assertTrue(stage.isShowing(), "Explicit activation must recover an empty browser");
            } finally {
                stage.hide();
            }
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
            mouse(note.view().dragBar().lookup("#note-drag-region"), MouseEvent.MOUSE_RELEASED, 0, 0);
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

    private record Fixture(NoteManager manager, NoteRepository repository, Stage primary) { }
    @FunctionalInterface private interface FxCheck { void run(Fixture fixture) throws Exception; }

    private static void awaitIconified(Stage stage, boolean expected) {
        awaitWindowState(() -> stage.isIconified() == expected,
                "Window manager did not reach iconified=" + expected);
    }

    private static void awaitWindowState(java.util.function.BooleanSupplier ready, String message) {
        Object key = new Object();
        var check = new javafx.animation.Timeline();
        var timeout = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
        check.getKeyFrames().add(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(50), event -> {
                    if (ready.getAsBoolean()) {
                        check.stop();
                        timeout.stop();
                        Platform.exitNestedEventLoop(key, true);
                    }
                }));
        check.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeout.setOnFinished(event -> {
            check.stop();
            Platform.exitNestedEventLoop(key, false);
        });
        check.play();
        timeout.play();
        try {
            assertEquals(Boolean.TRUE, Platform.enterNestedEventLoop(key), message);
        } finally {
            check.stop();
            timeout.stop();
        }
    }

    private void withManager(FxCheck check) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            try (var database = new DatabaseProvider(directory.resolve("notes.db"))) {
                var repository = new NoteRepository(database);
                Stage primary = new Stage();
                var manager = new NoteManager(repository, primary,
                        new PlayfulSettingsStore(directory.resolve("preferences.properties")));
                try {
                    check.run(new Fixture(manager, repository, primary));
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