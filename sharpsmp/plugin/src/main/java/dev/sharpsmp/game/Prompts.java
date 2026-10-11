package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Prompts {
    private record Prompt(long expiresAt, Consumer<String> callback) {
    }

    private final SharpPlugin plugin;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    public Prompts(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, Component message, Consumer<String> callback) {
        pending.put(player.getUniqueId(), new Prompt(System.currentTimeMillis() + 20_000L, callback));
        player.closeInventory();
        player.sendMessage(message);
        player.sendMessage(plugin.text().mini("<dark_gray>Type <white>cancel <dark_gray>to stop. You have 20 seconds."));
    }

    public boolean take(Player player, String text) {
        Prompt prompt = pending.get(player.getUniqueId());
        if (prompt == null) {
            return false;
        }
        if (prompt.expiresAt() < System.currentTimeMillis()) {
            pending.remove(player.getUniqueId());
            return false;
        }
        pending.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (text.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.text().msg("cancelled"));
                return;
            }
            prompt.callback().accept(text);
        });
        return true;
    }
}
