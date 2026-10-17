package org.stickynotefx.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.stickynotefx.model.Note;
import org.stickynotefx.persistence.NoteRepository;
import org.stickynotefx.reminder.ReminderService;
import org.stickynotefx.ui.playful.PlayfulPreferencesDialog;
import org.stickynotefx.ui.playful.PlayfulSettings;
import org.stickynotefx.ui.playful.PlayfulSettingsStore;
import org.stickynotefx.ui.preferences.ProfilePreferences;
import org.stickynotefx.theme.ThemeRegistry;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class NoteManager implements AutoCloseable {
    private final NoteRepository repository;
    private final java.util.List<StickyNoteController> windows = new java.util.ArrayList<>();
    private final Map<Long, NoteSession> sessions = new LinkedHashMap<>();
    private final javafx.collections.ObservableList<NoteSession> collection =
            javafx.collections.FXCollections.observableArrayList(session ->
                    new javafx.beans.Observable[]{session.noteProperty(), session.accessedProperty()});
    private final MainWindow main;
    private final ReminderService reminders;
    private final PlayfulSettingsStore settingsStore;
    private PlayfulSettings settings;
    private final ProfilePreferences profile;
    private boolean smartPlacement;
    private boolean closed;
    private long accessOrder;

    public NoteManager(NoteRepository repository, Stage primary) {
        this(repository, primary, PlayfulSettingsStore.userSettings(), new ProfilePreferences(
                org.stickynotefx.persistence.DatabaseProvider.defaultPath().resolveSibling("visuals.properties")));
    }

    public NoteManager(NoteRepository repository, Stage primary, PlayfulSettingsStore settingsStore) {
        this(repository, primary, settingsStore, new ProfilePreferences(settingsStore.sidecarPath()));
    }

    public NoteManager(NoteRepository repository, Stage primary, PlayfulSettingsStore settingsStore, ProfilePreferences profile) {
        this.repository = repository;
        this.profile = profile;
        this.settingsStore = settingsStore;
        settings = settingsStore.load();
        main = new MainWindow(primary, this::createNote, this::showAll, this::show, this::quit);
        main.setTheme(profile.theme());
        main.onPreferences(() -> showPreferences(primary));
        reminders = new ReminderService(Clock.systemDefaultZone(), Platform::runLater,
                id -> sessions.containsKey(id) ? sessions.get(id).note() : null,
                this::reminderDelivered, this::reportError);
    }

    public void restore() {
        repository.findAll().forEach(note -> openWindow(register(note)));
        if (sessions.isEmpty()) createNote();
    }

    public StickyNoteController createNote() {
        return openWindow(createSession());
    }

    private NoteSession createSession() {
        var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        int offset = sessions.size() % 12 * 28;
        var placement = smartPlacement ? WindowPlacement.choose(bounds, windows.stream().map(controller -> {
            var stage = controller.view().stage();
            return new javafx.geometry.Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        }).toList()) : new javafx.geometry.Rectangle2D(bounds.getMinX() + 80 + offset,
            bounds.getMinY() + 80 + offset, 320, 300);
        Note created = repository.create(Note.blank(placement.getMinX(), placement.getMinY())
                .withBounds(placement.getMinX(), placement.getMinY(), placement.getWidth(), placement.getHeight()));
        discardVisual(created.id());
        return register(created);
    }

    private NoteSession register(Note note) {
        var session = new NoteSession(note, this::save);
        sessions.put(note.id(), session);
        session.onReminderChanged(timestamp -> {
            saveExpired(session, false);
            selectedWindows(note.id()).forEach(controller -> controller.view().setPlayfulExpired(false));
        });
        collection.add(session);
        reminders.refresh(note);
        refreshList();
        return session;
    }

    private StickyNoteController openWindow(NoteSession session) {
        StickyNoteController controller = new StickyNoteController(session);
        windows.add(controller);
        var view = controller.view();
        view.setTheme(profile.theme());
        controller.configureBrowser(collection, () -> {
            if (controller.session() != null) controller.session().accessedProperty().set(++accessOrder);
            applyVisual(controller);
        });
        applyVisual(controller);
        view.onPlayful(next -> {
            if (controller.note() != null) setNotePlayfulSettings(controller.note().id(), next);
        });
        view.onSnooze(minutes -> controller.setReminder(System.currentTimeMillis() + minutes * 60_000L));
        view.clearReminderItem().setOnAction(event -> controller.setReminder(null));
        view.preferencesItem().setOnAction(event -> showPreferences(view.stage()));
        view.addButton().setOnAction(event -> {
            if (controller.mode() == StickyNoteController.Mode.QUICK) createNote();
            else createIn(controller);
        });
        view.sidebar().newNoteButton().setOnAction(event -> createIn(controller));
        view.showAllItem().setOnAction(event -> showAll());
        view.quitItem().setOnAction(event -> quit());
        view.deleteItem().setOnAction(event -> confirmDelete(controller));
        view.reminderButton().setOnAction(event -> new ReminderDialog(view.stage(), controller.note().reminderTimestamp())
                .showAndWait().ifPresent(selection -> controller.setReminder(selection.timestamp())));
        controller.show();
        return controller;
    }

    private void createIn(StickyNoteController controller) {
        controller.flush();
        controller.view().sidebar().searchField().clear();
        controller.replaceSession(createSession());
        controller.view().editor().requestFocus();
    }

    private java.util.List<StickyNoteController> selectedWindows(long id) {
        return windows.stream().filter(controller -> controller.note() != null && controller.note().id() == id).toList();
    }

    private void applyVisual(StickyNoteController controller) {
        if (controller.note() == null) return;
        var visual = profile.note(controller.note().id(), settings);
        controller.view().setPlayfulSettings(visual.settings());
        controller.view().setPlayfulExpired(visual.expired() && controller.note().reminderTimestamp() == null);
    }

    private void save(Note note) {
        repository.update(note);
        reminders.refresh(note);
        refreshList();
    }

    void reminderDelivered(long id, long expectedTimestamp) {
        var session = sessions.get(id);
        if (session == null || !Objects.equals(session.note().reminderTimestamp(), expectedTimestamp)) return;
        session.flush();
        if (repository.clearReminder(id, expectedTimestamp)) {
            session.setReminder(null);
            saveExpired(session, true);
            selectedWindows(id).forEach(controller -> controller.view().reminderFired());
        }
    }

    private void showPreferences(Stage owner) {
        var dialog = new PlayfulPreferencesDialog(owner, settings, this::setPlayfulSettings);
        dialog.addThemeSelector(profile.theme(), this::setTheme);
        dialog.addPlacementToggle(smartPlacement, this::setSmartPlacement);
        dialog.showAndWait();
    }

    public void setSmartPlacement(boolean enabled) { smartPlacement = enabled; }

    public void setTheme(ThemeRegistry.Theme theme) {
        profile.setTheme(theme);
        main.setTheme(theme);
        windows.forEach(controller -> {
            controller.view().setTheme(theme);
            controller.requestAutosave();
        });
    }

    public void setNotePlayfulSettings(long id, PlayfulSettings next) {
        var session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("Unknown note: " + id);
        profile.setNote(id, new ProfilePreferences.NoteVisual(next.playfulModeEnabled(), next.playfulModeType(),
                profile.note(id, settings).expired()));
        selectedWindows(id).forEach(this::applyVisual);
    }

    private void saveExpired(NoteSession session, boolean expired) {
        if (!expired && !profile.hasNote(session.note().id())) return;
        var current = profile.note(session.note().id(), settings).settings();
        if (expired && current.activeMode() == org.stickynotefx.ui.playful.PlayfulMode.NONE
            && !profile.hasNote(session.note().id())) return;
        try {
            profile.setNote(session.note().id(), new ProfilePreferences.NoteVisual(
                    current.playfulModeEnabled(), current.playfulModeType(), expired));
        } catch (RuntimeException failure) {
            reportError("Visual state could not be saved. Reminder delivery is unaffected.");
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Visual state save failed", failure);
        }
    }

    public void setPlayfulSettings(PlayfulSettings next) {
        try {
            settingsStore.save(next);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        settings = next;
        windows.stream().filter(controller -> controller.note() != null && !profile.hasNote(controller.note().id()))
            .forEach(this::applyVisual);
    }

    private void confirmDelete(StickyNoteController controller) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete this note permanently?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(controller.view().stage());
        confirmation.setHeaderText("Delete note");
        ThemeRegistry.apply(confirmation.getDialogPane(), profile.theme());
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> delete(controller.note().id()));
    }

    public void delete(long id) {
        repository.delete(id);
        discardVisual(id);
        reminders.cancel(id);
        var session = sessions.remove(id);
        if (session != null) session.close();
        var affected = selectedWindows(id);
        NoteSession next = sessions.values().stream().findFirst().orElse(null);
        for (var controller : affected) {
            if (controller.mode() == StickyNoteController.Mode.QUICK) {
                controller.dispose();
                windows.remove(controller);
            } else {
                controller.replaceSession(next);
            }
        }
        collection.remove(session);
        refreshList();
        if (sessions.isEmpty() && windows.stream().noneMatch(controller ->
                controller.mode() != StickyNoteController.Mode.QUICK && controller.view().stage().isShowing()))
            showManager();
    }

    private void discardVisual(long id) {
        try {
            profile.removeNote(id);
        } catch (RuntimeException failure) {
            main.setStatus("Visual preferences could not be cleaned up. Note storage is unaffected.");
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Visual cleanup failed", failure);
        }
    }

    public void show(long id) {
        var session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("Unknown note: " + id);
        var compact = selectedWindows(id).stream().filter(controller -> controller.mode() == StickyNoteController.Mode.QUICK)
                .findFirst().orElse(null);
        if (compact == null) openWindow(session);
        else compact.show();
    }

    public void showAll() {
        var focused = windows.stream().filter(controller -> controller.view().stage().isFocused()).findFirst();
        java.util.List.copyOf(sessions.keySet()).forEach(this::show);
        focused.ifPresent(StickyNoteController::show);
    }

    public void showManager() {
        var host = windows.stream().filter(controller -> controller.mode() != StickyNoteController.Mode.QUICK)
                .findFirst().orElseGet(() -> windows.stream().findFirst().orElseGet(() ->
                        openWindow(sessions.values().stream().findFirst().orElse(null))));
        host.browse();
        host.show();
    }
    StickyNoteController controller(long id) { return selectedWindows(id).stream().findFirst().orElse(null); }

    private void refreshList() { main.update(sessions.values().stream().map(NoteSession::note).toList()); }

    private void reportError(String message) {
        main.setStatus(message);
        main.show();
    }

    public void flush() {
        for (var session : sessions.values()) {
            try {
                session.flush();
            } catch (RuntimeException failure) {
                var host = selectedWindows(session.note().id()).stream().findFirst().orElseGet(() -> openWindow(session));
                host.show();
                throw failure;
            }
        }
    }

    private void quit() {
        flush();
        Platform.exit();
    }

    @Override public void close() {
        if (closed) return;
        flush();
        closed = true;
        reminders.close();
        windows.forEach(StickyNoteController::dispose);
        sessions.values().forEach(NoteSession::close);
    }
}