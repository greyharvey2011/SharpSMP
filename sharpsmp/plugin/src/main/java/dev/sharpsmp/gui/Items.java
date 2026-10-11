package dev.sharpsmp.gui;

import dev.sharpsmp.SharpPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {
    }

    public static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack button(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (Component line : lore) {
                lines.add(plain(line));
            }
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack filler(Material material) {
        return button(material, Component.text(" "), List.of(), false);
    }

    public static void frame(Inventory inventory) {
        ItemStack edge = filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack corner = filler(Material.RED_STAINED_GLASS_PANE);
        int size = inventory.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean border = row == 0 || row == rows - 1 || column == 0 || column == 8;
            if (!border) {
                continue;
            }
            boolean cornerSlot = (row == 0 || row == rows - 1) && (column == 0 || column == 8);
            inventory.setItem(slot, cornerSlot ? corner : edge);
        }
    }

    public static ItemStack head(OfflinePlayer player, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(plain(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream().map(Items::plain).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack star(SharpPlugin plugin) {
        ItemStack item = button(
                Material.NETHER_STAR,
                plugin.text().mini("<gradient:#ff3355:#ffffff><bold>Sharp Menu</bold></gradient>"),
                List.of(
                        plugin.text().mini("<gray>Right-click to open the hub."),
                        plugin.text().mini("<dark_gray>Shop, bounties, auction, crates")
                ),
                true
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.key("star"), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
