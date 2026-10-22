package com.omniroute.client.ui;

import com.omniroute.client.api.GatewayClient;
import com.omniroute.client.model.InventoryDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

public class InventoryView extends BorderPane {

    private final GatewayClient gatewayClient;
    private final Consumer<String> statusReporter;
    private final Consumer<Integer> skuCountListener;

    private final TableView<InventoryDto> inventoryTable = new TableView<>();
    private final ObservableList<InventoryDto> inventoryData = FXCollections.observableArrayList();

    public InventoryView(GatewayClient gatewayClient, Consumer<String> statusReporter, Consumer<Integer> skuCountListener) {
        this.gatewayClient = gatewayClient;
        this.statusReporter = statusReporter;
        this.skuCountListener = skuCountListener;

        setPadding(new Insets(16));
        setStyle("-fx-background-color: #0b0f19;");

        VBox contentBox = new VBox(12);
        contentBox.getStyleClass().add("card-panel");
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Warehouse Stock & Inventory (Auto-refresh: 2s)");
        title.getStyleClass().add("card-header");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Now");
        refreshBtn.getStyleClass().add("btn-outline");
        refreshBtn.setOnAction(e -> refreshInventory());

        header.getChildren().addAll(title, spacer, refreshBtn);

        // Table
        inventoryTable.setItems(inventoryData);
        inventoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<InventoryDto, String> skuCol = new TableColumn<>("SKU Code");
        skuCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sku()));
        skuCol.setPrefWidth(120);

        TableColumn<InventoryDto, Integer> availCol = new TableColumn<>("Available Qty");
        availCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().availableQty()).asObject());
        availCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                if (empty || qty == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(qty));
                    if (qty <= 10) {
                        setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
                    } else if (qty <= 50) {
                        setStyle("-fx-text-fill: #fbbf24; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
                    }
                }
            }
        });
        availCol.setPrefWidth(100);

        TableColumn<InventoryDto, Integer> resCol = new TableColumn<>("Reserved Qty");
        resCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().reservedQty()).asObject());
        resCol.setPrefWidth(100);

        TableColumn<InventoryDto, Integer> totCol = new TableColumn<>("Total Initial Qty");
        totCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().totalQty()).asObject());
        totCol.setPrefWidth(110);

        TableColumn<InventoryDto, Void> barCol = new TableColumn<>("Stock Availability Level");
        barCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label ratioLabel = new Label();
            private final HBox container = new HBox(8, bar, ratioLabel);

            {
                container.setAlignment(Pos.CENTER_LEFT);
                bar.setPrefWidth(150);
                bar.setStyle("-fx-accent: #38bdf8;");
                ratioLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    InventoryDto inv = getTableRow().getItem();
                    double ratio = inv.getAvailabilityRatio();
                    bar.setProgress(ratio);
                    ratioLabel.setText(String.format("%.0f%%", ratio * 100));

                    if (ratio < 0.2) {
                        bar.setStyle("-fx-accent: #ef4444;");
                    } else if (ratio < 0.5) {
                        bar.setStyle("-fx-accent: #f59e0b;");
                    } else {
                        bar.setStyle("-fx-accent: #10b981;");
                    }
                    setGraphic(container);
                }
            }
        });
        barCol.setPrefWidth(220);

        inventoryTable.getColumns().addAll(skuCol, availCol, resCol, totCol, barCol);
        VBox.setVgrow(inventoryTable, Priority.ALWAYS);

        contentBox.getChildren().addAll(header, inventoryTable);
        setCenter(contentBox);
    }

    public void refreshInventory() {
        gatewayClient.fetchInventory()
                .thenAccept(items -> Platform.runLater(() -> {
                    inventoryData.setAll(items);
                    if (skuCountListener != null) {
                        skuCountListener.accept(items.size());
                    }
                }))
                .exceptionally(ex -> null);
    }
}
