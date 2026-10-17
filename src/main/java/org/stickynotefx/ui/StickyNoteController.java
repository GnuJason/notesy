package org.stickynotefx.ui;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.stage.Screen;
import org.stickynotefx.model.Note;

import java.util.Objects;
import java.util.function.Consumer;

public final class StickyNoteController {
    public enum Mode { QUICK, BROWSER, NOTEBOOK }
    private final StickyNoteView view = new StickyNoteView();
    private NoteSession session;
    private final boolean ownsSession;
    private boolean updatingEditor;
    private boolean updatingPin;
    private boolean changingBounds;
    private boolean disposed;
    private Mode mode = Mode.QUICK;
    private Mode previousMode = Mode.QUICK;
    private Rectangle2D previousBounds;
    private Rectangle2D browserBounds;
    private Runnable selectionChanged = () -> { };
    private final java.util.Map<Long, EditorPosition> positions = new java.util.HashMap<>();
    private record EditorPosition(int anchor, int caret) { }
    private final ChangeListener<Boolean> pinChanged = (observable, before, after) -> applyPin();
    private final ChangeListener<Note> noteChanged = (observable, before, after) -> display(before, after);
    private final ChangeListener<String> statusChanged = (observable, before, after) -> view.setStatus(after);
    private double dragX;
    private double dragY;
    private double resizeX;
    private double resizeY;
    private double initialWidth;
    private double initialHeight;

    public StickyNoteController(Note initial, Consumer<Note> save) {
        this(new NoteSession(visibleBounds(initial), save), true);
    }

    StickyNoteController(NoteSession session) { this(session, false); }

    private StickyNoteController(NoteSession session, boolean ownsSession) {
        this.ownsSession = ownsSession;
        attach(session);
        if (note() != null) applyBounds(visibleBounds(note()));
        view.editor().textProperty().addListener((observable, before, after) -> {
            if (!updatingEditor && this.session != null) this.session.update(current -> current.withContent(after));
        });
        view.onColor(color -> {
            if (this.session != null) this.session.update(current -> current.withColor(color));
        });
        view.pinButton().selectedProperty().addListener((observable, before, after) -> {
            if (!updatingPin && this.session != null && mode != Mode.NOTEBOOK)
                this.session.pinnedProperty().set(after);
        });
        view.stage().alwaysOnTopProperty().addListener((observable, before, after) -> {
            if (!updatingPin && this.session != null && mode != Mode.NOTEBOOK)
                this.session.pinnedProperty().set(after);
        });
        view.dragBar().setOnMousePressed(event -> {
            if (isButton(event.getTarget()) || mode == Mode.NOTEBOOK) return;
            dragX = event.getScreenX() - view.stage().getX();
            dragY = event.getScreenY() - view.stage().getY();
        });
        view.dragBar().setOnMouseDragged(event -> {
            if (isButton(event.getTarget()) || !event.isPrimaryButtonDown() || mode == Mode.NOTEBOOK) return;
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
            if (!event.isPrimaryButtonDown() || mode == Mode.NOTEBOOK) return;
            view.stage().setWidth(Math.max(260, initialWidth + event.getScreenX() - resizeX));
            view.stage().setHeight(Math.max(220, initialHeight + event.getScreenY() - resizeY));
            updateBounds();
        });
        view.stage().setOnCloseRequest(event -> {
            event.consume();
            flush();
            view.stage().hide();
        });
        view.minimizeButton().setOnAction(event -> {
            flush();
            view.stage().setIconified(true);
        });
        javafx.beans.InvalidationListener boundsChanged = observable -> {
            if (view.stage().isShowing()) updateBounds();
        };
        view.stage().xProperty().addListener(boundsChanged);
        view.stage().yProperty().addListener(boundsChanged);
        view.stage().widthProperty().addListener(boundsChanged);
        view.stage().heightProperty().addListener(boundsChanged);
        view.dragBar().setOnMouseReleased(event -> {
            if (mode != Mode.QUICK || !view.snapToGridItem().isSelected() || isButton(event.getTarget())) return;
            var bounds = screenBounds();
            view.stage().setX(Math.max(bounds.getMinX(), Math.min(bounds.getMaxX() - view.stage().getWidth(),
                    bounds.getMinX() + Math.round((view.stage().getX() - bounds.getMinX()) / 20) * 20)));
            view.stage().setY(Math.max(bounds.getMinY(), Math.min(bounds.getMaxY() - view.stage().getHeight(),
                    bounds.getMinY() + Math.round((view.stage().getY() - bounds.getMinY()) / 20) * 20)));
        });
    }

