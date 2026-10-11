package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class Rtp {
    private final SharpPlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public Rtp(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public long secondsLeft(Player player) {
        long until = cooldown.getOrDefault(player.getUniqueId(), 0L);
        return Math.max(0L, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public void go(Player player) {
        if (plugin.combat().tagged(player)) {
            player.sendMessage(plugin.text().msg("combat-blocked", plugin.text().vars("time", String.valueOf(plugin.combat().secondsLeft(player)))));
            return;
        }
        long left = secondsLeft(player);
        if (left > 0) {
            player.sendMessage(plugin.text().msg("rtp-cooldown", plugin.text().vars("time", String.valueOf(left))));
            return;
        }
        World world = plugin.getServer().getWorld(plugin.getConfig().getString("rtp.world", "world"));
        if (world == null) {
            player.sendMessage(plugin.text().msg("rtp-fail"));
            return;
        }
        player.sendMessage(plugin.text().msg("rtp-search"));
        attempt(player, world, 10);
    }

    private void attempt(Player player, World world, int left) {
        if (!player.isOnline()) {
            return;
        }
        int min = plugin.getConfig().getInt("rtp.min-distance", 800);
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max-distance", 20000));
        int distance = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        int x = (int) Math.round(Math.cos(angle) * distance);
        int z = (int) Math.round(Math.sin(angle) * distance);
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Location location = inspect(world, x, z);
                    if (location != null) {
                        int seconds = plugin.getConfig().getInt("rtp.delay-seconds", 3);
                        cooldown.put(player.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getInt("rtp.cooldown-seconds", 45) * 1000L);
                        plugin.teleports().later(player, location, seconds, "the wild");
                    } else if (left > 1) {
                        attempt(player, world, left - 1);
                    } else {
                        player.sendMessage(plugin.text().msg("rtp-fail"));
                    }
                })
        );
    }

    private Location inspect(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight() + 2) {
            return null;
        }
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        if (!ground.getType().isSolid() || !feet.isPassable() || !head.isPassable()) {
            return null;
        }
        String name = ground.getType().name();
        if (ground.getType() == Material.LAVA
                || ground.getType() == Material.WATER
                || ground.getType() == Material.MAGMA_BLOCK
                || ground.getType() == Material.CACTUS
                || ground.getType() == Material.POWDER_SNOW
                || ground.getType() == Material.POINTED_DRIPSTONE
                || name.contains("LEAVES")
                || name.contains("FENCE")
                || name.contains("WALL")) {
            return null;
        }
        String biome = world.getBiome(x, y, z).getKey().getKey();
        if (biome.contains("ocean") || biome.contains("river")) {
            return null;
        }
        Location location = new Location(world, x + 0.5, y + 1, z + 0.5);
        if (plugin.spawn().inSpawn(location)) {
            return null;
        }
        return location;
    }
}
