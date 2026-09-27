package org.stickynotefx.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.stickynotefx.model.Note;

import java.util.List;
import java.util.function.LongConsumer;

public final class MainWindow {
    private final Stage stage;
    private final ListView<Note> notes = new ListView<>();
    private final Label status = new Label();
    private final javafx.scene.control.MenuItem preferences = new javafx.scene.control.MenuItem("Preferences...");

    public MainWindow(Stage stage, Runnable create, Runnable showAll, LongConsumer open, Runnable quit) {
        this.stage = stage;
        stage.setTitle("Notesy");
        org.stickynotefx.theme.WindowIcons.apply(stage);
        Button add = new Button("+ New note");
        add.setOnAction(event -> create.run());
        Button stack = new Button("Show all");
        stack.setOnAction(event -> showAll.run());
        Button exit = new Button("Quit");
        exit.setOnAction(event -> quit.run());
        Button openButton = new Button("Open note");
        openButton.disableProperty().bind(notes.getSelectionModel().selectedItemProperty().isNull());
        openButton.setOnAction(event -> open.accept(notes.getSelectionModel().getSelectedItem().id()));
        notes.setPlaceholder(new Label("No notes"));
        notes.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                setText(null);
                if (empty || note == null) {
                    setGraphic(null);
                } else {
                    String text = note.content().lines().filter(line -> !line.isBlank()).findFirst().orElse("Blank note");
                    Label title = new Label(text.length() > 80 ? text.substring(0, 80) + "..." : text);
                    title.setMaxWidth(280);
                    HBox row = new HBox(10, new Circle(6, Color.web(note.color())), title);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 2, 8, 2));
                    setGraphic(row);
                }
            }
        });
        notes.setOnMouseClicked(event -> {
            Note selected = notes.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) open.accept(selected.id());
        });
        status.setWrapText(true);
        status.setPadding(new Insets(8));
        BorderPane root = new BorderPane(notes);
        var options = new javafx.scene.control.MenuButton("\u22ef", null, preferences);
        options.setTooltip(new javafx.scene.control.Tooltip("Options"));
        options.setAccessibleText("Options");
        root.setTop(new ToolBar(add, openButton, stack, exit, options));
        root.setBottom(status);
        stage.setScene(new Scene(root, 460, 360));
        stage.setMinWidth(420);
        stage.setMinHeight(260);
        stage.setOnCloseRequest(event -> {
            event.consume();
            if (notes.getItems().isEmpty()) quit.run();
            else stage.hide();
        });
    }

    public void update(List<Note> values) {
        Note selection = notes.getSelectionModel().getSelectedItem();
        notes.setItems(FXCollections.observableArrayList(values));
        if (selection != null) values.stream().filter(note -> note.id() == selection.id()).findFirst()
                .ifPresent(note -> notes.getSelectionModel().select(note));
    }

    public void show() { stage.show(); stage.toFront(); }
    public void setTheme(org.stickynotefx.theme.ThemeRegistry.Theme theme) {
        org.stickynotefx.theme.ThemeRegistry.apply(stage.getScene().getRoot(), theme);
    }
    public void onPreferences(Runnable action) { preferences.setOnAction(event -> action.run()); }
    public void setStatus(String text) { status.setText(text); }
}