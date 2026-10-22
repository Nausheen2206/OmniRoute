package com.omniroute.client;

import com.omniroute.client.api.GatewayClient;
import com.omniroute.client.model.OrderDto;
import com.omniroute.client.ui.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class OmniRouteApplication extends Application {

    private GatewayClient gatewayClient;
    private ScheduledExecutorService scheduler;

    private HeaderView headerView;
    private OrdersView ordersView;
    private FleetView fleetView;
    private InventoryView inventoryView;
    private StatusFooterView statusFooterView;

    private final AtomicInteger totalOrdersCount = new AtomicInteger(0);
    private final AtomicInteger completedOrdersCount = new AtomicInteger(0);
    private final AtomicInteger activeTrucksCount = new AtomicInteger(0);
    private final AtomicInteger skuCount = new AtomicInteger(0);

    @Override
    public void start(Stage primaryStage) {
        String defaultGatewayUrl = System.getProperty("gateway.url", "http://localhost:8080");
        this.gatewayClient = new GatewayClient(defaultGatewayUrl);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0b0f19;");

        statusFooterView = new StatusFooterView();

        headerView = new HeaderView(gatewayClient, newUrl -> {
            statusFooterView.setMessage("Switched gateway endpoint to: " + newUrl);
            triggerAllRefreshes();
        });

        ordersView = new OrdersView(gatewayClient,
                msg -> statusFooterView.setMessage(msg),
                count -> totalOrdersCount.set(count)
        );

        fleetView = new FleetView(gatewayClient,
                msg -> statusFooterView.setMessage(msg),
                trucks -> activeTrucksCount.set(trucks)
        );

        inventoryView = new InventoryView(gatewayClient,
                msg -> statusFooterView.setMessage(msg),
                skus -> skuCount.set(skus)
        );

        // Tabs
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab ordersTab = new Tab("📦  Orders & Sagas", ordersView);
        Tab fleetTab = new Tab("🚚  Live Fleet Radar", fleetView);
        Tab inventoryTab = new Tab("🏭  Warehouse Inventory", inventoryView);

        tabPane.getTabs().addAll(ordersTab, fleetTab, inventoryTab);

        root.setTop(headerView);
        root.setCenter(tabPane);
        root.setBottom(statusFooterView);

        Scene scene = new Scene(root, 1280, 820);

        // Load stylesheet
        try {
            String cssPath = getClass().getResource("/styles.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("Warning: could not load styles.css: " + e.getMessage());
        }

        primaryStage.setTitle("OmniRoute - Distributed Event-Driven Control Center");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);

        primaryStage.setOnCloseRequest(e -> {
            shutdownScheduler();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();

        // Start background schedulers
        startScheduledTasks();
    }

    private void startScheduledTasks() {
        scheduler = Executors.newScheduledThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        // Orders refresh every 1 second
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ordersView.refreshOrders();
                calculateCompletedOrders();
            } catch (Exception ignored) {}
        }, 500, 1000, TimeUnit.MILLISECONDS);

        // Fleet refresh twice per second (500ms)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                fleetView.refreshFleet();
            } catch (Exception ignored) {}
        }, 500, 500, TimeUnit.MILLISECONDS);

        // Inventory refresh every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                inventoryView.refreshInventory();
            } catch (Exception ignored) {}
        }, 1000, 2000, TimeUnit.MILLISECONDS);

        // Gateway healthcheck & metric refresh every 3 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                gatewayClient.checkHealth().thenAccept(isHealthy -> {
                    headerView.updateConnectionStatus(isHealthy);
                    headerView.updateMetrics(
                            totalOrdersCount.get(),
                            completedOrdersCount.get(),
                            activeTrucksCount.get(),
                            skuCount.get()
                    );
                });
            } catch (Exception ignored) {}
        }, 1000, 3000, TimeUnit.MILLISECONDS);
    }

    private void calculateCompletedOrders() {
        List<OrderDto> orders = ordersView.getCurrentOrders();
        long completed = orders.stream()
                .filter(o -> "COMPLETED".equalsIgnoreCase(o.status()))
                .count();
        completedOrdersCount.set((int) completed);
        totalOrdersCount.set(orders.size());
    }

    private void triggerAllRefreshes() {
        ordersView.refreshOrders();
        fleetView.refreshFleet();
        inventoryView.refreshInventory();
    }

    private void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
