package com.omniroute.client.ui;

import com.omniroute.client.api.GatewayClient;
import com.omniroute.client.model.CircuitBreakerDto;
import com.omniroute.client.model.PaymentConfigDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ResilienceView extends ScrollPane {

    private final GatewayClient gatewayClient;
    private final Consumer<String> statusReporter;

    // Circuit Breaker UI elements
    private final Label cbStateBadge = new Label("● CLOSED");
    private final Label cbExplanation = new Label("Normal operation: Payment requests routed directly with immediate confirmation.");
    private final Label metricFailureRate = new Label("0.0 %");
    private final Label metricSlowRate = new Label("0.0 %");
    private final Label metricBuffered = new Label("0 / 5");
    private final Label metricFailed = new Label("0");

    // Slider controls
    private final Slider latencySlider = new Slider(0, 5000, 0);
    private final Slider failureSlider = new Slider(0, 100, 0);
    private final Label latencyValueLabel = new Label("0 ms");
    private final Label failureValueLabel = new Label("0 %");
    private final Label actionFeedbackLabel = new Label("Ready to configure payment fault injection.");

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ResilienceView(GatewayClient gatewayClient, Consumer<String> statusReporter) {
        this.gatewayClient = gatewayClient;
        this.statusReporter = statusReporter;

        setFitToWidth(true);
        setStyle("-fx-background-color: #0b0f19; -fx-background: #0b0f19;");

        VBox container = new VBox(20);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #0b0f19;");

        // 1. Header Card
        VBox headerCard = createHeaderCard();

        // 2. Circuit Breaker Telemetry Card
        VBox cbCard = createCircuitBreakerCard();

        // 3. Chaos Controls Card
        VBox chaosCard = createChaosControlsCard();

        // 4. Runbook / Architecture Info Card
        VBox infoCard = createArchitectureInfoCard();

        container.getChildren().addAll(headerCard, cbCard, chaosCard, infoCard);
        setContent(container);

        // Fetch initial config from backend
        fetchInitialConfig();
    }

    private VBox createHeaderCard() {
        VBox box = new VBox(6);
        box.getStyleClass().add("card-panel");

        Label title = new Label("🛡️  Resilience & Chaos Engineering Controls");
        title.getStyleClass().add("card-header");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #38bdf8;");

        Label subtitle = new Label("Real-time Resilience4j Circuit Breaker telemetry, runtime payment latency & failure injection, and automated self-healing.");
        subtitle.getStyleClass().add("card-subtext");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");

        box.getChildren().addAll(title, subtitle);
        return box;
    }

    private VBox createCircuitBreakerCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-panel");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label cardTitle = new Label("Payment Path Circuit Breaker (Resilience4j)");
        cardTitle.getStyleClass().add("card-header");
        cardTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        cbStateBadge.getStyleClass().add("badge-closed");

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("btn-outline");
        refreshBtn.setOnAction(e -> refreshCircuitBreaker());

        topRow.getChildren().addAll(cardTitle, spacer, cbStateBadge, refreshBtn);

        // Explanation text
        cbExplanation.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic;");

        // Metrics Grid (4 chips)
        HBox metricsGrid = new HBox(14);
        metricsGrid.setAlignment(Pos.CENTER_LEFT);

        VBox chip1 = createMetricChip("Failure Rate", metricFailureRate, "Threshold: 50.0%");
        VBox chip2 = createMetricChip("Slow Call Rate", metricSlowRate, "Threshold: 50.0% @ 2s");
        VBox chip3 = createMetricChip("Buffered Calls", metricBuffered, "Sliding Window: 5");
        VBox chip4 = createMetricChip("Failed Calls", metricFailed, "Min Calls: 3");

        HBox.setHgrow(chip1, Priority.ALWAYS);
        HBox.setHgrow(chip2, Priority.ALWAYS);
        HBox.setHgrow(chip3, Priority.ALWAYS);
        HBox.setHgrow(chip4, Priority.ALWAYS);

        metricsGrid.getChildren().addAll(chip1, chip2, chip3, chip4);

        card.getChildren().addAll(topRow, cbExplanation, metricsGrid);
        return card;
    }

    private VBox createMetricChip(String label, Label valueLabel, String subtext) {
        VBox chip = new VBox(4);
        chip.getStyleClass().add("metric-box");
        chip.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label(label);
        titleLbl.getStyleClass().add("metric-label");

        valueLabel.getStyleClass().add("metric-value");

        Label subLbl = new Label(subtext);
        subLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

        chip.getChildren().addAll(titleLbl, valueLabel, subLbl);
        return chip;
    }

    private VBox createChaosControlsCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card-panel");

        Label sectionTitle = new Label("Payment Service Fault Injection");
        sectionTitle.getStyleClass().add("card-header");

        Label sectionDesc = new Label("Adjust sliders and apply to modify payment response behavior dynamically via POST /api/demo/payment-config.");
        sectionDesc.getStyleClass().add("card-subtext");

        // Latency slider
        VBox latencyBox = new VBox(6);
        HBox latencyHeader = new HBox(10);
        latencyHeader.setAlignment(Pos.CENTER_LEFT);
        Label latencyLabel = new Label("Artificial Processing Latency:");
        latencyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e2e8f0;");
        latencyValueLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #38bdf8; -fx-font-size: 14px;");
        latencyHeader.getChildren().addAll(latencyLabel, latencyValueLabel);

        latencySlider.setMajorTickUnit(1000);
        latencySlider.setMinorTickCount(4);
        latencySlider.setBlockIncrement(100);
        latencySlider.setShowTickLabels(true);
        latencySlider.setShowTickMarks(true);
        latencySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            long val = Math.round(newVal.doubleValue() / 50.0) * 50;
            latencyValueLabel.setText(val + " ms");
        });

        Label latencyHelp = new Label("Delays payment execution. Latencies exceeding 2000ms trigger Resilience4j slow-call trips.");
        latencyHelp.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        latencyBox.getChildren().addAll(latencyHeader, latencySlider, latencyHelp);

        // Failure rate slider
        VBox failureBox = new VBox(6);
        HBox failureHeader = new HBox(10);
        failureHeader.setAlignment(Pos.CENTER_LEFT);
        Label failureLabel = new Label("Random Payment Failure Rate:");
        failureLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e2e8f0;");
        failureValueLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #f87171; -fx-font-size: 14px;");
        failureHeader.getChildren().addAll(failureLabel, failureValueLabel);

        failureSlider.setMajorTickUnit(25);
        failureSlider.setMinorTickCount(4);
        failureSlider.setBlockIncrement(5);
        failureSlider.setShowTickLabels(true);
        failureSlider.setShowTickMarks(true);
        failureSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = (int) Math.round(newVal.doubleValue());
            failureValueLabel.setText(val + " %");
        });

        Label failureHelp = new Label("Percentage of payments artificially declined. Rates > 50% cause Circuit Breaker to transition to OPEN.");
        failureHelp.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        failureBox.getChildren().addAll(failureHeader, failureSlider, failureHelp);

        // Action Buttons Row
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Button applyBtn = new Button("⚡ Apply Chaos Config");
        applyBtn.getStyleClass().add("btn-primary");
        applyBtn.setOnAction(e -> applyCurrentConfig());

        Button resetBtn = new Button("↩️ Reset to Defaults (0ms, 0%)");
        resetBtn.getStyleClass().add("btn-secondary");
        resetBtn.setOnAction(e -> {
            latencySlider.setValue(0);
            failureSlider.setValue(0);
            applyConfig(0, 0.0, "Reset to defaults (0ms latency, 0% failure)");
        });

        Button tripBtn = new Button("💥 Trip Circuit Breaker (100% Fail)");
        tripBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8px 14px;");
        tripBtn.setOnAction(e -> {
            failureSlider.setValue(100);
            applyConfig((long) latencySlider.getValue(), 1.0, "Simulated 100% payment outage to trip circuit breaker");
        });

        btnRow.getChildren().addAll(applyBtn, resetBtn, tripBtn);

        // Feedback banner
        actionFeedbackLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #38bdf8;");

        card.getChildren().addAll(sectionTitle, sectionDesc, latencyBox, failureBox, btnRow, actionFeedbackLabel);
        return card;
    }

    private VBox createArchitectureInfoCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-panel");

        Label title = new Label("Resilience Architecture Highlights");
        title.getStyleClass().add("card-header");

        Label bullet1 = new Label("• Non-Fatal Degradation: When Circuit Breaker opens, orders transition to PAYMENT_PENDING instead of failing.");
        bullet1.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label bullet2 = new Label("• Self-Healing Scheduler: PaymentPendingRetryScheduler re-evaluates circuit health every 2s and re-dispatches queued orders.");
        bullet2.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label bullet3 = new Label("• Dead-Letter Topics (DLT): Malformed poison events automatically route to <topic>.DLT after 3 exponential retries.");
        bullet3.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        card.getChildren().addAll(title, bullet1, bullet2, bullet3);
        return card;
    }

    private void applyCurrentConfig() {
        long latency = Math.round(latencySlider.getValue() / 50.0) * 50;
        double failRate = failureSlider.getValue() / 100.0;
        applyConfig(latency, failRate, "Applied chaos configuration");
    }

    private void applyConfig(long latencyMs, double failRate, String actionDesc) {
        gatewayClient.updatePaymentConfig(latencyMs, failRate)
                .thenAccept(res -> Platform.runLater(() -> {
                    String time = LocalTime.now().format(TIME_FORMATTER);
                    actionFeedbackLabel.setText(String.format("[%s] %s: Latency = %d ms, Failure Rate = %.1f%%",
                            time, actionDesc, latencyMs, failRate * 100));
                    actionFeedbackLabel.setStyle("-fx-text-fill: #34d399; -fx-font-size: 12px;");
                    statusReporter.accept(String.format("Payment chaos config updated: %dms, %.0f%% fail", latencyMs, failRate * 100));
                    refreshCircuitBreaker();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        actionFeedbackLabel.setText("Failed applying config: " + ex.getMessage());
                        actionFeedbackLabel.setStyle("-fx-text-fill: #f87171; -fx-font-size: 12px;");
                    });
                    return null;
                });
    }

    private void fetchInitialConfig() {
        gatewayClient.fetchPaymentConfig()
                .thenAccept(config -> Platform.runLater(() -> {
                    if (config != null) {
                        latencySlider.setValue(config.latencyMs());
                        failureSlider.setValue(config.failureRate() * 100.0);
                    }
                }))
                .exceptionally(ex -> null);
    }

    public void refreshCircuitBreaker() {
        gatewayClient.fetchCircuitBreakerStatus()
                .thenAccept(this::updateCircuitBreakerUi)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        cbStateBadge.setText("● UNREACHABLE");
                        cbStateBadge.getStyleClass().setAll("badge-open");
                    });
                    return null;
                });
    }

    private void updateCircuitBreakerUi(CircuitBreakerDto cb) {
        Platform.runLater(() -> {
            String state = cb.state() != null ? cb.state().toUpperCase() : "UNKNOWN";
            cbStateBadge.setText("● " + state);

            cbStateBadge.getStyleClass().removeAll("badge-closed", "badge-open", "badge-half-open");
            switch (state) {
                case "OPEN" -> {
                    cbStateBadge.getStyleClass().add("badge-open");
                    cbExplanation.setText("Circuit OPEN: Payment calls short-circuited! Orders held in PAYMENT_PENDING state.");
                    cbExplanation.setStyle("-fx-text-fill: #f87171; -fx-font-size: 12px; -fx-font-weight: bold;");
                }
                case "HALF_OPEN" -> {
                    cbStateBadge.getStyleClass().add("badge-half-open");
                    cbExplanation.setText("Circuit HALF_OPEN: Trial probe requests allowed to test payment recovery.");
                    cbExplanation.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 12px; -fx-font-weight: bold;");
                }
                default -> {
                    cbStateBadge.getStyleClass().add("badge-closed");
                    cbExplanation.setText("Circuit CLOSED: Normal operation. All payments processing directly.");
                    cbExplanation.setStyle("-fx-text-fill: #34d399; -fx-font-size: 12px;");
                }
            }

            float failRate = cb.failureRate() != null && cb.failureRate() >= 0 ? cb.failureRate() : 0.0f;
            metricFailureRate.setText(String.format("%.1f %%", failRate));

            float slowRate = cb.slowCallRate() != null && cb.slowCallRate() >= 0 ? cb.slowCallRate() : 0.0f;
            metricSlowRate.setText(String.format("%.1f %%", slowRate));

            int buffered = cb.bufferedCalls() != null ? cb.bufferedCalls() : 0;
            metricBuffered.setText(buffered + " / 5");

            int failed = cb.failedCalls() != null ? cb.failedCalls() : 0;
            metricFailed.setText(String.valueOf(failed));
        });
    }
}