    private void attach(NoteSession next) {
        session = next;
        view.setSelected(next != null);
        if (next == null) {
            updatingEditor = true;
            try { view.editor().clear(); }
            finally { updatingEditor = false; }
            view.setStatus("");
            view.setColor(Note.DEFAULT_COLOR);
            view.disposePlayful();
            applyPin();
            return;
        }
        display(null, next.note());
        view.setStatus(next.statusProperty().get());
        next.noteProperty().addListener(noteChanged);
        next.statusProperty().addListener(statusChanged);
        next.pinnedProperty().addListener(pinChanged);
        applyPin();
    }

    private void detach() {
        if (session == null) return;
        session.noteProperty().removeListener(noteChanged);
        session.statusProperty().removeListener(statusChanged);
        session.pinnedProperty().removeListener(pinChanged);
    }

    private void applyPin() {
        updatingPin = true;
        try {
            boolean pinned = session != null && session.pinnedProperty().get();
            view.pinButton().setSelected(pinned);
            view.pinButton().setDisable(session == null || mode == Mode.NOTEBOOK);
            view.stage().setAlwaysOnTop(pinned && mode != Mode.NOTEBOOK);
        } finally {
            updatingPin = false;
        }
    }

    void configureBrowser(javafx.collections.ObservableList<NoteSession> sessions, Runnable changed) {
        selectionChanged = changed;
        var sidebar = new NotesSidebarView(sessions, this::selectSession);
        view.installSidebar(sidebar);
        sidebar.select(session);
        view.notesButton().setOnAction(event -> {
            if (mode == Mode.QUICK) browse();
            else compact();
        });
        view.managerItem().setOnAction(event -> browse());
        view.expandButton().setDisable(false);
        view.expandButton().setOnAction(event -> {
            if (mode == Mode.NOTEBOOK) restore();
            else expand();
        });
    }

    public void selectSession(NoteSession next) {
        if (next == session) return;
        flush();
        replaceSession(next);
    }

    void replaceSession(NoteSession next) {
        if (session != null) positions.put(session.note().id(),
                new EditorPosition(view.editor().getAnchor(), view.editor().getCaretPosition()));
        detach();
        attach(next);
        if (next != null) {
            var position = positions.get(next.note().id());
            if (position != null) view.editor().selectRange(
                    Math.min(position.anchor(), next.note().content().length()),
                    Math.min(position.caret(), next.note().content().length()));
        }
        if (view.sidebar() != null) view.sidebar().select(next);
        selectionChanged.run();
    }

    public Mode mode() { return mode; }

    public void browse() {
        if (mode != Mode.QUICK) return;
        flush();
        mode = Mode.BROWSER;
        view.setBrowsing(true);
        var screen = screenBounds();
        resizeWindow(browserBounds != null ? browserBounds : new Rectangle2D(
                Math.max(screen.getMinX(), view.stage().getX() - 220),
                Math.min(view.stage().getY(), Math.max(screen.getMinY(), screen.getMaxY() - 480)),
                Math.min(720, screen.getWidth()), Math.min(480, screen.getHeight())));
    }

    public void compact() {
        flush();
        if (session == null) return;
        if (mode == Mode.BROWSER) browserBounds = new Rectangle2D(view.stage().getX(), view.stage().getY(),
                view.stage().getWidth(), view.stage().getHeight());
        view.sidebar().setNotebook(false);
        view.setNotebook(false);
        view.setBrowsing(false);
        mode = Mode.QUICK;
        resizeWindow(visibleBounds(note()));
        applyPin();
    }

    public void expand() {
        if (mode == Mode.NOTEBOOK) return;
        flush();
        previousMode = mode;
        previousBounds = new Rectangle2D(view.stage().getX(), view.stage().getY(),
                view.stage().getWidth(), view.stage().getHeight());
        mode = Mode.NOTEBOOK;
        view.setBrowsing(true);
        view.sidebar().setNotebook(true);
        view.setNotebook(true);
        applyPin();
        resizeWindow(screenBounds());
    }

