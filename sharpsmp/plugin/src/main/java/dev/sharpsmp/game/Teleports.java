package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Teleports {
    private record Warmup(Location destination, long when, Location start, String label) {
    }

    private final SharpPlugin plugin;
    private final Map<UUID, Warmup> pending = new ConcurrentHashMap<>();

    public Teleports(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void later(Player player, Location destination, int seconds, String label) {
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(plugin.text().msg("warp-missing"));
            return;
        }
        if (plugin.combat().tagged(player)) {
            player.sendMessage(plugin.text().msg("combat-blocked", plugin.text().vars("time", String.valueOf(plugin.combat().secondsLeft(player)))));
            return;
        }
        if (seconds <= 0) {
            finish(player, destination, label);
            return;
        }
        pending.put(player.getUniqueId(), new Warmup(destination, System.currentTimeMillis() + seconds * 1000L, player.getLocation().clone(), label));
        player.sendMessage(plugin.text().msg("teleport-start", plugin.text().vars("label", label, "time", String.valueOf(seconds))));
    }

    public void cancel(Player player) {
        if (pending.remove(player.getUniqueId()) != null) {
            player.sendMessage(plugin.text().msg("teleport-cancel"));
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Warmup>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Warmup> entry = iterator.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            Warmup warmup = entry.getValue();
            if (moved(player, warmup.start()) || plugin.combat().tagged(player)) {
                iterator.remove();
                player.sendMessage(plugin.text().msg("teleport-cancel"));
                continue;
            }
            if (now >= warmup.when()) {
                iterator.remove();
                finish(player, warmup.destination(), warmup.label());
            }
        }
    }

    private void finish(Player player, Location destination, String label) {
        player.teleport(destination);
        player.setFallDistance(0);
        player.sendMessage(plugin.text().msg("teleported", plugin.text().vars("label", label)));
        plugin.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.05f);
    }

    private boolean moved(Player player, Location start) {
        Location now = player.getLocation();
        return now.getWorld() == null
                || start.getWorld() == null
                || !now.getWorld().equals(start.getWorld())
                || now.getBlockX() != start.getBlockX()
                || now.getBlockY() != start.getBlockY()
                || now.getBlockZ() != start.getBlockZ();
    }
}
