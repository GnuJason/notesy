package org.stickynotefx.ui;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
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
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.stickynotefx.ui.playful.PlayfulMode;
import org.stickynotefx.ui.playful.PlayfulReminderOverlay;
import org.stickynotefx.ui.playful.PlayfulSettings;

import java.util.function.Consumer;

public final class StickyNoteView {
    private final Stage stage = new Stage(javafx.stage.StageStyle.UNDECORATED);
    private final BorderPane paper = new BorderPane();
    private final TextArea editor = new TextArea();
    private final HBox dragBar = new HBox(2);
    private final Region resizeGrip = new Region();
    private final Button addButton = iconButton("+", "New note");
    private final Button notesButton = iconButton("", "Browse notes");
    private final Button reminderButton = iconButton("\u23f1", "Set reminder");
    private final javafx.scene.control.ToggleButton pinButton = new javafx.scene.control.ToggleButton();
    private final Button expandButton = iconButton("", "Expand workspace (Notebook mode is not available yet)");
    private final Button minimizeButton = iconButton("", "Minimize note");
    private final Button closeButton = iconButton("\u00d7", "Close note");
    private final MenuButton optionsMenu = new MenuButton();
    private final MenuItem deleteItem = new MenuItem("Delete note");
    private final MenuItem managerItem = new MenuItem("Browse notes");
    private final MenuItem quitItem = new MenuItem("Quit Notesy");
    private final MenuItem preferencesItem = new MenuItem("Preferences...");
    private final CheckMenuItem snapToGrid = new CheckMenuItem("Snap to grid (this session)");
    private final MenuItem clearReminder = new MenuItem("Clear reminder / visual");
    private final javafx.scene.control.Menu snooze = new javafx.scene.control.Menu("Snooze");
    private final java.util.Map<PlayfulMode, javafx.scene.control.RadioMenuItem> modeItems = new java.util.EnumMap<>(PlayfulMode.class);
    private Consumer<PlayfulSettings> onPlayful = settings -> { };
    private java.util.function.IntConsumer onSnooze = minutes -> { };
    private final Label status = new Label();
    private final StackPane playfulSlot = new StackPane();
    private PlayfulReminderOverlay overlay;
    private PlayfulSettings playfulSettings = PlayfulSettings.DEFAULT;
    private Long reminderTimestamp;
    private boolean playfulExpired;
    private Consumer<String> onColor = value -> { };

