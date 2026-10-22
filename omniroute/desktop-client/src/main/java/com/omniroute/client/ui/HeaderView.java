package com.omniroute.client.ui;

import com.omniroute.client.api.GatewayClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

public class HeaderView extends VBox {

    private final GatewayClient gatewayClient;
    private final Circle statusDot;
    private final Label statusText;
    private final TextField urlField;
    private final Label totalOrdersMetric;
    private final Label completedOrdersMetric;
    private final Label activeTrucksMetric;
    private final Label totalSkusMetric;

    public HeaderView(GatewayClient gatewayClient, Consumer<String> onUrlChanged) {
        this.gatewayClient = gatewayClient;

        setStyle("-fx-background-color: #0d1321; -fx-border-color: #1e293b; -fx-border-width: 0 0 1px 0; -fx-padding: 12px 20px;");
        setSpacing(10);

        // Top Row: Title, Gateway Config, Status
        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Branding
        VBox titleBox = new VBox(2);
        Label brand = new Label("OMNIROUTE");
        brand.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #38bdf8; -fx-letter-spacing: 1px;");
        Label subtitle = new Label("EVENT-DRIVEN SAGA & FLEET ORCHESTRATION");
        subtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b; -fx-font-weight: bold;");
        titleBox.getChildren().addAll(brand, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Gateway URL Input
        HBox urlBox = new HBox(8);
        urlBox.setAlignment(Pos.CENTER);
        Label urlLabel = new Label("Gateway:");
        urlLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        urlField = new TextField(gatewayClient.getBaseUrl());
        urlField.setPrefWidth(220);

        Button applyBtn = new Button("Connect");
        applyBtn.getStyleClass().add("btn-outline");
        applyBtn.setOnAction(e -> {
            String newUrl = urlField.getText().trim();
            if (!newUrl.isEmpty()) {
                gatewayClient.setBaseUrl(newUrl);
                if (onUrlChanged != null) {
                    onUrlChanged.accept(newUrl);
                }
            }
        });
        urlBox.getChildren().addAll(urlLabel, urlField, applyBtn);

        // Live Health Status Badge
        HBox statusPill = new HBox(6);
        statusPill.setAlignment(Pos.CENTER);
        statusPill.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 20px; -fx-padding: 5px 12px; -fx-border-color: #334155; -fx-border-radius: 20px;");
        statusDot = new Circle(4, Color.web("#64748b"));
        statusText = new Label("CONNECTING");
        statusText.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        statusPill.getChildren().addAll(statusDot, statusText);

        topRow.getChildren().addAll(titleBox, spacer, urlBox, statusPill);

        // Bottom Row: Summary Metrics
        HBox metricsRow = new HBox(12);
        metricsRow.setAlignment(Pos.CENTER_LEFT);

        totalOrdersMetric = createMetricBox("TOTAL ORDERS", "0", "#38bdf8", metricsRow);
        completedOrdersMetric = createMetricBox("COMPLETED ORDERS", "0", "#34d399", metricsRow);
        activeTrucksMetric = createMetricBox("FLEET TRUCKS", "0", "#818cf8", metricsRow);
        totalSkusMetric = createMetricBox("TRACKED SKUS", "0", "#fbbf24", metricsRow);

        getChildren().addAll(topRow, metricsRow);
    }

    private Label createMetricBox(String label, String initialValue, String accentColor, HBox parent) {
        VBox box = new VBox(2);
        box.getStyleClass().add("metric-box");
        box.setPrefWidth(150);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");

        Label val = new Label(initialValue);
        val.getStyleClass().add("metric-value");
        val.setStyle("-fx-text-fill: " + accentColor + ";");

        box.getChildren().addAll(lbl, val);
        parent.getChildren().add(box);
        return val;
    }

    public void updateConnectionStatus(boolean isOnline) {
        Platform.runLater(() -> {
            if (isOnline) {
                statusDot.setFill(Color.web("#10b981"));
                statusText.setText("ONLINE");
                statusText.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #34d399;");
            } else {
                statusDot.setFill(Color.web("#ef4444"));
                statusText.setText("DISCONNECTED");
                statusText.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #f87171;");
            }
        });
    }

    public void updateMetrics(int totalOrders, int completedOrders, int truckCount, int skuCount) {
        Platform.runLater(() -> {
            totalOrdersMetric.setText(String.valueOf(totalOrders));
            completedOrdersMetric.setText(String.valueOf(completedOrders));
            activeTrucksMetric.setText(String.valueOf(truckCount));
            totalSkusMetric.setText(String.valueOf(skuCount));
        });
    }
}
