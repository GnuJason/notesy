package org.stickynotefx.ui;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;

public final class NotesSidebarView extends VBox {
    private final Button newNote = new Button("+ New Note");
    private final TextField search = new TextField();
    private final Button clear = new Button("Clear");
    private final HBox searchRow = new HBox(4, search, clear);
    private final ListView<NoteSession> list = new ListView<>();
    private final FilteredList<NoteSession> filtered;
    private boolean selecting;
    private NoteSession selected;

    public NotesSidebarView(ObservableList<NoteSession> sessions, Consumer<NoteSession> select) {
        super(8);
        getStyleClass().add("notes-sidebar");
        setPadding(new Insets(10));
        setMinWidth(180);
        setPrefWidth(220);
        setMaxWidth(300);
        newNote.setAccessibleText("New note in this workspace");
        newNote.setId("workspace-new-note");
        newNote.setMaxWidth(Double.MAX_VALUE);
        search.setPromptText("Search notes...");
        search.setAccessibleText("Search note content");
        search.setId("search-notes");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMinWidth(0);
        clear.setAccessibleText("Clear note search");
        clear.setOnAction(event -> search.clear());
        filtered = new FilteredList<>(sessions);
        var sorted = new SortedList<>(filtered, Comparator
                .comparingLong((NoteSession session) -> session.accessedProperty().get()).reversed()
                .thenComparing(Comparator.comparingLong((NoteSession session) -> session.note().id()).reversed()));
        list.setItems(sorted);
        list.setAccessibleText("Saved notes");
        list.setId("saved-notes");
        var placeholder = new Label("No notes");
        list.setPlaceholder(placeholder);
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.toLowerCase(Locale.ROOT);
            selecting = true;
            try {
                filtered.setPredicate(session -> session.note().content().toLowerCase(Locale.ROOT).contains(query));
            } finally {
                select(selected);
            }
            placeholder.setText(query.isEmpty() ? "No notes" : "No matching notes");
        });
        list.setCellFactory(value -> {
            var cell = new ListCell<NoteSession>() {
                @Override protected void updateItem(NoteSession session, boolean empty) {
                    super.updateItem(session, empty);
                    setText(null);
                    setGraphic(empty || session == null ? null : MainWindow.preview(session.note()));
                }
            };
            cell.setPrefWidth(0);
            return cell;
        });
        list.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (selecting || after == null) return;
            if (before != null && !list.getItems().contains(before)) {
                select(selected);
                return;
            }
            try {
                select.accept(after);
            } catch (RuntimeException failure) {
                select(before);
                throw failure;
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(newNote, searchRow, new Label("Recent / All notes"), list);
        setNotebook(false);
    }

    public void select(NoteSession session) {
        selected = session;
        selecting = true;
        try {
            if (session == null || !list.getItems().contains(session)) list.getSelectionModel().clearSelection();
            else list.getSelectionModel().select(session);
        } finally {
            selecting = false;
        }
    }

    public void setNotebook(boolean notebook) {
        searchRow.setVisible(notebook);
        searchRow.setManaged(notebook);
    }

    public Button newNoteButton() { return newNote; }
    public TextField searchField() { return search; }
    public ListView<NoteSession> list() { return list; }

    public void dispose() {
        selecting = true;
        list.setItems(javafx.collections.FXCollections.observableArrayList());
    }
}
