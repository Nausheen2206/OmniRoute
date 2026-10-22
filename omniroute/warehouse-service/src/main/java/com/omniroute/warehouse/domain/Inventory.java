package com.omniroute.warehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    public Inventory() {}

    public Inventory(String sku, int availableQty) {
        this.sku = sku;
        this.availableQty = availableQty;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public int getAvailableQty() { return availableQty; }
    public void setAvailableQty(int availableQty) { this.availableQty = availableQty; }
}
