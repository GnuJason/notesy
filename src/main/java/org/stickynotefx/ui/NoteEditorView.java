package org.stickynotefx.ui;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import org.stickynotefx.ui.playful.PlayfulMode;
import org.stickynotefx.ui.playful.PlayfulReminderOverlay;
import org.stickynotefx.ui.playful.PlayfulSettings;

public final class NoteEditorView extends BorderPane {
    private final TextArea editor = new TextArea();
    private final Label status = new Label();
    private final Region resizeGrip = new Region();
    private final StackPane playfulSlot = new StackPane();
    private PlayfulReminderOverlay overlay;
    private PlayfulSettings settings = PlayfulSettings.DEFAULT;
    private Long reminderTimestamp;
    private boolean expired;
    private boolean active;

    public NoteEditorView() {
        editor.setWrapText(true);
        editor.setAccessibleText("Note text");
        editor.getStyleClass().add("note-editor");
        editor.setFont(Font.font("DejaVu Sans", 16));
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
        setCenter(editor);
        setBottom(footer);
    }

    public TextArea editor() { return editor; }
    public Region resizeGrip() { return resizeGrip; }
    public PlayfulReminderOverlay overlay() { return overlay; }
    public PlayfulSettings settings() { return settings; }
    public boolean expired() { return expired; }
    public void setStatus(String text) { status.setText(text); }

    public void setActive(boolean value) {
        active = value;
        visual(() -> {
            if (active) refresh();
            else if (overlay != null) overlay.close();
        });
    }

    public void setReminder(Long timestamp) {
        reminderTimestamp = timestamp;
        expired = false;
        visual(() -> {
            if (overlay != null && active) overlay.scheduled(timestamp);
        });
    }

    public void setSettings(PlayfulSettings value) {
        settings = value;
        visual(() -> {
            disposePlayful();
            if (settings.activeMode() == PlayfulMode.NONE) return;
            overlay = PlayfulReminderOverlay.create(settings.activeMode());
            playfulSlot.getChildren().setAll(overlay);
            playfulSlot.visibleProperty().bind(overlay.visibleProperty());
            if (active) refresh();
        });
    }

    public void reminderFired() {
        expired = true;
        visual(() -> {
            if (overlay != null && active) overlay.fired();
        });
    }

    public void setExpired(boolean value) {
        expired = value;
        visual(this::refresh);
    }

    private void refresh() {
        if (overlay == null || !active) return;
        if (expired && reminderTimestamp == null) overlay.showExpired();
        else overlay.scheduled(reminderTimestamp);
    }

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
}
