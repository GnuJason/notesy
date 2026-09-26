package org.stickynotefx.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.stickynotefx.reminder.ReminderPreset;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public final class ReminderDialog extends Dialog<ReminderDialog.Selection> {
    public record Selection(Long timestamp) { }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    public ReminderDialog(Window owner, Long existing) {
        initOwner(owner);
        setTitle("Note reminder");
        ButtonType set = new ButtonType("Set reminder", ButtonBar.ButtonData.OK_DONE);
        ButtonType clear = new ButtonType("Remove reminder", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(set, clear, ButtonType.CANCEL);
        getDialogPane().lookupButton(clear).setDisable(existing == null);
        ComboBox<Object> preset = new ComboBox<>();
        preset.getItems().addAll((Object[]) ReminderPreset.values());
        preset.getItems().add("Custom");
        preset.getSelectionModel().selectFirst();
        ZonedDateTime initial = existing == null ? ZonedDateTime.now().plusHours(1)
                : java.time.Instant.ofEpochMilli(existing).atZone(ZoneId.systemDefault());
        DatePicker date = new DatePicker(initial.toLocalDate());
        date.setEditable(false);
        TextField time = new TextField(initial.format(TIME));
        time.setPrefColumnCount(5);
        Label error = new Label();
        error.setStyle("-fx-text-fill: #b3261e;");
        error.setWrapText(true);
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(12);
        fields.setPadding(new Insets(16));
        fields.addRow(0, new Label("Remind in"), preset);
        fields.addRow(1, new Label("Date"), date);
        fields.addRow(2, new Label("Time (24h)"), time);
        fields.addRow(3, new Label("Time zone"), new Label(ZoneId.systemDefault().toString()));
        fields.add(error, 0, 4, 2, 1);
        date.disableProperty().bind(preset.valueProperty().isNotEqualTo("Custom"));
        time.disableProperty().bind(date.disableProperty());
        if (existing != null) preset.setValue("Custom");
        getDialogPane().setContent(fields);
        Button setButton = (Button) getDialogPane().lookupButton(set);
        final Long[] chosen = new Long[1];
        setButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                ZonedDateTime now = ZonedDateTime.now();
                ZonedDateTime target;
                if (preset.getValue() instanceof ReminderPreset choice) {
                    target = choice.after(now);
                } else {
                    LocalDate day = date.getValue();
                    if (day == null) throw new IllegalArgumentException();
                    var local = day.atTime(LocalTime.parse(time.getText().strip(), TIME));
                    if (ZoneId.systemDefault().getRules().getValidOffsets(local).isEmpty()) {
                        throw new IllegalArgumentException();
                    }
                    target = local.atZone(ZoneId.systemDefault());
                }
                if (!target.isAfter(now)) throw new IllegalArgumentException();
                chosen[0] = target.toInstant().toEpochMilli();
                error.setText("");
            } catch (RuntimeException invalid) {
                error.setText("Enter a valid future date and time (HH:mm).");
                event.consume();
            }
        });
        setResultConverter(button -> button == set ? new Selection(chosen[0])
                : button == clear ? new Selection(null) : null);
    }
}