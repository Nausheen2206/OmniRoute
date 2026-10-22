package com.omniroute.client.ui;

import com.omniroute.client.api.GatewayClient;
import com.omniroute.client.model.AssignmentDto;
import com.omniroute.client.model.TruckDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public class FleetView extends BorderPane {

    private final GatewayClient gatewayClient;
    private final Consumer<String> statusReporter;
    private final Consumer<Integer> truckCountListener;

    private final Canvas mapCanvas = new Canvas(600, 520);
    private final TableView<AssignmentDto> assignmentTable = new TableView<>();
    private final ObservableList<AssignmentDto> assignmentData = FXCollections.observableArrayList();

    private final TableView<TruckDto> truckTable = new TableView<>();
    private final ObservableList<TruckDto> truckData = FXCollections.observableArrayList();

    private final Label truckCounterLabel = new Label("Live Fleet: 0 Active Trucks");
    private final Label assignmentCounterLabel = new Label("Assignments: 0");

    // Warehouse default coordinates
    private static final double WAREHOUSE_LAT = 11.0168;
    private static final double WAREHOUSE_LON = 76.9558;
    private static final double VIEW_SPAN_DEG = 0.36; // Range to render

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private List<TruckDto> currentTrucks = Collections.emptyList();
    private List<AssignmentDto> currentAssignments = Collections.emptyList();

    public FleetView(GatewayClient gatewayClient, Consumer<String> statusReporter, Consumer<Integer> truckCountListener) {
        this.gatewayClient = gatewayClient;
        this.statusReporter = statusReporter;
        this.truckCountListener = truckCountListener;

        setPadding(new Insets(16));
        setStyle("-fx-background-color: #0b0f19;");

        // Split pane: Left Canvas Radar Map, Right Side Info & Assignments
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent;");

        VBox mapBox = createMapBox();
        VBox rightBox = createRightBox();

        splitPane.getItems().addAll(mapBox, rightBox);
        splitPane.setDividerPositions(0.60);

        setCenter(splitPane);

        // Bind canvas resize
        mapCanvas.widthProperty().bind(mapBox.widthProperty().subtract(20));
        mapCanvas.heightProperty().bind(mapBox.heightProperty().subtract(60));
        mapCanvas.widthProperty().addListener((obs, oldVal, newVal) -> renderMap());
        mapCanvas.heightProperty().addListener((obs, oldVal, newVal) -> renderMap());

        renderMap();
    }

    private VBox createMapBox() {
        VBox box = new VBox(8);
        box.getStyleClass().add("card-panel");
        VBox.setVgrow(box, Priority.ALWAYS);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Live Telemetry Radar Map (500ms Refresh)");
        title.getStyleClass().add("card-header");

        truckCounterLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label legend = new Label("● Warehouse (Gold)   ● Fleet (Cyan)   ― Active Dispatch (Green)");
        legend.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        header.getChildren().addAll(title, truckCounterLabel, spacer, legend);

        Pane canvasHolder = new Pane(mapCanvas);
        VBox.setVgrow(canvasHolder, Priority.ALWAYS);

        box.getChildren().addAll(header, canvasHolder);
        return box;
    }

    private VBox createRightBox() {
        VBox box = new VBox(12);
        box.setStyle("-fx-padding: 0 0 0 12px;");

        // Assignments Box
        VBox assignCard = new VBox(8);
        assignCard.getStyleClass().add("card-panel");
        VBox.setVgrow(assignCard, Priority.ALWAYS);

        HBox assignHeader = new HBox(8);
        assignHeader.setAlignment(Pos.CENTER_LEFT);
        Label assignTitle = new Label("Recent Fleet Assignments");
        assignTitle.getStyleClass().add("card-header");
        assignmentCounterLabel.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold; -fx-font-size: 11px;");
        assignHeader.getChildren().addAll(assignTitle, assignmentCounterLabel);

        assignmentTable.setItems(assignmentData);
        assignmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AssignmentDto, String> ordCol = new TableColumn<>("Order ID");
        ordCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().orderId() != null && cell.getValue().orderId().length() > 8
                        ? cell.getValue().orderId().substring(0, 8) + "..."
                        : cell.getValue().orderId()
        ));
        ordCol.setPrefWidth(90);

        TableColumn<AssignmentDto, String> truckCol = new TableColumn<>("Assigned Truck");
        truckCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().truckId()));
        truckCol.setPrefWidth(100);

        TableColumn<AssignmentDto, String> distCol = new TableColumn<>("Distance");
        distCol.setCellValueFactory(cell -> new SimpleStringProperty(String.format("%.2f km", cell.getValue().distanceKm())));
        distCol.setPrefWidth(80);

        TableColumn<AssignmentDto, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().assignedAt() != null ? TIME_FMT.format(cell.getValue().assignedAt()) : "-"
        ));
        timeCol.setPrefWidth(70);

        assignmentTable.getColumns().addAll(ordCol, truckCol, distCol, timeCol);
        VBox.setVgrow(assignmentTable, Priority.ALWAYS);
        assignCard.getChildren().addAll(assignHeader, assignmentTable);

        // Active Trucks List Box
        VBox truckCard = new VBox(8);
        truckCard.getStyleClass().add("card-panel");
        VBox.setVgrow(truckCard, Priority.ALWAYS);

        Label truckTitle = new Label("Active Fleet Units");
        truckTitle.getStyleClass().add("card-header");

        truckTable.setItems(truckData);
        truckTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<TruckDto, String> idCol = new TableColumn<>("Truck ID");
        idCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().truckId()));
        idCol.setPrefWidth(90);

        TableColumn<TruckDto, String> posCol = new TableColumn<>("Coordinates");
        posCol.setCellValueFactory(cell -> new SimpleStringProperty(
                String.format("%.4f, %.4f", cell.getValue().lat(), cell.getValue().lon())
        ));
        posCol.setPrefWidth(120);

        TableColumn<TruckDto, String> statCol = new TableColumn<>("Status");
        statCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        statCol.setPrefWidth(70);

        truckTable.getColumns().addAll(idCol, posCol, statCol);
        VBox.setVgrow(truckTable, Priority.ALWAYS);
        truckCard.getChildren().addAll(truckTitle, truckTable);

        box.getChildren().addAll(assignCard, truckCard);
        return box;
    }

    public void refreshFleet() {
        gatewayClient.fetchTrucks()
                .thenAccept(trucks -> Platform.runLater(() -> {
                    this.currentTrucks = trucks;
                    truckData.setAll(trucks);
                    truckCounterLabel.setText("Live Fleet: " + trucks.size() + " Active Trucks");
                    if (truckCountListener != null) {
                        truckCountListener.accept(trucks.size());
                    }
                    renderMap();
                }))
                .exceptionally(ex -> null);

        gatewayClient.fetchAssignments()
                .thenAccept(assignments -> Platform.runLater(() -> {
                    this.currentAssignments = assignments;
                    assignmentData.setAll(assignments);
                    assignmentCounterLabel.setText("Assignments: " + assignments.size());
                    renderMap();
                }))
                .exceptionally(ex -> null);
    }

    private void renderMap() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        // Background
        gc.setFill(Color.web("#060911"));
        gc.fillRect(0, 0, w, h);

        double centerX = w / 2.0;
        double centerY = h / 2.0;

        // Radar grid circles
        gc.setStroke(Color.web("#1e293b"));
        gc.setLineWidth(1.0);
        for (int r = 50; r <= Math.max(w, h); r += 60) {
            gc.strokeOval(centerX - r, centerY - r, r * 2, r * 2);
        }

        // Radar Crosshairs
        gc.setStroke(Color.web("#172235"));
        gc.strokeLine(0, centerY, w, centerY);
        gc.strokeLine(centerX, 0, centerX, h);

        // Bounding box calculations
        double minLat = WAREHOUSE_LAT - VIEW_SPAN_DEG / 2.0;
        double maxLat = WAREHOUSE_LAT + VIEW_SPAN_DEG / 2.0;
        double minLon = WAREHOUSE_LON - VIEW_SPAN_DEG / 2.0;
        double maxLon = WAREHOUSE_LON + VIEW_SPAN_DEG / 2.0;

        // Map assigned truck IDs
        Set<String> assignedTruckIds = new HashSet<>();
        Map<String, AssignmentDto> latestAssignmentMap = new HashMap<>();
        for (AssignmentDto a : currentAssignments) {
            assignedTruckIds.add(a.truckId());
            latestAssignmentMap.put(a.truckId(), a);
        }

        // Draw assignment vector lines from warehouse to assigned trucks
        gc.setLineWidth(1.5);
        gc.setStroke(Color.web("#10b981", 0.6));
        for (TruckDto truck : currentTrucks) {
            if (assignedTruckIds.contains(truck.truckId())) {
                double tx = scaleLon(truck.lon(), minLon, maxLon, w);
                double ty = scaleLat(truck.lat(), minLat, maxLat, h);
                gc.strokeLine(centerX, centerY, tx, ty);
            }
        }

        // Draw all trucks
        for (TruckDto truck : currentTrucks) {
            double tx = scaleLon(truck.lon(), minLon, maxLon, w);
            double ty = scaleLat(truck.lat(), minLat, maxLat, h);

            boolean isAssigned = assignedTruckIds.contains(truck.truckId());
            if (isAssigned) {
                // Glowing outer ring for assigned truck
                gc.setFill(Color.web("#10b981", 0.3));
                gc.fillOval(tx - 7, ty - 7, 14, 14);
                gc.setFill(Color.web("#34d399"));
                gc.fillOval(tx - 4, ty - 4, 8, 8);
            } else {
                // Normal truck blip
                gc.setFill(Color.web("#0284c7", 0.3));
                gc.fillOval(tx - 5, ty - 5, 10, 10);
                gc.setFill(Color.web("#38bdf8"));
                gc.fillOval(tx - 3, ty - 3, 6, 6);
            }
        }

        // Draw Central Warehouse Hub (Glowing Beacon)
        gc.setFill(Color.web("#f59e0b", 0.25));
        gc.fillOval(centerX - 16, centerY - 16, 32, 32);
        gc.setFill(Color.web("#f59e0b", 0.5));
        gc.fillOval(centerX - 10, centerY - 10, 20, 20);
        gc.setFill(Color.web("#fbbf24"));
        gc.fillOval(centerX - 5, centerY - 5, 10, 10);

        // Warehouse label
        gc.setFill(Color.web("#fde68a"));
        gc.fillText("WAREHOUSE HUB", centerX + 12, centerY + 4);
    }

    private double scaleLon(double lon, double minLon, double maxLon, double width) {
        return ((lon - minLon) / (maxLon - minLon)) * width;
    }

    private double scaleLat(double lat, double minLat, double maxLat, double height) {
        // Invert Y for screen coordinates (north is up)
        return height - (((lat - minLat) / (maxLat - minLat)) * height);
    }
}
