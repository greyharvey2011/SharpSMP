package dev.sharpsmp.listen;

import dev.sharpsmp.SharpPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class WorldListener implements Listener {
    private final SharpPlugin plugin;

    public WorldListener(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.spawn().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.spawn().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.spawn().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.spawn().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.spawn().inSpawn(player.getLocation())
                && event.getCause() != EntityDamageEvent.DamageCause.KILL
                && !plugin.combat().loggingOut(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.STARVATION) {
            plugin.teleports().cancel(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        plugin.combat().tagPair(attacker, victim);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (plugin.spawn().inSpawn(event.getLocation())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> plugin.spawn().inSpawn(block.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.spawn().inSpawn(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> plugin.spawn().inSpawn(block.getLocation()));
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (plugin.spawn().inSpawn(event.getLocation()) && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        plugin.spawn().rescue(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }
        if (plugin.combat().tagged(event.getPlayer()) && plugin.spawn().inSpawn(event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.text().msg("combat-blocked", plugin.text().vars("time", String.valueOf(plugin.combat().secondsLeft(event.getPlayer())))));
        }
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
