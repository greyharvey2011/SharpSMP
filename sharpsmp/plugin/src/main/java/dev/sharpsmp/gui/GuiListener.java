package dev.sharpsmp.gui;

import dev.sharpsmp.SharpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;

public final class GuiListener implements Listener {
    private final SharpPlugin plugin;

    public GuiListener(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            String action = holder.action(event.getSlot());
            if (action == null) {
                return;
            }
            boolean ok = plugin.actions().handle(player, action, event);
            plugin.play(player, ok ? org.bukkit.Sound.UI_BUTTON_CLICK : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, ok ? 1.15f : 0.7f);
            return;
        }
        if (event.getInventory().getType() == InventoryType.MERCHANT && event.getInventory().getHolder() instanceof org.bukkit.entity.Villager villager
                && plugin.spawn().npcId(villager) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }
}
