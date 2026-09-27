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
    private final Map<Long, StickyNoteController> notes = new LinkedHashMap<>();
    private final MainWindow main;
    private final ReminderService reminders;
    private final PlayfulSettingsStore settingsStore;
    private PlayfulSettings settings;
    private final ProfilePreferences profile;
    private boolean smartPlacement;
    private boolean closed;

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
                id -> notes.containsKey(id) ? notes.get(id).note() : null,
                this::reminderDelivered, message -> { main.setStatus(message); main.show(); });
    }

    public void restore() {
        repository.findAll().forEach(this::open);
        if (notes.isEmpty()) createNote();
    }

    public StickyNoteController createNote() {
        var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        int offset = notes.size() % 12 * 28;
        var placement = smartPlacement ? WindowPlacement.choose(bounds, notes.values().stream().map(controller -> {
            var stage = controller.view().stage();
            return new javafx.geometry.Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        }).toList()) : new javafx.geometry.Rectangle2D(bounds.getMinX() + 80 + offset,
            bounds.getMinY() + 80 + offset, 320, 300);
        Note created = repository.create(Note.blank(placement.getMinX(), placement.getMinY())
                .withBounds(placement.getMinX(), placement.getMinY(), placement.getWidth(), placement.getHeight()));
        discardVisual(created.id());
        return open(created);
    }

    private StickyNoteController open(Note note) {
        StickyNoteController controller = new StickyNoteController(note, this::save);
        notes.put(note.id(), controller);
        var view = controller.view();
        var visual = profile.note(note.id(), settings);
        view.setTheme(profile.theme());
        view.setPlayfulSettings(visual.settings());
        view.setPlayfulExpired(visual.expired() && note.reminderTimestamp() == null);
        view.onPlayful(next -> setNotePlayfulSettings(note.id(), next));
        view.onSnooze(minutes -> controller.setReminder(System.currentTimeMillis() + minutes * 60_000L));
        view.clearReminderItem().setOnAction(event -> controller.setReminder(null));
        controller.onReminderChanged(timestamp -> saveExpired(controller, false));
        view.preferencesItem().setOnAction(event -> showPreferences(view.stage()));
        view.addButton().setOnAction(event -> createNote());
        view.managerItem().setOnAction(event -> { refreshList(); main.show(); });
        view.quitItem().setOnAction(event -> quit());
        view.deleteItem().setOnAction(event -> confirmDelete(controller));
        view.reminderButton().setOnAction(event -> new ReminderDialog(view.stage(), controller.note().reminderTimestamp())
                .showAndWait().ifPresent(selection -> controller.setReminder(selection.timestamp())));
        view.stage().setOnCloseRequest(event -> {
            event.consume();
            controller.flush();
            view.stage().hide();
            main.show();
        });
        reminders.refresh(note);
        refreshList();
        controller.show();
        return controller;
    }

    private void save(Note note) {
        repository.update(note);
        reminders.refresh(note);
        refreshList();
    }

    void reminderDelivered(long id, long expectedTimestamp) {
        StickyNoteController controller = notes.get(id);
        if (controller == null || !Objects.equals(controller.note().reminderTimestamp(), expectedTimestamp)) return;
        controller.flush();
        if (repository.clearReminder(id, expectedTimestamp)) {
            controller.setReminder(null);
            saveExpired(controller, true);
            controller.view().reminderFired();
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
        notes.values().forEach(controller -> {
            controller.view().setTheme(theme);
            controller.requestAutosave();
        });
    }

    public void setNotePlayfulSettings(long id, PlayfulSettings next) {
        var controller = notes.get(id);
        if (controller == null) return;
        profile.setNote(id, new ProfilePreferences.NoteVisual(next.playfulModeEnabled(), next.playfulModeType(),
                controller.view().playfulExpired()));
        controller.view().setPlayfulSettings(next);
    }

    private void saveExpired(StickyNoteController controller, boolean expired) {
        if (!expired && !profile.hasNote(controller.note().id())) return;
        var current = controller.view().playfulSettings();
        if (expired && current.activeMode() == org.stickynotefx.ui.playful.PlayfulMode.NONE
            && !profile.hasNote(controller.note().id())) return;
        try {
            profile.setNote(controller.note().id(), new ProfilePreferences.NoteVisual(
                    current.playfulModeEnabled(), current.playfulModeType(), expired));
        } catch (RuntimeException failure) {
            main.setStatus("Visual state could not be saved. Reminder delivery is unaffected.");
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
        notes.values().stream().filter(controller -> !profile.hasNote(controller.note().id()))
            .forEach(controller -> controller.view().setPlayfulSettings(next));
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
        StickyNoteController controller = notes.remove(id);
        if (controller != null) controller.dispose();
        refreshList();
        if (notes.isEmpty()) main.show();
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
        StickyNoteController controller = notes.get(id);
        if (controller != null) controller.show();
    }

    public void showAll() {
        var focused = notes.values().stream().filter(controller -> controller.view().stage().isFocused()).findFirst();
        notes.values().forEach(StickyNoteController::show);
        focused.ifPresent(StickyNoteController::show);
    }

    public void showManager() { refreshList(); main.show(); }
    StickyNoteController controller(long id) { return notes.get(id); }

    private void refreshList() { main.update(notes.values().stream().map(StickyNoteController::note).toList()); }

    public void flush() { notes.values().forEach(StickyNoteController::flush); }

    private void quit() {
        flush();
        Platform.exit();
    }

    @Override public void close() {
        if (closed) return;
        flush();
        closed = true;
        reminders.close();
        notes.values().forEach(StickyNoteController::dispose);
    }
}