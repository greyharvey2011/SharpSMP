package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatLog {
    public static final Set<String> BLOCKED = Set.of(
            "spawn", "rtp", "home", "homes", "sethome", "delhome", "warp", "warps",
            "tpa", "tpaccept", "tpdeny", "tpahere", "tpyes", "tpno", "kit", "kits", "crate", "crates"
    );

    private final SharpPlugin plugin;
    private final Map<UUID, Long> tags = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> attackers = new ConcurrentHashMap<>();
    private final Set<UUID> loggingOut = ConcurrentHashMap.newKeySet();

    public CombatLog(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void tagPair(Player first, Player second) {
        tag(first, second);
        tag(second, first);
    }

    public boolean tagged(Player player) {
        Long until = tags.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long secondsLeft(Player player) {
        Long until = tags.get(player.getUniqueId());
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public Player attacker(Player player) {
        UUID id = attackers.get(player.getUniqueId());
        return id == null ? null : Bukkit.getPlayer(id);
    }

    public void clear(Player player) {
        tags.remove(player.getUniqueId());
        attackers.remove(player.getUniqueId());
    }

    public void markLogout(Player player) {
        loggingOut.add(player.getUniqueId());
    }

    public boolean loggingOut(Player player) {
        return loggingOut.contains(player.getUniqueId());
    }

    public void tick() {
        long now = System.currentTimeMillis();
        tags.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            long left = secondsLeft(player);
            if (left <= 0) {
                continue;
            }
            Component bar = plugin.text().mini("<red>Combat</red> <gray>" + left + "s");
            player.sendActionBar(bar);
        }
    }

    private void tag(Player player, Player other) {
        long millis = plugin.getConfig().getInt("combat.tag-seconds", 20) * 1000L;
        tags.put(player.getUniqueId(), System.currentTimeMillis() + millis);
        attackers.put(player.getUniqueId(), other.getUniqueId());
        plugin.teleports().cancel(player);
    }
}