    public StickyNoteView() {
        stage.setTitle("Notesy");
        org.stickynotefx.theme.WindowIcons.apply(stage);
        stage.setMinWidth(260);
        stage.setMinHeight(220);
        editor.setWrapText(true);
        editor.setAccessibleText("Note text");
        editor.getStyleClass().add("note-editor");
        editor.setFont(Font.font("DejaVu Sans", 16));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMinWidth(36);
        spacer.setId("note-drag-region");
        notesButton.setId("browse-notes");
        notesButton.setGraphic(controlGraphic("M 0 0 H 3 V 3 H 0 Z M 5 0 H 14 V 3 H 5 Z "
                + "M 0 5 H 3 V 8 H 0 Z M 5 5 H 14 V 8 H 5 Z "
                + "M 0 10 H 3 V 13 H 0 Z M 5 10 H 14 V 13 H 5 Z"));
        expandButton.setId("expand-note");
        expandButton.setGraphic(controlGraphic("M 0 5 V 0 H 5 V 2 H 2 V 5 Z "
                + "M 9 0 H 14 V 5 H 12 V 2 H 9 Z "
                + "M 14 9 V 14 H 9 V 12 H 12 V 9 Z "
                + "M 5 14 H 0 V 9 H 2 V 12 H 5 Z"));
        expandButton.setDisable(true);
        StackPane expandControl = new StackPane(expandButton);
        Tooltip.install(expandControl, expandButton.getTooltip());
        expandControl.setOnMousePressed(javafx.event.Event::consume);
        expandControl.setOnMouseDragged(javafx.event.Event::consume);
        minimizeButton.setId("minimize-note");
        minimizeButton.setGraphic(controlGraphic("M 0 0 H 12 V 2 H 0 Z"));
        optionsMenu.setTooltip(new Tooltip("Note options"));
        optionsMenu.setAccessibleText("Note options");
        optionsMenu.setGraphic(controlGraphic("M 0 0 H 3 V 3 H 0 Z "
                + "M 5 0 H 8 V 3 H 5 Z M 10 0 H 13 V 3 H 10 Z"));
        optionsMenu.setId("note-options");
        optionsMenu.getStyleClass().add("icon-button");
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
        pinButton.getStyleClass().add("icon-button");
        pinButton.setId("pin-note");
        pinButton.setGraphic(pinGraphic());
        Tooltip pinTooltip = new Tooltip();
        pinTooltip.textProperty().bind(javafx.beans.binding.Bindings.when(pinButton.selectedProperty())
            .then("Unpin note").otherwise("Pin note (keep on top)"));
        pinButton.setTooltip(pinTooltip);
        pinButton.accessibleTextProperty().bind(pinTooltip.textProperty());
        pinButton.selectedProperty().addListener((observable, before, selected) -> {
            stage.setAlwaysOnTop(selected);
            pinButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), selected);
        });
        stage.alwaysOnTopProperty().addListener((observable, before, selected) -> pinButton.setSelected(selected));
        closeButton.setId("close-note");
        closeButton.setOnAction(event -> stage.fireEvent(new javafx.stage.WindowEvent(stage,
            javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        CheckMenuItem handwriting = new CheckMenuItem("Handwritten font");
        String family = Font.getFamilies().stream()
                .filter(name -> name.equals("Chilanka") || name.equals("Patrick Hand")
                        || name.equals("Comic Sans MS"))
                .findFirst().orElse(null);
        handwriting.setDisable(family == null);
        handwriting.setOnAction(event -> editor.setFont(
                Font.font(handwriting.isSelected() ? family : "DejaVu Sans", 16)));
        var graphics = new javafx.scene.control.Menu("Reminder visual");
        var group = new javafx.scene.control.ToggleGroup();
        for (PlayfulMode mode : PlayfulMode.values()) {
            var item = new javafx.scene.control.RadioMenuItem(mode.toString());
            item.setToggleGroup(group);
            item.setSelected(mode == PlayfulMode.NONE);
            item.setOnAction(event -> onPlayful.accept(new PlayfulSettings(mode != PlayfulMode.NONE, mode)));
            modeItems.put(mode, item);
            graphics.getItems().add(item);
        }
        for (int minutes : new int[]{5, 10, 30}) {
            MenuItem item = new MenuItem(minutes + " minutes");
            item.setOnAction(event -> onSnooze.accept(minutes));
            snooze.getItems().add(item);
        }
        snooze.setDisable(true);
        clearReminder.setDisable(true);
        optionsMenu.getItems().addAll(colors, handwriting, snapToGrid, graphics, snooze, clearReminder, preferencesItem,
                new SeparatorMenuItem(), managerItem, deleteItem, quitItem);
        dragBar.getStyleClass().add("drag-bar");
        dragBar.setCursor(Cursor.MOVE);
        dragBar.getChildren().addAll(addButton, notesButton, spacer, pinButton, reminderButton, optionsMenu,
                expandControl, minimizeButton, closeButton);
        resizeGrip.getStyleClass().add("resize-grip");
        resizeGrip.setCursor(Cursor.SE_RESIZE);
        resizeGrip.setMinSize(18, 18);
        resizeGrip.setPrefSize(18, 18);
        resizeGrip.setMaxSize(18, 18);
        resizeGrip.setAccessibleText("Resize note");
        status.getStyleClass().add("note-status");
        status.setMinWidth(0);
        status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        playfulSlot.setVisible(false);
        playfulSlot.managedProperty().bind(playfulSlot.visibleProperty());
        playfulSlot.setMouseTransparent(true);
        HBox footer = new HBox(status, playfulSlot, resizeGrip);
        footer.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        footer.setPadding(new Insets(0, 8, 6, 12));
        paper.getStyleClass().add("paper");
        paper.setTop(dragBar);
        paper.setCenter(editor);
        paper.setBottom(footer);
        StackPane root = new StackPane(paper);
        root.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(root, 320, 300, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/org/stickynotefx/notes.css").toExternalForm());
        stage.setScene(scene);
        setTheme(org.stickynotefx.theme.ThemeRegistry.Theme.LIGHT);
        javafx.beans.InvalidationListener visibilityChanged = observable -> visual(() -> {
            if (overlay == null) return;
            if (visualsVisible()) refreshPlayful();
            else overlay.close();
        });
        stage.showingProperty().addListener(visibilityChanged);
        stage.iconifiedProperty().addListener(visibilityChanged);
    }

    private static Button iconButton(String symbol, String description) {
        Button button = new Button(symbol);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    private static SVGPath pinGraphic() {
        SVGPath pin = new SVGPath();
        pin.setContent("M 5 2 L 15 2 L 15 5 L 12 8 L 12 11 L 15 14 L 15 16 L 10 16 L 10 23 L 9 23 L 9 16 L 4 16 L 4 14 L 7 11 L 7 8 L 5 5 Z");
        pin.getStyleClass().add("note-pin-icon");
        return pin;
    }

    private static SVGPath controlGraphic(String content) {
        SVGPath graphic = new SVGPath();
        graphic.setContent(content);
        graphic.getStyleClass().add("note-control-icon");
        return graphic;
    }

    private boolean visualsVisible() {
        return stage.isShowing() && !stage.isIconified();
    }

    public void setColor(String color) {
        paper.setStyle("-notesy-note-color: " + color + ";");
    }

    public void setReminder(Long timestamp) {
        reminderTimestamp = timestamp;
        playfulExpired = false;
        refreshReminderActions();
        visual(() -> {
            if (overlay != null && visualsVisible()) overlay.scheduled(timestamp);
        });
        reminderButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), timestamp != null);
        String description = timestamp == null ? "Set reminder" : "Reminder: "
                + java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm z (XXX)"));
        reminderButton.setTooltip(new Tooltip(description));
        reminderButton.setAccessibleText(description);
    }

    public void setPlayfulSettings(PlayfulSettings settings) {
        playfulSettings = settings;
        modeItems.get(settings.activeMode()).setSelected(true);
        visual(() -> {
            disposePlayful();
            if (settings.activeMode() == PlayfulMode.NONE) return;
            overlay = PlayfulReminderOverlay.create(settings.activeMode());
            playfulSlot.getChildren().setAll(overlay);
            playfulSlot.visibleProperty().bind(overlay.visibleProperty());
            if (visualsVisible()) refreshPlayful();
        });
    }

    public void reminderFired() {
        playfulExpired = true;
        refreshReminderActions();
        visual(() -> {
            if (overlay != null && visualsVisible()) overlay.fired();
        });
    }

    public void setPlayfulExpired(boolean expired) {
        playfulExpired = expired;
        refreshReminderActions();
        visual(this::refreshPlayful);
    }

    private void refreshPlayful() {
        if (overlay == null || !visualsVisible()) return;
        if (playfulExpired && reminderTimestamp == null) overlay.showExpired();
        else overlay.scheduled(reminderTimestamp);
    }

    private void refreshReminderActions() {
        snooze.setDisable(reminderTimestamp == null && !playfulExpired);
        clearReminder.setDisable(snooze.isDisable());
    }

    public void setTheme(org.stickynotefx.theme.ThemeRegistry.Theme theme) {
        org.stickynotefx.theme.ThemeRegistry.apply(stage.getScene().getRoot(), theme);
    }

    public boolean playfulExpired() { return playfulExpired; }
    public CheckMenuItem snapToGridItem() { return snapToGrid; }
    public MenuItem clearReminderItem() { return clearReminder; }
    public javafx.scene.control.Menu snoozeMenu() { return snooze; }
    public void onPlayful(Consumer<PlayfulSettings> action) { onPlayful = action; }
    public void onSnooze(java.util.function.IntConsumer action) { onSnooze = action; }

    private void visual(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            disposePlayful();
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Playful visual unavailable; keeping standard reminder UI", failure);
        }
    }

    public void disposePlayful() {
        if (overlay != null) overlay.close();
        overlay = null;
        playfulSlot.visibleProperty().unbind();
        playfulSlot.setVisible(false);
        playfulSlot.getChildren().clear();
    }

    public PlayfulSettings playfulSettings() { return playfulSettings; }
    public PlayfulReminderOverlay playfulOverlay() { return overlay; }
    public MenuItem preferencesItem() { return preferencesItem; }

    public Stage stage() { return stage; }
    public TextArea editor() { return editor; }
    public HBox dragBar() { return dragBar; }
    public Region resizeGrip() { return resizeGrip; }
    public Button addButton() { return addButton; }
    public Button notesButton() { return notesButton; }
    public Button expandButton() { return expandButton; }
    public Button minimizeButton() { return minimizeButton; }
    public Button reminderButton() { return reminderButton; }
    public javafx.scene.control.ToggleButton pinButton() { return pinButton; }
    public Button closeButton() { return closeButton; }
    public MenuButton optionsMenu() { return optionsMenu; }
    public MenuItem deleteItem() { return deleteItem; }
    public MenuItem managerItem() { return managerItem; }
    public MenuItem quitItem() { return quitItem; }
    public void onColor(Consumer<String> callback) { onColor = callback; }
    public void setStatus(String message) { status.setText(message); }
}