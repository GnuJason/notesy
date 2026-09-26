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
    private boolean closed;

    public NoteManager(NoteRepository repository, Stage primary) {
        this(repository, primary, PlayfulSettingsStore.userSettings());
    }

    public NoteManager(NoteRepository repository, Stage primary, PlayfulSettingsStore settingsStore) {
        this.repository = repository;
        this.settingsStore = settingsStore;
        settings = settingsStore.load();
        main = new MainWindow(primary, this::createNote, this::showAll, this::show, this::quit);
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
        int offset = notes.size() % 12 * 28;
        var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        return open(repository.create(Note.blank(bounds.getMinX() + 80 + offset, bounds.getMinY() + 80 + offset)));
    }

    private StickyNoteController open(Note note) {
        StickyNoteController controller = new StickyNoteController(note, this::save);
        notes.put(note.id(), controller);
        var view = controller.view();
        view.setPlayfulSettings(settings);
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
            controller.view().reminderFired();
        }
    }

    private void showPreferences(Stage owner) {
        new PlayfulPreferencesDialog(owner, settings, this::setPlayfulSettings).showAndWait();
    }

    public void setPlayfulSettings(PlayfulSettings next) {
        try {
            settingsStore.save(next);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        settings = next;
        notes.values().forEach(controller -> controller.view().setPlayfulSettings(next));
    }

    private void confirmDelete(StickyNoteController controller) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete this note permanently?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(controller.view().stage());
        confirmation.setHeaderText("Delete note");
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> delete(controller.note().id()));
    }

    public void delete(long id) {
        repository.delete(id);
        reminders.cancel(id);
        StickyNoteController controller = notes.remove(id);
        if (controller != null) controller.dispose();
        refreshList();
        if (notes.isEmpty()) main.show();
    }

    public void show(long id) {
        StickyNoteController controller = notes.get(id);
        if (controller != null) controller.show();
    }

    public void showAll() { notes.values().forEach(StickyNoteController::show); }

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