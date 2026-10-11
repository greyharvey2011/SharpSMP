package dev.sharpsmp.data;

import dev.sharpsmp.SharpPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class WarpBook {
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final SharpPlugin plugin;
    private final File file;
    private final Map<String, Point> warps = new LinkedHashMap<>();

    public WarpBook(SharpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
    }

    public void load() {
        warps.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("warps");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            warps.put(name.toLowerCase(Locale.ROOT), new Point(
                    section.getString("world", "world"),
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch")
            ));
        }
    }

    public void save() {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Point> entry : warps.entrySet()) {
            Point point = entry.getValue();
            String path = "warps." + entry.getKey();
            yaml.set(path + ".world", point.world());
            yaml.set(path + ".x", point.x());
            yaml.set(path + ".y", point.y());
            yaml.set(path + ".z", point.z());
            yaml.set(path + ".yaw", point.yaw());
            yaml.set(path + ".pitch", point.pitch());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save warps", ex);
        }
    }

    public List<String> names() {
        return List.copyOf(warps.keySet());
    }

    public Point get(String name) {
        if (name == null) {
            return null;
        }
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean set(String name, Point point) {
        if (name == null || !NAME.matcher(name).matches()) {
            return false;
        }
        warps.put(name.toLowerCase(Locale.ROOT), point);
        save();
        return true;
    }

    public boolean delete(String name) {
        if (warps.remove(name.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        save();
        return true;
    }
}