    public void restore() {
        if (mode != Mode.NOTEBOOK) return;
        flush();
        mode = previousMode == Mode.QUICK && session == null ? Mode.BROWSER : previousMode;
        view.sidebar().setNotebook(false);
        view.setNotebook(false);
        view.setBrowsing(mode != Mode.QUICK);
        if (mode == Mode.QUICK) resizeWindow(visibleBounds(note()));
        else resizeWindow(previousBounds);
        applyPin();
    }

    private void resizeWindow(Note bounds) {
        resizeWindow(new Rectangle2D(bounds.x(), bounds.y(), bounds.width(), bounds.height()));
    }

    private void resizeWindow(Rectangle2D bounds) {
        changingBounds = true;
        try {
            view.stage().setMinWidth(mode == Mode.QUICK ? 260 : Math.min(560, screenBounds().getWidth()));
            view.stage().setMinHeight(mode == Mode.QUICK ? 220 : Math.min(300, screenBounds().getHeight()));
            var screen = screenBounds();
            view.stage().setX(Math.max(screen.getMinX(), Math.min(bounds.getMinX(), screen.getMaxX() - bounds.getWidth())));
            view.stage().setY(Math.max(screen.getMinY(), Math.min(bounds.getMinY(), screen.getMaxY() - bounds.getHeight())));
            view.stage().setWidth(bounds.getWidth());
            view.stage().setHeight(bounds.getHeight());
        } finally {
            changingBounds = false;
        }
    }

    private void display(Note before, Note after) {
        if (!view.editor().getText().equals(after.content())) {
            int anchor = view.editor().getAnchor();
            int caret = view.editor().getCaretPosition();
            updatingEditor = true;
            try {
                view.editor().setText(after.content());
                view.editor().selectRange(Math.min(anchor, after.content().length()),
                        Math.min(caret, after.content().length()));
            } finally {
                updatingEditor = false;
            }
        }
        view.setColor(after.color());
        if (before == null || !Objects.equals(before.reminderTimestamp(), after.reminderTimestamp()))
            view.setReminder(after.reminderTimestamp());
    }

    private static boolean isButton(Object target) {
        for (Node node = target instanceof Node candidate ? candidate : null;
             node != null; node = node.getParent()) {
            if (node instanceof ButtonBase) return true;
        }
        return false;
    }

    private Rectangle2D screenBounds() {
        return Screen.getScreensForRectangle(view.stage().getX(), view.stage().getY(), 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
    }

    private static Note visibleBounds(Note note) {
        Rectangle2D bounds = Screen.getScreens().stream().map(Screen::getVisualBounds)
                .filter(screen -> screen.contains(note.x() + 40, note.y() + 30))
                .findFirst().orElse(Screen.getPrimary().getVisualBounds());
        double width = Math.min(note.width(), bounds.getWidth());
        double height = Math.min(note.height(), bounds.getHeight());
        return note.withBounds(Math.max(bounds.getMinX(), Math.min(note.x(), bounds.getMaxX() - width)),
                Math.max(bounds.getMinY(), Math.min(note.y(), bounds.getMaxY() - height)), width, height);
    }

    private void applyBounds(Note note) {
        view.stage().setX(note.x());
        view.stage().setY(note.y());
        view.stage().setWidth(note.width());
        view.stage().setHeight(note.height());
    }

    private void updateBounds() {
        if (disposed || changingBounds || mode != Mode.QUICK || session == null) return;
        session.update(current -> current.withBounds(view.stage().getX(), view.stage().getY(),
                view.stage().getWidth(), view.stage().getHeight()));
    }

    public void flush() { if (session != null) session.flush(); }
    public void setReminder(Long timestamp) { session.setReminder(timestamp); }
    public void onReminderChanged(Consumer<Long> action) { session.onReminderChanged(action); }
    public void requestAutosave() { if (session != null) session.requestAutosave(); }
    NoteSession session() { return session; }

    public void dispose() {
        disposed = true;
        detach();
        if (ownsSession && session != null) session.close();
        if (view.sidebar() != null) view.sidebar().dispose();
        view.disposePlayful();
        view.stage().hide();
    }

    public Note note() { return session == null ? null : session.note(); }
    public StickyNoteView view() { return view; }
    public void show() {
        view.stage().setIconified(false);
        view.stage().show();
        view.stage().toFront();
    }
}
