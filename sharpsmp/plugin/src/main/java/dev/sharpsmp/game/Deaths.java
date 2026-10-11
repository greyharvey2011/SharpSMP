package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Deaths {
    private final SharpPlugin plugin;
    private final Set<UUID> resolved = ConcurrentHashMap.newKeySet();

    public Deaths(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(Player victim, Player killer, boolean logout) {
        if (!resolved.add(victim.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> resolved.remove(victim.getUniqueId()), 100L);
        PlayerProfile victimProfile = plugin.profiles().get(victim);
        victimProfile.addDeath();
        plugin.combat().clear(victim);
        Component message;
        if (killer != null && !killer.equals(victim)) {
            PlayerProfile killerProfile = plugin.profiles().get(killer);
            killerProfile.addKill();
            long reward = plugin.getConfig().getLong("kills.reward", 50);
            long streakBonus = plugin.getConfig().getLong("kills.streak-bonus", 25) * Math.min(killerProfile.streak(), 10);
            killerProfile.deposit(reward + streakBonus);
            long bounty = victimProfile.claimBounty();
            if (bounty > 0) {
                killerProfile.deposit(bounty);
            }
            message = logout
                    ? Component.text(victim.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" combat logged to ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(), NamedTextColor.RED))
                    : Component.text(killer.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" eliminated ", NamedTextColor.GRAY))
                    .append(Component.text(victim.getName(), NamedTextColor.RED));
            if (bounty > 0) {
                message = message.append(Component.text(" (" + plugin.text().money(bounty) + " bounty)", NamedTextColor.GOLD));
                killer.showTitle(Title.title(
                        plugin.text().mini("<gradient:#ff3355:#ffffff><bold>Bounty claimed</bold></gradient>"),
                        plugin.text().mini("<gray>" + plugin.text().money(bounty)),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(400))
                ));
                plugin.play(killer, Sound.ENTITY_PLAYER_LEVELUP, 1.1f);
            }
            plugin.play(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
            int streak = killerProfile.streak();
            if (streak == 5 || streak == 10 || streak == 15 || streak == 20 || streak == 25) {
                broadcast("streak", plugin.text().vars("player", killer.getName(), "count", String.valueOf(streak)), killer, victim);
            }
            plugin.refresh(killer);
        } else if (logout) {
            message = Component.text(victim.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" combat logged.", NamedTextColor.GRAY));
        } else {
            message = Component.text(victim.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" died.", NamedTextColor.GRAY));
        }
        broadcastMessage(message, killer, victim);
        plugin.refresh(victim);
        plugin.profiles().markDirty();
    }

    private void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver vars, Player killer, Player victim) {
        Component component = plugin.text().banner(key, vars);
        broadcastMessage(component, killer, victim);
    }

    private void broadcastMessage(Component message, Player killer, Player victim) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean involved = player.equals(killer) || player.equals(victim);
            if (involved || plugin.profiles().get(player).setting("death-messages")) {
                player.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
