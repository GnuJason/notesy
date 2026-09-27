package org.stickynotefx.ui.playful;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

public final class PlayfulPreferencesDialog extends Dialog<Void> {
    private final CheckBox enabled = new CheckBox("Enable playful visuals by default");
    private final ComboBox<PlayfulMode> mode = new ComboBox<>();
    private final Label error = new Label();
    private PlayfulSettings saved;
    private boolean updating;
    private double dragX;
    private double dragY;

    public PlayfulPreferencesDialog(Window owner, PlayfulSettings initial, Consumer<PlayfulSettings> save) {
        saved = initial;
        initStyle(javafx.stage.StageStyle.UNDECORATED);
        initOwner(owner);
        setTitle("Preferences");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().getStyleClass().add("notesy-preferences");
        Label title = new Label("Preferences");
        title.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.control.Button close = new javafx.scene.control.Button("\u00d7");
        close.setId("close-preferences");
        close.getStyleClass().add("icon-button");
        close.setTooltip(new javafx.scene.control.Tooltip("Close preferences"));
        close.setAccessibleText("Close preferences");
        close.setOnAction(event -> close());
        var header = new javafx.scene.layout.HBox(8, title, close);
        header.getStyleClass().add("preferences-header");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        title.setOnMousePressed(event -> {
            Window window = getDialogPane().getScene().getWindow();
            dragX = event.getScreenX() - window.getX();
            dragY = event.getScreenY() - window.getY();
        });
        title.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            Window window = getDialogPane().getScene().getWindow();
            window.setX(event.getScreenX() - dragX);
            window.setY(event.getScreenY() - dragY);
        });
        getDialogPane().setHeader(header);
        mode.getItems().addAll(PlayfulMode.EGG, PlayfulMode.BOMB, PlayfulMode.TOASTER);
        mode.setPromptText("Mode");
        mode.setAccessibleText("Playful reminder mode");
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.disableProperty().bind(enabled.selectedProperty().not());
        error.setStyle("-fx-text-fill: #b3261e;");
        error.setWrapText(true);
        restore();
        enabled.setOnAction(event -> persist(save));
        mode.setOnAction(event -> persist(save));
        VBox content = new VBox(12, enabled, mode, error);
        content.setPadding(new Insets(16));
        content.setPrefWidth(340);
        getDialogPane().setContent(content);
        org.stickynotefx.theme.ThemeRegistry.apply(getDialogPane(),
                org.stickynotefx.theme.ThemeRegistry.current(owner.getScene().getRoot()));
    }

    public void addThemeSelector(org.stickynotefx.theme.ThemeRegistry.Theme initial,
                                 Consumer<org.stickynotefx.theme.ThemeRegistry.Theme> save) {
        var themes = new ComboBox<org.stickynotefx.theme.ThemeRegistry.Theme>();
        themes.getItems().setAll(org.stickynotefx.theme.ThemeRegistry.Theme.values());
        themes.setValue(initial);
        themes.setAccessibleText("Theme");
        themes.setMaxWidth(Double.MAX_VALUE);
        themes.setOnAction(event -> {
            if (updating) return;
            var before = org.stickynotefx.theme.ThemeRegistry.current(getDialogPane());
            try {
                save.accept(themes.getValue());
                org.stickynotefx.theme.ThemeRegistry.apply(getDialogPane(), themes.getValue());
                error.setText("");
            } catch (RuntimeException failure) {
                error.setText("Could not save theme. Previous theme is still active.");
                updating = true;
                themes.setValue(before);
                updating = false;
            }
        });
        ((VBox) getDialogPane().getContent()).getChildren().addAll(new Label("Theme"), themes);
    }

    private void restore() {
        updating = true;
        enabled.setSelected(saved.playfulModeEnabled());
        mode.setValue(saved.playfulModeType() == PlayfulMode.NONE ? null : saved.playfulModeType());
        updating = false;
    }

    public void addPlacementToggle(boolean initial, Consumer<Boolean> change) {
        CheckBox placement = new CheckBox("Avoid overlap for new notes (this session)");
        placement.setWrapText(true);
        placement.setSelected(initial);
        placement.setOnAction(event -> change.accept(placement.isSelected()));
        ((VBox) getDialogPane().getContent()).getChildren().add(placement);
    }

    private void persist(Consumer<PlayfulSettings> save) {
        if (updating) return;
        PlayfulMode selected = mode.getValue();
        if (selected == null) selected = enabled.isSelected() ? PlayfulMode.EGG : PlayfulMode.NONE;
        PlayfulSettings next = new PlayfulSettings(enabled.isSelected(), selected);
        try {
            save.accept(next);
            saved = next;
            error.setText("");
        } catch (RuntimeException failure) {
            error.setText("Could not save preferences. Previous settings are still active.");
        }
        restore();
    }
}