package org.stickynotefx.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.util.Duration;
import org.stickynotefx.model.Note;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class NoteSession implements AutoCloseable {
    private final ReadOnlyObjectWrapper<Note> note;
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("");
    private final Consumer<Note> save;
    private final PauseTransition autosave = new PauseTransition(Duration.millis(400));
    private Consumer<Long> reminderChanged = timestamp -> { };
    private boolean closed;
    private final BooleanProperty pinned = new SimpleBooleanProperty();
    private final LongProperty accessed = new SimpleLongProperty();

    public NoteSession(Note initial, Consumer<Note> save) {
        note = new ReadOnlyObjectWrapper<>(initial);
        this.save = save;
        autosave.setOnFinished(event -> flush());
    }

    public Note note() { return note.get(); }
    public ReadOnlyObjectProperty<Note> noteProperty() { return note.getReadOnlyProperty(); }
    public ReadOnlyStringProperty statusProperty() { return status.getReadOnlyProperty(); }
    public BooleanProperty pinnedProperty() { return pinned; }
    public LongProperty accessedProperty() { return accessed; }

    public void update(UnaryOperator<Note> change) {
        if (closed) throw new IllegalStateException("Note session is closed");
        Note next = change.apply(note());
        if (next.equals(note())) return;
        note.set(next);
        requestAutosave();
    }

    public void requestAutosave() {
        if (closed) throw new IllegalStateException("Note session is closed");
        status.set("Unsaved");
        autosave.playFromStart();
    }

    public void flush() {
        if (closed) throw new IllegalStateException("Note session is closed");
        autosave.stop();
        try {
            save.accept(note());
            status.set("");
        } catch (RuntimeException failure) {
            status.set("Save failed");
            throw failure;
        }
    }

    public void setReminder(Long timestamp) {
        update(current -> current.withReminder(timestamp));
        flush();
        reminderChanged.accept(timestamp);
    }

    public void onReminderChanged(Consumer<Long> action) { reminderChanged = action; }

    @Override public void close() {
        autosave.stop();
        closed = true;
    }
}
