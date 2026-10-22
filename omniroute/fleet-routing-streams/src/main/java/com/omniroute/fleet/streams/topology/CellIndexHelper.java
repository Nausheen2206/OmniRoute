package com.omniroute.fleet.streams.topology;

import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CellIndexHelper {

    private CellIndexHelper() {}

    public static Set<String> getTrucks(ReadOnlyKeyValueStore<String, String> store, String cellId) {
        if (store == null || cellId == null) {
            return Collections.emptySet();
        }
        String val = store.get(cellId);
        if (val == null || val.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String id : val.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    public static void addTruck(KeyValueStore<String, String> store, String cellId, String truckId) {
        if (store == null || cellId == null || truckId == null) {
            return;
        }
        Set<String> set = new LinkedHashSet<>(getTrucks(store, cellId));
        if (set.add(truckId)) {
            store.put(cellId, String.join(",", set));
        }
    }

    public static void removeTruck(KeyValueStore<String, String> store, String cellId, String truckId) {
        if (store == null || cellId == null || truckId == null) {
            return;
        }
        Set<String> set = new LinkedHashSet<>(getTrucks(store, cellId));
        if (set.remove(truckId)) {
            if (set.isEmpty()) {
                store.delete(cellId);
            } else {
                store.put(cellId, String.join(",", set));
            }
        }
    }
}
