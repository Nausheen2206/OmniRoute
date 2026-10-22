package com.omniroute.client.ui;

import com.omniroute.client.api.GatewayClient;
import com.omniroute.client.model.CreateOrderRequest;
import com.omniroute.client.model.OrderDto;
import com.omniroute.client.model.OrderItemDto;
import com.omniroute.client.model.SagaStepDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OrdersView extends BorderPane {

    private final GatewayClient gatewayClient;
    private final Consumer<String> statusReporter;
    private final Consumer<Integer> orderCountListener;

    private final TableView<OrderDto> orderTable = new TableView<>();
    private final ObservableList<OrderDto> orderData = FXCollections.observableArrayList();

    // Saga History Panel
    private final Label selectedOrderIdLabel = new Label("Select an order to inspect");
    private final Label selectedOrderStatusBadge = new Label("");
    private final Label selectedOrderReason = new Label("");
    private final TableView<SagaStepDto> sagaTable = new TableView<>();
    private final ObservableList<SagaStepDto> sagaData = FXCollections.observableArrayList();

    // Form inputs
    private final TextField customerField = new TextField("CUST-1001");
    private final ComboBox<String> skuCombo = new ComboBox<>();
    private final Spinner<Integer> qtySpinner = new Spinner<>(1, 100, 2);
    private final TextField amountField = new TextField("120.00");
    private final Button placeOrderBtn = new Button("Place Order");
    private final Button place20Btn = new Button("⚡ Place 20 Random Orders");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public OrdersView(GatewayClient gatewayClient, Consumer<String> statusReporter, Consumer<Integer> orderCountListener) {
        this.gatewayClient = gatewayClient;
        this.statusReporter = statusReporter;
        this.orderCountListener = orderCountListener;

        setPadding(new Insets(16));
        setStyle("-fx-background-color: #0b0f19;");

        // Top: Order Placement Form Card
        VBox topCard = createOrderFormCard();
        setTop(topCard);

        // Center: SplitPane with Orders Table on left, Saga History on right
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent; -fx-padding: 12px 0 0 0;");

        VBox tableBox = createOrderTableBox();
        VBox sagaBox = createSagaHistoryBox();

        splitPane.getItems().addAll(tableBox, sagaBox);
        splitPane.setDividerPositions(0.65);
        setCenter(splitPane);

        setupTableSelection();
    }

    private VBox createOrderFormCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-panel");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Create New Order");
        title.getStyleClass().add("card-header");
        Label subtitle = new Label("Trigger distributed Saga workflow via Gateway");
        subtitle.getStyleClass().add("card-subtext");
        header.getChildren().addAll(title, subtitle);

        HBox formRow = new HBox(12);
        formRow.setAlignment(Pos.BOTTOM_LEFT);

        // Customer ID
        VBox custBox = new VBox(4);
        Label custLbl = new Label("Customer ID");
        custLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        customerField.setPrefWidth(130);
        custBox.getChildren().addAll(custLbl, customerField);

        // SKU
        VBox skuBox = new VBox(4);
        Label skuLbl = new Label("Product SKU");
        skuLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        skuCombo.getItems().addAll("SKU-001", "SKU-002", "SKU-003", "SKU-004");
        skuCombo.setValue("SKU-001");
        skuCombo.setPrefWidth(130);
        skuBox.getChildren().addAll(skuLbl, skuCombo);

        // Quantity
        VBox qtyBox = new VBox(4);
        Label qtyLbl = new Label("Quantity");
        qtyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        qtySpinner.setPrefWidth(90);
        qtySpinner.setEditable(true);
        qtyBox.getChildren().addAll(qtyLbl, qtySpinner);

        // Amount
        VBox amountBox = new VBox(4);
        Label amtLbl = new Label("Total Amount ($)");
        amtLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        amountField.setPrefWidth(110);
        amountBox.getChildren().addAll(amtLbl, amountField);

        // Action Buttons
        placeOrderBtn.getStyleClass().add("btn-emerald");
        placeOrderBtn.setOnAction(e -> handlePlaceSingleOrder());

        Button forceFailBtn = new Button("Set $13.13 (Force Fail)");
        forceFailBtn.getStyleClass().add("btn-outline");
        forceFailBtn.setOnAction(e -> amountField.setText("13.13"));

        place20Btn.getStyleClass().add("btn-purple");
        place20Btn.setOnAction(e -> handlePlace20RandomOrders());

        formRow.getChildren().addAll(custBox, skuBox, qtyBox, amountBox, placeOrderBtn, forceFailBtn, place20Btn);
        card.getChildren().addAll(header, formRow);
        return card;
    }

    private VBox createOrderTableBox() {
        VBox box = new VBox(8);
        Label title = new Label("Live Orders (Auto-refresh: 1s)");
        title.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-font-size: 13px;");

        orderTable.setItems(orderData);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Column: Order ID
        TableColumn<OrderDto, String> idCol = new TableColumn<>("Order ID");
        idCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().orderId() != null && cell.getValue().orderId().length() > 8
                        ? cell.getValue().orderId().substring(0, 8) + "..."
                        : cell.getValue().orderId()
        ));
        idCol.setPrefWidth(100);

        // Column: Customer
        TableColumn<OrderDto, String> custCol = new TableColumn<>("Customer");
        custCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().customerId()));
        custCol.setPrefWidth(90);

        // Column: Items
        TableColumn<OrderDto, String> itemsCol = new TableColumn<>("Items");
        itemsCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getItemsSummary()));
        itemsCol.setPrefWidth(140);

        // Column: Total Amount
        TableColumn<OrderDto, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().totalAmount() != null ? "$" + cell.getValue().totalAmount().setScale(2, RoundingMode.HALF_UP) : "$0.00"
        ));
        amtCol.setPrefWidth(80);

        // Column: Status with Color Badges
        TableColumn<OrderDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().add("status-badge");
                    switch (status.toUpperCase()) {
                        case "COMPLETED":
                            badge.getStyleClass().add("status-completed");
                            break;
                        case "FAILED":
                            badge.getStyleClass().add("status-failed");
                            break;
                        case "COMPENSATING":
                            badge.getStyleClass().add("status-compensating");
                            break;
                        default:
                            badge.getStyleClass().add("status-pending");
                            break;
                    }
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });
        statusCol.setPrefWidth(130);

        // Column: Created At
        TableColumn<OrderDto, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().createdAt() != null ? TIME_FMT.format(cell.getValue().createdAt()) : "-"
        ));
        timeCol.setPrefWidth(70);

        orderTable.getColumns().addAll(idCol, custCol, itemsCol, amtCol, statusCol, timeCol);
        VBox.setVgrow(orderTable, Priority.ALWAYS);

        box.getChildren().addAll(title, orderTable);
        return box;
    }

    private VBox createSagaHistoryBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card-panel");
        box.setPadding(new Insets(12));

        Label header = new Label("Saga Execution Audit");
        header.getStyleClass().add("card-header");

        selectedOrderIdLabel.setStyle("-fx-font-family: monospace; -fx-text-fill: #38bdf8; -fx-font-weight: bold;");
        selectedOrderStatusBadge.getStyleClass().addAll("status-badge", "status-pending");
        selectedOrderStatusBadge.setVisible(false);

        selectedOrderReason.setStyle("-fx-text-fill: #f87171; -fx-font-size: 11px;");
        selectedOrderReason.setWrapText(true);

        HBox topInfo = new HBox(8);
        topInfo.setAlignment(Pos.CENTER_LEFT);
        topInfo.getChildren().addAll(selectedOrderIdLabel, selectedOrderStatusBadge);

        // Saga Steps Table
        sagaTable.setItems(sagaData);
        sagaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SagaStepDto, String> stepCol = new TableColumn<>("Saga Step");
        stepCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().step()));
        stepCol.setPrefWidth(140);

        TableColumn<SagaStepDto, String> eventCol = new TableColumn<>("Event ID");
        eventCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().eventId() != null && cell.getValue().eventId().length() > 8
                        ? cell.getValue().eventId().substring(0, 8) + "..."
                        : cell.getValue().eventId()
        ));
        eventCol.setPrefWidth(90);

        TableColumn<SagaStepDto, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().createdAt() != null ? TIME_FMT.format(cell.getValue().createdAt()) : "-"
        ));
        timeCol.setPrefWidth(70);

        sagaTable.getColumns().addAll(stepCol, eventCol, timeCol);
        VBox.setVgrow(sagaTable, Priority.ALWAYS);

        box.getChildren().addAll(header, topInfo, selectedOrderReason, sagaTable);
        return box;
    }

    private void setupTableSelection() {
        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                inspectOrder(newVal.orderId());
            }
        });
    }

    private void inspectOrder(String orderId) {
        if (orderId == null) return;
        gatewayClient.fetchOrderDetails(orderId)
                .thenAccept(details -> Platform.runLater(() -> {
                    selectedOrderIdLabel.setText(details.orderId());
                    selectedOrderStatusBadge.setText(details.status());
                    selectedOrderStatusBadge.setVisible(true);
                    selectedOrderStatusBadge.getStyleClass().removeAll("status-completed", "status-failed", "status-pending", "status-compensating");
                    if ("COMPLETED".equalsIgnoreCase(details.status())) {
                        selectedOrderStatusBadge.getStyleClass().add("status-completed");
                    } else if ("FAILED".equalsIgnoreCase(details.status())) {
                        selectedOrderStatusBadge.getStyleClass().add("status-failed");
                    } else if ("COMPENSATING".equalsIgnoreCase(details.status())) {
                        selectedOrderStatusBadge.getStyleClass().add("status-compensating");
                    } else {
                        selectedOrderStatusBadge.getStyleClass().add("status-pending");
                    }

                    if (details.failureReason() != null && !details.failureReason().isEmpty()) {
                        selectedOrderReason.setText("Failure reason: " + details.failureReason());
                        selectedOrderReason.setVisible(true);
                    } else {
                        selectedOrderReason.setText("");
                        selectedOrderReason.setVisible(false);
                    }

                    sagaData.setAll(details.sagaHistory());
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> statusReporter.accept("Failed to load saga history: " + ex.getMessage()));
                    return null;
                });
    }

    private void handlePlaceSingleOrder() {
        String cust = customerField.getText().trim();
        String sku = skuCombo.getValue();
        int qty = qtySpinner.getValue();
        BigDecimal amt;
        try {
            amt = new BigDecimal(amountField.getText().trim());
        } catch (Exception e) {
            statusReporter.accept("Invalid amount value: " + amountField.getText());
            return;
        }

        placeOrderBtn.setDisable(true);
        CreateOrderRequest request = new CreateOrderRequest(cust, List.of(new OrderItemDto(sku, qty)), amt);

        gatewayClient.placeOrder(request)
                .thenAccept(res -> Platform.runLater(() -> {
                    placeOrderBtn.setDisable(false);
                    statusReporter.accept("Order placed successfully! Initializing Saga...");
                    refreshOrders();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        placeOrderBtn.setDisable(false);
                        statusReporter.accept("Failed to place order: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void handlePlace20RandomOrders() {
        place20Btn.setDisable(true);
        statusReporter.accept("Submitting 20 demo orders asynchronously...");

        String[] skus = {"SKU-001", "SKU-002", "SKU-003", "SKU-004"};
        Random rng = new Random();

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String custId = "DEMO-USER-" + (100 + i);
            String sku = skus[rng.nextInt(skus.length)];
            int qty = 1 + rng.nextInt(4);

            // 10% chance to inject $13.13 for forced payment failure demo
            BigDecimal amount;
            if (i % 7 == 0) {
                amount = new BigDecimal("13.13");
            } else {
                amount = BigDecimal.valueOf(25 + rng.nextInt(150)).setScale(2, RoundingMode.HALF_UP);
            }

            CreateOrderRequest req = new CreateOrderRequest(custId, List.of(new OrderItemDto(sku, qty)), amount);
            futures.add(gatewayClient.placeOrder(req));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((res, ex) -> Platform.runLater(() -> {
                    place20Btn.setDisable(false);
                    if (ex != null) {
                        statusReporter.accept("20 orders submission completed with some errors: " + ex.getMessage());
                    } else {
                        statusReporter.accept("Successfully dispatched 20 random orders! Monitoring live saga state...");
                    }
                    refreshOrders();
                }));
    }

    public void refreshOrders() {
        gatewayClient.fetchOrders()
                .thenAccept(orders -> Platform.runLater(() -> {
                    OrderDto selected = orderTable.getSelectionModel().getSelectedItem();
                    orderData.setAll(orders);
                    if (orderCountListener != null) {
                        orderCountListener.accept(orders.size());
                    }
                    if (selected != null) {
                        for (OrderDto o : orders) {
                            if (o.orderId().equals(selected.orderId())) {
                                orderTable.getSelectionModel().select(o);
                                break;
                            }
                        }
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> statusReporter.accept("Gateway connection failed: " + ex.getMessage()));
                    return null;
                });
    }

    public List<OrderDto> getCurrentOrders() {
        return new ArrayList<>(orderData);
    }
}
