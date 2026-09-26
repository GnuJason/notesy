package org.stickynotefx.ui;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.function.Consumer;

public final class StickyNoteView {
    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final BorderPane paper = new BorderPane();
    private final TextArea editor = new TextArea();
    private final HBox dragBar = new HBox(4);
    private final Region resizeGrip = new Region();
    private final Button addButton = iconButton("+", "New note");
    private final Button reminderButton = iconButton("\u23f1", "Set reminder");
    private final MenuItem deleteItem = new MenuItem("Delete note");
    private final MenuItem managerItem = new MenuItem("All notes");
    private final MenuItem quitItem = new MenuItem("Quit Notesy");
    private final Label status = new Label();
    private Consumer<String> onColor = value -> { };

    public StickyNoteView() {
        stage.setTitle("Notesy");
        stage.setMinWidth(260);
        stage.setMinHeight(220);
        editor.setWrapText(true);
        editor.setAccessibleText("Note text");
        editor.getStyleClass().add("note-editor");
        editor.setFont(Font.font("DejaVu Sans", 16));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMinWidth(24);
        MenuButton menu = new MenuButton("\u22ef");
        menu.setTooltip(new Tooltip("Note options"));
        menu.setAccessibleText("Note options");
        menu.getStyleClass().add("icon-button");
        HBox swatches = new HBox(8);
        swatches.setPadding(new Insets(6));
        for (String color : new String[]{"#FFF176", "#B8E0D2", "#F6BBC6", "#B9DBF4", "#E3D5F5"}) {
            Button swatch = new Button();
            swatch.setGraphic(new Circle(9, Color.web(color)));
            swatch.setTooltip(new Tooltip(color));
            swatch.setAccessibleText("Note color " + color);
            swatch.setOnAction(event -> onColor.accept(color));
            swatches.getChildren().add(swatch);
        }
        CustomMenuItem colors = new CustomMenuItem(swatches, false);
        CheckMenuItem onTop = new CheckMenuItem("Keep on top");
        onTop.setOnAction(event -> stage.setAlwaysOnTop(onTop.isSelected()));
        CheckMenuItem handwriting = new CheckMenuItem("Handwritten font");
        String family = Font.getFamilies().stream()
                .filter(name -> name.equals("Chilanka") || name.equals("Patrick Hand")
                        || name.equals("Comic Sans MS"))
                .findFirst().orElse(null);
        handwriting.setDisable(family == null);
        handwriting.setOnAction(event -> editor.setFont(
                Font.font(handwriting.isSelected() ? family : "DejaVu Sans", 16)));
        Menu themes = new Menu("Timer theme");
        MenuItem none = new MenuItem("None (animated themes planned for v1.2)");
        none.setDisable(true);
        themes.getItems().add(none);
        menu.getItems().addAll(colors, onTop, handwriting, themes,
                new SeparatorMenuItem(), managerItem, deleteItem, quitItem);
        dragBar.getStyleClass().add("drag-bar");
        dragBar.setCursor(Cursor.MOVE);
        dragBar.getChildren().addAll(addButton, spacer, reminderButton, menu);
        resizeGrip.getStyleClass().add("resize-grip");
        resizeGrip.setCursor(Cursor.SE_RESIZE);
        resizeGrip.setMinSize(18, 18);
        resizeGrip.setPrefSize(18, 18);
        resizeGrip.setMaxSize(18, 18);
        resizeGrip.setAccessibleText("Resize note");
        status.getStyleClass().add("note-status");
        status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox footer = new HBox(status, resizeGrip);
        footer.setPadding(new Insets(0, 8, 6, 12));
        paper.getStyleClass().add("paper");
        paper.setTop(dragBar);
        paper.setCenter(editor);
        paper.setBottom(footer);
        StackPane root = new StackPane(paper);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(root, 320, 300, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/org/stickynotefx/notes.css").toExternalForm());
        stage.setScene(scene);
    }

    private static Button iconButton(String symbol, String description) {
        Button button = new Button(symbol);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    public void setColor(String color) {
        paper.setStyle("-fx-background-color: " + color + ";");
    }

    public void setReminder(Long timestamp) {
        reminderButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), timestamp != null);
        String description = timestamp == null ? "Set reminder" : "Reminder: "
                + java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"));
        reminderButton.setTooltip(new Tooltip(description));
        reminderButton.setAccessibleText(description);
    }

    public Stage stage() { return stage; }
    public TextArea editor() { return editor; }
    public HBox dragBar() { return dragBar; }
    public Region resizeGrip() { return resizeGrip; }
    public Button addButton() { return addButton; }
    public Button reminderButton() { return reminderButton; }
    public MenuItem deleteItem() { return deleteItem; }
    public MenuItem managerItem() { return managerItem; }
    public MenuItem quitItem() { return quitItem; }
    public void onColor(Consumer<String> callback) { onColor = callback; }
    public void setStatus(String message) { status.setText(message); }
}