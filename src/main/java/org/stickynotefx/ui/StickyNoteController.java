package org.stickynotefx.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.stage.Screen;
import javafx.util.Duration;
import org.stickynotefx.model.Note;

import java.util.function.Consumer;

public final class StickyNoteController {
    private final StickyNoteView view;
    private final Consumer<Note> save;
    private final PauseTransition autosave = new PauseTransition(Duration.millis(400));
    private Note note;
    private double dragX;
    private double dragY;
    private double resizeX;
    private double resizeY;
    private double initialWidth;
    private double initialHeight;
    private Consumer<Long> reminderChanged = timestamp -> { };

    public StickyNoteController(Note initial, Consumer<Note> save) {
        this.note = visibleBounds(initial);
        this.save = save;
        view = new StickyNoteView();
        view.stage().setX(note.x());
        view.stage().setY(note.y());
        view.stage().setWidth(note.width());
        view.stage().setHeight(note.height());
        view.editor().setText(note.content());
        view.setColor(note.color());
        view.setReminder(note.reminderTimestamp());
        autosave.setOnFinished(event -> flush());
        view.editor().textProperty().addListener((observable, before, after) -> {
            note = note.withContent(after);
            dirty();
        });
        view.onColor(color -> {
            note = note.withColor(color);
            view.setColor(color);
            dirty();
        });
        view.dragBar().setOnMousePressed(event -> {
            if (isButton(event.getTarget())) return;
            dragX = event.getScreenX() - view.stage().getX();
            dragY = event.getScreenY() - view.stage().getY();
        });
        view.dragBar().setOnMouseDragged(event -> {
            if (isButton(event.getTarget()) || !event.isPrimaryButtonDown()) return;
            view.stage().setX(event.getScreenX() - dragX);
            view.stage().setY(event.getScreenY() - dragY);
            updateBounds();
        });
        view.resizeGrip().setOnMousePressed(event -> {
            resizeX = event.getScreenX();
            resizeY = event.getScreenY();
            initialWidth = view.stage().getWidth();
            initialHeight = view.stage().getHeight();
        });
        view.resizeGrip().setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            view.stage().setWidth(Math.max(260, initialWidth + event.getScreenX() - resizeX));
            view.stage().setHeight(Math.max(220, initialHeight + event.getScreenY() - resizeY));
            updateBounds();
        });
        view.stage().setOnHidden(event -> flush());
        javafx.beans.InvalidationListener boundsChanged = observable -> {
            if (view.stage().isShowing()) updateBounds();
        };
        view.stage().xProperty().addListener(boundsChanged);
        view.stage().yProperty().addListener(boundsChanged);
        view.stage().widthProperty().addListener(boundsChanged);
        view.stage().heightProperty().addListener(boundsChanged);
        view.dragBar().setOnMouseReleased(event -> {
            if (!view.snapToGridItem().isSelected() || isButton(event.getTarget())) return;
            var bounds = Screen.getScreensForRectangle(view.stage().getX(), view.stage().getY(), 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
            view.stage().setX(Math.max(bounds.getMinX(), Math.min(bounds.getMaxX() - view.stage().getWidth(),
                bounds.getMinX() + Math.round((view.stage().getX() - bounds.getMinX()) / 20) * 20)));
            view.stage().setY(Math.max(bounds.getMinY(), Math.min(bounds.getMaxY() - view.stage().getHeight(),
                bounds.getMinY() + Math.round((view.stage().getY() - bounds.getMinY()) / 20) * 20)));
        });
    }

    private static boolean isButton(Object target) {
        for (Node node = target instanceof Node candidate ? candidate : null;
             node != null; node = node.getParent()) {
            if (node instanceof ButtonBase) return true;
        }
        return false;
    }

    private static Note visibleBounds(Note note) {
        Rectangle2D bounds = Screen.getScreens().stream().map(Screen::getVisualBounds)
                .filter(screen -> screen.contains(note.x() + 40, note.y() + 30))
                .findFirst().orElse(Screen.getPrimary().getVisualBounds());
        double width = Math.min(note.width(), bounds.getWidth());
        double height = Math.min(note.height(), bounds.getHeight());
        double left = Math.max(bounds.getMinX(), Math.min(note.x(), bounds.getMaxX() - width));
        double top = Math.max(bounds.getMinY(), Math.min(note.y(), bounds.getMaxY() - height));
        return note.withBounds(left, top, width, height);
    }

    private void updateBounds() {
        note = note.withBounds(view.stage().getX(), view.stage().getY(),
                view.stage().getWidth(), view.stage().getHeight());
        dirty();
    }

    private void dirty() {
        view.setStatus("Unsaved");
        autosave.playFromStart();
    }

    public void flush() {
        autosave.stop();
        try {
            save.accept(note);
            view.setStatus("");
        } catch (RuntimeException failure) {
            view.setStatus("Save failed");
            throw failure;
        }
    }

    public void setReminder(Long timestamp) {
        note = note.withReminder(timestamp);
        view.setReminder(timestamp);
        flush();
        reminderChanged.accept(timestamp);
    }

    public void onReminderChanged(Consumer<Long> action) { reminderChanged = action; }
    public void requestAutosave() { dirty(); }

    public void dispose() {
        autosave.stop();
        view.disposePlayful();
        view.stage().setOnHidden(null);
        view.stage().hide();
    }

    public Note note() { return note; }
    public StickyNoteView view() { return view; }
    public void show() { view.stage().show(); view.stage().toFront(); }
}