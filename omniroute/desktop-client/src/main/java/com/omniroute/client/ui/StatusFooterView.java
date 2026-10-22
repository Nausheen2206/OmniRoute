package com.omniroute.client.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class StatusFooterView extends HBox {

    private final Label statusMessageLabel = new Label("Ready. Connecting to OmniRoute Gateway...");
    private final Label pollIndicator = new Label("Polling intervals: Orders (1s) | Fleet (500ms) | Inventory (2s)");
    private final Label timeLabel = new Label();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public StatusFooterView() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(16);

        statusMessageLabel.setStyle("-fx-text-fill: #94a3b8;");
        pollIndicator.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        timeLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-family: monospace;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        updateClock();

        getChildren().addAll(statusMessageLabel, spacer, pollIndicator, timeLabel);
    }

    public void setMessage(String message) {
        Platform.runLater(() -> {
            statusMessageLabel.setText(message);
            updateClock();
        });
    }

    public void updateClock() {
        Platform.runLater(() -> timeLabel.setText(LocalTime.now().format(TIME_FMT)));
    }
}
