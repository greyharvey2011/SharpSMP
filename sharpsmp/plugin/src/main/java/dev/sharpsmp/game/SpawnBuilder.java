package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SpawnBuilder {
    private record Npc(String id, String title, String subtitle, Villager.Profession profession, double angle) {
    }

    private static final Npc[] NPCS = {
            new Npc("shop", "Shop", "Gear and food", Villager.Profession.TOOLSMITH, 3 * Math.PI / 2),
            new Npc("bounties", "Bounties", "Name your price", Villager.Profession.WEAPONSMITH, Math.PI / 2),
            new Npc("auction", "Auction", "Player trade", Villager.Profession.LIBRARIAN, 0),
            new Npc("crates", "Crates", "Test your luck", Villager.Profession.CLERIC, Math.PI),
            new Npc("warps", "Warps", "Leave The Edge", Villager.Profession.CARTOGRAPHER, Math.PI / 4),
            new Npc("settings", "Settings", "Make it yours", Villager.Profession.MASON, 3 * Math.PI / 4),
            new Npc("teams", "Teams", "Fight together", Villager.Profession.SHEPHERD, 5 * Math.PI / 4),
            new Npc("leaderboard", "Leaders", "Top of the Edge", Villager.Profession.ARMORER, 7 * Math.PI / 4)
    };

    private final SharpPlugin plugin;
    private final World world;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radius;

    public SpawnBuilder(SharpPlugin plugin, World world, int centerX, int centerY, int centerZ, int radius) {
        this.plugin = plugin;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public void start(Runnable done) {
        int chunkRadius = (radius >> 4) + 2;
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        for (int x = chunkX - chunkRadius; x <= chunkX + chunkRadius; x++) {
            for (int z = chunkZ - chunkRadius; z <= chunkZ + chunkRadius; z++) {
                world.getChunkAt(x, z).load(true);
            }
        }
        clearEntities();
        List<int[]> columns = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    columns.add(new int[]{x, z});
                }
            }
        }
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int budget = 500;
                while (budget-- > 0 && index < columns.size()) {
                    int[] column = columns.get(index++);
                    buildColumn(column[0], column[1]);
                }
                if (index < columns.size()) {
                    return;
                }
                cancel();
                decorate();
                done.run();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void clearEntities() {
        Location center = new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
        for (Entity entity : world.getNearbyEntities(center, radius + 24, 50, radius + 24)) {
            String role = entity.getPersistentDataContainer().get(plugin.key("role"), PersistentDataType.STRING);
            if (role != null && (role.startsWith("npc:") || role.startsWith("holo:") || role.startsWith("decor:"))) {
                entity.remove();
            }
        }
    }

    private void buildColumn(int x, int z) {
        int wx = centerX + x;
        int wz = centerZ + z;
        double dist = Math.sqrt(x * x + z * z);
        for (int dy = -6; dy <= 14; dy++) {
            world.getBlockAt(wx, centerY + dy, wz).setType(Material.AIR, false);
        }
        world.getBlockAt(wx, centerY - 3, wz).setType(Material.DEEPSLATE, false);
        world.getBlockAt(wx, centerY - 2, wz).setType(dist > radius - 4 ? Material.CRYING_OBSIDIAN : Material.BLACKSTONE, false);
        world.getBlockAt(wx, centerY - 1, wz).setType(Material.POLISHED_BLACKSTONE, false);
        world.getBlockAt(wx, centerY, wz).setType(floor(x, z, dist), false);
        boolean rim = dist >= radius - 1.05;
        boolean gate = (Math.abs(x) <= 1 && Math.abs(z) > radius - 4) || (Math.abs(z) <= 1 && Math.abs(x) > radius - 4);
        if (rim && !gate) {
            world.getBlockAt(wx, centerY + 1, wz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            if (Math.floorMod(x * 3 + z * 5, 4) == 0) {
                world.getBlockAt(wx, centerY + 2, wz).setType(Material.SOUL_LANTERN, false);
            }
        }
        if (rim && Math.floorMod(x + z, 6) == 0) {
            for (int i = 2; i <= 5; i++) {
                world.getBlockAt(wx, centerY - i, wz).setType(Material.IRON_CHAIN, false);
            }
        }
    }

    private Material floor(int x, int z, double dist) {
        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            return Material.IRON_BLOCK;
        }
        if (dist <= 5.6 || Math.abs(x) <= 1 || Math.abs(z) <= 1) {
            return Material.SMOOTH_QUARTZ;
        }
        if (dist >= 15.15 && dist <= 16.45) {
            return Material.RED_NETHER_BRICKS;
        }
        if (Math.abs(Math.abs(x) - Math.abs(z)) == 0 && dist > 6 && dist < radius - 2) {
            return Material.RED_NETHER_BRICKS;
        }
        if (x % 6 == 0 && z % 6 == 0) {
            return Material.SEA_LANTERN;
        }
        if (((x + z) & 1) == 0) {
            return Material.DEEPSLATE_TILES;
        }
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private void decorate() {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(centerX + dx, centerY, centerZ + dz).setType(Material.IRON_BLOCK, false);
            }
        }
        for (int dy = 1; dy <= 34; dy++) {
            world.getBlockAt(centerX, centerY + dy, centerZ).setType(Material.AIR, false);
        }
        Block beacon = world.getBlockAt(centerX, centerY + 1, centerZ);
        beacon.setType(Material.BEACON, true);
        world.getBlockAt(centerX, centerY + 32, centerZ).setType(Material.RED_STAINED_GLASS, false);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            int x = (int) Math.round(Math.cos(angle) * 4.5);
            int z = (int) Math.round(Math.sin(angle) * 4.5);
            world.getBlockAt(centerX + x, centerY + 1, centerZ + z).setType(Material.END_ROD, false);
            double pillarAngle = angle + Math.PI / 8;
            int px = (int) Math.round(Math.cos(pillarAngle) * (radius - 7));
            int pz = (int) Math.round(Math.sin(pillarAngle) * (radius - 7));
            pillar(px, pz);
        }
        for (Npc npc : NPCS) {
            spawnNpc(npc);
        }
        title();
        sword();
        leaderboard();
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        Location spawn = new Location(world, centerX + 0.5, centerY + 1, centerZ + 7.5, 180f, 0f);
        world.setSpawnLocation(spawn);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block block = world.getBlockAt(centerX, centerY + 1, centerZ);
            if (block.getType() == Material.BEACON) {
                block.getState().update(true, true);
            }
        }, 20L);
    }

    private void pillar(int x, int z) {
        for (int height = 1; height <= 5; height++) {
            world.getBlockAt(centerX + x, centerY + height, centerZ + z).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        world.getBlockAt(centerX + x, centerY + 6, centerZ + z).setType(Material.CHISELED_POLISHED_BLACKSTONE, false);
        world.getBlockAt(centerX + x, centerY + 7, centerZ + z).setType(Material.END_ROD, false);
    }

    private void spawnNpc(Npc npc) {
        int x = (int) Math.round(Math.cos(npc.angle()) * 13);
        int z = (int) Math.round(Math.sin(npc.angle()) * 13);
        world.getBlockAt(centerX + x, centerY, centerZ + z).setType(Material.SHROOMLIGHT, false);
        int sideX = (int) Math.round(-Math.sin(npc.angle()) * 2);
        int sideZ = (int) Math.round(Math.cos(npc.angle()) * 2);
        frame(x + sideX, z + sideZ);
        frame(x - sideX, z - sideZ);
        Location location = new Location(world, centerX + x + 0.5, centerY + 1, centerZ + z + 0.5);
        Vector toward = new Vector(centerX + 0.5 - location.getX(), 0, centerZ + 0.5 - location.getZ());
        if (toward.lengthSquared() > 0.01) {
            location.setDirection(toward);
        }
        world.spawn(location, Villager.class, villager -> {
            villager.setProfession(npc.profession());
            villager.setVillagerType(Villager.Type.SNOW);
            villager.setVillagerLevel(5);
            villager.setAI(false);
            villager.setAware(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            villager.setCanPickupItems(false);
            villager.customName(Component.text(npc.title()));
            villager.setCustomNameVisible(false);
            villager.getPersistentDataContainer().set(plugin.key("role"), PersistentDataType.STRING, "npc:" + npc.id());
        });
        Location label = location.clone().add(0, 2.35, 0);
        Component text = plugin.text().mini("<gradient:#ff3355:#ffffff><bold>" + npc.title() + "</bold></gradient>")
                .append(Component.newline())
                .append(plugin.text().mini("<gray>" + npc.subtitle()));
        spawnText(label, text, "holo:" + npc.id(), 1.35f, 220);
    }

    private void frame(int x, int z) {
        for (int height = 1; height <= 3; height++) {
            world.getBlockAt(centerX + x, centerY + height, centerZ + z).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        world.getBlockAt(centerX + x, centerY + 4, centerZ + z).setType(Material.SOUL_LANTERN, false);
    }

    private void title() {
        Location location = new Location(world, centerX + 0.5, centerY + 4.4, centerZ + 0.5);
        Component text = plugin.text().mini("<gradient:#ff3355:#ffffff><bold>SHARPSMP</bold></gradient>")
                .append(Component.newline())
                .append(plugin.text().mini("<gray>The Edge"));
        spawnText(location, text, "holo:title", 3.1f, 900);
    }

    private void sword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(plugin.text().mini("<gradient:#ff3355:#ffffff><bold>Edge</bold></gradient>"));
        meta.setEnchantmentGlintOverride(true);
        sword.setItemMeta(meta);
        Location location = new Location(world, centerX + 0.5, centerY + 7.6, centerZ + 0.5);
        world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(sword);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(scale(2.4f));
            display.getPersistentDataContainer().set(plugin.key("role"), PersistentDataType.STRING, "decor:sword");
        });
    }

    private void leaderboard() {
        Location location = new Location(world, centerX + 16.5, centerY + 3.4, centerZ + 0.5);
        spawnText(location, plugin.text().mini("<gradient:#ff3355:#ffffff><bold>Leaders</bold></gradient>\n<gray>Rising..."), "holo:lb", 1.45f, 280);
    }

    private void spawnText(Location location, Component text, String role, float scale, int width) {
        world.spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setLineWidth(width);
            display.setViewRange(2.2f);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBackgroundColor(Color.fromARGB(150, 10, 8, 14));
            display.setTransformation(scale(scale));
            display.getPersistentDataContainer().set(plugin.key("role"), PersistentDataType.STRING, role);
        });
    }

    private Transformation scale(float size) {
        return new Transformation(
                new Vector3f(),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(size, size, size),
                new AxisAngle4f(0, 0, 1, 0)
        );
    }
}
