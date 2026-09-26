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
    private final CheckBox enabled = new CheckBox("Enable playful reminder visuals");
    private final ComboBox<PlayfulMode> mode = new ComboBox<>();
    private final Label error = new Label();
    private PlayfulSettings saved;
    private boolean updating;

    public PlayfulPreferencesDialog(Window owner, PlayfulSettings initial, Consumer<PlayfulSettings> save) {
        saved = initial;
        initOwner(owner);
        setTitle("Preferences");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
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
    }

    private void restore() {
        updating = true;
        enabled.setSelected(saved.playfulModeEnabled());
        mode.setValue(saved.playfulModeType() == PlayfulMode.NONE ? null : saved.playfulModeType());
        updating = false;
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