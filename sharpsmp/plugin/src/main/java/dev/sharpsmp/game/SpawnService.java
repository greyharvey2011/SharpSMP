package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.gui.Items;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpawnService {
    private final SharpPlugin plugin;
    private final List<Runnable> waiting = new ArrayList<>();
    private final Set<UUID> shownBar = new HashSet<>();
    private final Map<UUID, Long> rescueCooldown = new HashMap<>();
    private BossBar bar;
    private boolean ready;
    private boolean building;
    private int ambience;

    public SpawnService(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin() {
        bar = BossBar.bossBar(
                plugin.text().mini("<gradient:#ff3355:#ffffff><bold>SharpSMP</bold></gradient> <gray>The Edge"),
                1f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        World world = world();
        if (world == null) {
            plugin.getLogger().severe("Spawn world '" + worldName() + "' is not loaded.");
            ready = true;
            flush();
            return;
        }
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
        boolean built = plugin.getConfig().getBoolean("spawn.built")
                && world.getBlockAt(centerX(), centerY() + 1, centerZ()).getType() == Material.BEACON;
        if (built) {
            ready = true;
            Location location = location();
            if (location != null) {
                world.setSpawnLocation(location);
            }
            refreshBoard();
            plugin.getLogger().info("The Edge is already standing.");
        } else {
            rebuild();
        }
    }

    public boolean rebuild() {
        if (building) {
            return false;
        }
        World world = world();
        if (world == null) {
            return false;
        }
        building = true;
        ready = false;
        Location hold = new Location(world, centerX() + 0.5, centerY() + 30, centerZ() + 0.5);
        for (Player player : world.getPlayers()) {
            if (inSpawn(player.getLocation())) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, false, false, true));
                player.teleport(hold);
                player.setFallDistance(0);
            }
        }
        plugin.getLogger().info("Raising The Edge...");
        new SpawnBuilder(plugin, world, centerX(), centerY(), centerZ(), radius()).start(() -> {
            building = false;
            ready = true;
            plugin.getConfig().set("spawn.built", true);
            plugin.saveConfig();
            refreshBoard();
            flush();
            plugin.getLogger().info("The Edge is ready.");
        });
        return true;
    }

    public void shutdown() {
        if (bar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
        }
        shownBar.clear();
    }

    public boolean ready() {
        return ready;
    }

    public boolean building() {
        return building;
    }

    public void whenReady(Runnable task) {
        if (ready) {
            task.run();
        } else {
            waiting.add(task);
        }
    }

    public Location location() {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world, centerX() + 0.5, centerY() + 1, centerZ() + 7.5, 180f, 0f);
    }

    public boolean inSpawn(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(worldName())) {
            return false;
        }
        double dx = location.getX() - (centerX() + 0.5);
        double dz = location.getZ() - (centerZ() + 0.5);
        double reach = radius() + 2.0;
        return dx * dx + dz * dz <= reach * reach
                && location.getY() > centerY() - 20
                && location.getY() < centerY() + 40;
    }

    public boolean canBuild(Player player, Location location) {
        return !inSpawn(location) || player.hasPermission("sharpsmp.admin");
    }

    public void giveStar(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (!profile.setting("star") || hasStar(player)) {
            return;
        }
        ItemStack star = Items.star(plugin);
        if (player.getInventory().getItem(8) == null || player.getInventory().getItem(8).getType().isAir()) {
            player.getInventory().setItem(8, star);
        } else {
            plugin.give(player, star);
        }
    }

    public String npcId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String role = entity.getPersistentDataContainer().get(plugin.key("role"), PersistentDataType.STRING);
        if (role != null && role.startsWith("npc:")) {
            return role.substring(4);
        }
        return null;
    }

    public void rescue(Player player, Location to) {
        if (!ready || to == null || to.getWorld() == null || world() == null || !to.getWorld().equals(world())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        double dx = to.getX() - (centerX() + 0.5);
        double dz = to.getZ() - (centerZ() + 0.5);
        if (dx * dx + dz * dz > (radius() + 8.0) * (radius() + 8.0)) {
            return;
        }
        if (to.getY() >= centerY() - 4 || to.getY() < centerY() - 40) {
            return;
        }
        for (int i = 1; i <= 3; i++) {
            if (!to.getWorld().getBlockAt(to.getBlockX(), to.getBlockY() - i, to.getBlockZ()).isPassable()) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (rescueCooldown.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        rescueCooldown.put(player.getUniqueId(), now + 2000L);
        Location spawn = location();
        if (spawn == null) {
            return;
        }
        player.teleport(spawn);
        player.setFallDistance(0);
        player.sendMessage(plugin.text().msg("rescued"));
        plugin.play(player, Sound.ITEM_TRIDENT_RETURN, 1.15f);
    }

    public void tick() {
        if (bar == null) {
            return;
        }
        float pulse = 0.84f + 0.16f * (float) Math.sin(System.currentTimeMillis() / 420.0d);
        bar.progress(Math.max(0.2f, Math.min(1f, pulse)));
        World world = world();
        boolean chime = ambience++ % 16 == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean here = ready && world != null && player.getWorld().equals(world) && inSpawn(player.getLocation());
            PlayerProfile profile = plugin.profiles().get(player);
            boolean show = here && profile.setting("bossbar");
            if (show && shownBar.add(player.getUniqueId())) {
                player.showBossBar(bar);
            } else if (!show && shownBar.remove(player.getUniqueId())) {
                player.hideBossBar(bar);
            }
            if (!here) {
                continue;
            }
            if (profile.setting("particles")) {
                Location center = new Location(world, centerX() + 0.5, centerY() + 2, centerZ() + 0.5);
                player.spawnParticle(Particle.END_ROD, center, 10, 1.6, 0.8, 1.6, 0.01);
                player.spawnParticle(Particle.CRIMSON_SPORE, center, 16, 2.4, 0.5, 2.4, 0);
            }
            if (chime && profile.setting("sounds")) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.25f);
            }
        }
    }

    public void refreshBoard() {
        Entity entity = find("holo:lb");
        if (!(entity instanceof TextDisplay display)) {
            return;
        }
        List<PlayerProfile> rich = plugin.profiles().all().stream()
                .sorted(Comparator.comparingLong((PlayerProfile profile) -> profile.balance()).reversed())
                .limit(3)
                .toList();
        List<PlayerProfile> hunters = plugin.profiles().all().stream()
                .sorted(Comparator.comparingInt((PlayerProfile profile) -> profile.kills()).reversed())
                .limit(3)
                .toList();
        Component text = Component.text("Leaders", NamedTextColor.WHITE)
                .append(Component.newline())
                .append(section("Richest", rich, true))
                .append(Component.newline())
                .append(section("Hunters", hunters, false));
        display.text(text);
    }

    private Component section(String title, List<PlayerProfile> profiles, boolean money) {
        Component component = Component.text(title, NamedTextColor.RED);
        int place = 1;
        if (profiles.isEmpty()) {
            return component.append(Component.newline()).append(Component.text("No one yet", NamedTextColor.DARK_GRAY));
        }
        for (PlayerProfile profile : profiles) {
            String value = money ? plugin.text().money(profile.balance()) : profile.kills() + " kills";
            component = component.append(Component.newline())
                    .append(Component.text(place + ". " + profile.name() + " ", NamedTextColor.GRAY))
                    .append(Component.text(value, NamedTextColor.WHITE));
            place++;
        }
        return component;
    }

    private Entity find(String role) {
        World world = world();
        if (world == null) {
            return null;
        }
        Location center = new Location(world, centerX() + 0.5, centerY(), centerZ() + 0.5);
        world.getChunkAt(center).load();
        for (Entity entity : world.getNearbyEntities(center, radius() + 20, 40, radius() + 20)) {
            String tag = entity.getPersistentDataContainer().get(plugin.key("role"), PersistentDataType.STRING);
            if (role.equals(tag)) {
                return entity;
            }
        }
        return null;
    }

    private void flush() {
        List<Runnable> tasks = List.copyOf(waiting);
        waiting.clear();
        for (Runnable task : tasks) {
            task.run();
        }
    }

    private boolean hasStar(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (plugin.isStar(stack)) {
                return true;
            }
        }
        return false;
    }

    private World world() {
        return Bukkit.getWorld(worldName());
    }

    private String worldName() {
        return plugin.getConfig().getString("spawn.world", "world");
    }

    private int centerX() {
        return plugin.getConfig().getInt("spawn.center-x", 0);
    }

    private int centerY() {
        return plugin.getConfig().getInt("spawn.center-y", 220);
    }

    private int centerZ() {
        return plugin.getConfig().getInt("spawn.center-z", 0);
    }

    private int radius() {
        return Math.max(18, Math.min(40, plugin.getConfig().getInt("spawn.radius", 28)));
    }
}
