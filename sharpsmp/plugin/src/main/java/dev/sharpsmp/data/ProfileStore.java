package dev.sharpsmp.data;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.game.Settings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ProfileStore {
    private final SharpPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private boolean saveQueued;

    public ProfileStore(SharpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        profiles.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            try {
                profiles.put(UUID.fromString(id), read(UUID.fromString(id), root.getConfigurationSection(id)));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Skipped unreadable profile " + id, ex);
            }
        }
        plugin.getLogger().info("Loaded " + profiles.size() + " player profiles.");
    }

    public void save() {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerProfile profile : profiles.values()) {
            write(yaml, profile);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player profiles", ex);
        }
    }

    public void markDirty() {
        if (saveQueued) {
            return;
        }
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveQueued = false;
            save();
        }, 40L);
    }

    public PlayerProfile get(Player player) {
        PlayerProfile profile = profiles.computeIfAbsent(player.getUniqueId(), id -> create(id, player.getName()));
        profile.name(player.getName());
        return profile;
    }

    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    public PlayerProfile find(String name) {
        if (name == null) {
            return null;
        }
        for (PlayerProfile profile : profiles.values()) {
            if (profile.name() != null && profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? null : get(online);
    }

    public List<PlayerProfile> all() {
        return List.copyOf(profiles.values());
    }

    private PlayerProfile create(UUID uuid, String name) {
        PlayerProfile profile = new PlayerProfile(uuid, name, plugin.getConfig().getLong("currency.starting-balance", 1500));
        for (Settings.Option option : Settings.ALL) {
            profile.setting(option.id(), true);
        }
        return profile;
    }

    private PlayerProfile read(UUID uuid, ConfigurationSection section) {
        PlayerProfile profile = new PlayerProfile(uuid, section.getString("name", "Unknown"), section.getLong("balance"));
        profile.kills(section.getInt("kills"));
        profile.deaths(section.getInt("deaths"));
        profile.streak(section.getInt("streak"));
        profile.bestStreak(section.getInt("best-streak"));
        profile.playtimeMinutes(section.getLong("playtime"));
        profile.keyProgress(section.getInt("key-progress"));
        profile.bounty(section.getLong("bounty"));
        profile.welcomed(section.getBoolean("welcomed"));
        profile.dailyAt(section.getLong("daily-at"));
        ConfigurationSection settings = section.getConfigurationSection("settings");
        if (settings != null) {
            for (String key : settings.getKeys(false)) {
                profile.setting(key, settings.getBoolean(key));
            }
        }
        ConfigurationSection homes = section.getConfigurationSection("homes");
        if (homes != null) {
            for (String name : homes.getKeys(false)) {
                ConfigurationSection home = homes.getConfigurationSection(name);
                if (home == null) {
                    continue;
                }
                profile.homes().put(name.toLowerCase(Locale.ROOT), new Point(
                        home.getString("world", "world"),
                        home.getDouble("x"),
                        home.getDouble("y"),
                        home.getDouble("z"),
                        (float) home.getDouble("yaw"),
                        (float) home.getDouble("pitch")
                ));
            }
        }
        ConfigurationSection keys = section.getConfigurationSection("keys");
        if (keys != null) {
            for (String key : keys.getKeys(false)) {
                profile.keys(key, keys.getInt(key));
            }
        }
        ConfigurationSection kits = section.getConfigurationSection("kit-ready");
        if (kits != null) {
            for (String key : kits.getKeys(false)) {
                profile.kitReadyAt(key, kits.getLong(key));
            }
        }
        profile.kitsClaimed().addAll(section.getStringList("kits-claimed"));
        List<?> mailbox = section.getList("mailbox");
        if (mailbox != null) {
            for (Object entry : mailbox) {
                ItemStack stack = asItem(entry);
                if (stack != null) {
                    profile.mailbox().add(stack);
                }
            }
        }
        return profile;
    }

    private void write(YamlConfiguration yaml, PlayerProfile profile) {
        String path = "players." + profile.uuid();
        yaml.set(path + ".name", profile.name());
        yaml.set(path + ".balance", profile.balance());
        yaml.set(path + ".kills", profile.kills());
        yaml.set(path + ".deaths", profile.deaths());
        yaml.set(path + ".streak", profile.streak());
        yaml.set(path + ".best-streak", profile.bestStreak());
        yaml.set(path + ".playtime", profile.playtimeMinutes());
        yaml.set(path + ".key-progress", profile.keyProgress());
        yaml.set(path + ".bounty", profile.bounty());
        yaml.set(path + ".welcomed", profile.welcomed());
        yaml.set(path + ".daily-at", profile.dailyAt());
        for (Settings.Option option : Settings.ALL) {
            yaml.set(path + ".settings." + option.id(), profile.setting(option.id()));
        }
        for (Map.Entry<String, Point> entry : profile.homes().entrySet()) {
            Point point = entry.getValue();
            String home = path + ".homes." + entry.getKey();
            yaml.set(home + ".world", point.world());
            yaml.set(home + ".x", point.x());
            yaml.set(home + ".y", point.y());
            yaml.set(home + ".z", point.z());
            yaml.set(home + ".yaw", point.yaw());
            yaml.set(home + ".pitch", point.pitch());
        }
        for (Map.Entry<String, Integer> entry : profile.keys().entrySet()) {
            yaml.set(path + ".keys." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : profile.kitReadyAt().entrySet()) {
            yaml.set(path + ".kit-ready." + entry.getKey(), entry.getValue());
        }
        yaml.set(path + ".kits-claimed", new ArrayList<>(profile.kitsClaimed()));
        yaml.set(path + ".mailbox", new ArrayList<>(profile.mailbox()));
    }

    @SuppressWarnings("unchecked")
    private ItemStack asItem(Object entry) {
        if (entry instanceof ItemStack stack && !stack.getType().isAir()) {
            return stack;
        }
        if (entry instanceof Map<?, ?> map) {
            try {
                Object decoded = ConfigurationSerialization.deserializeObject((Map<String, Object>) map);
                if (decoded instanceof ItemStack stack && !stack.getType().isAir()) {
                    return stack;
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
