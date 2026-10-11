package dev.sharpsmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class GuiHolder implements InventoryHolder {
    private final String id;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public GuiHolder(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    public void bind(int slot, String action) {
        actions.put(slot, action);
    }

    public String action(int slot) {
        return actions.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
