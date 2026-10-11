package dev.sharpsmp.data;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.game.Settings;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class Teams {
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public static final class TeamData {
        public final String id;
        public volatile String name;
        public volatile String color;
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();
        public final Map<UUID, Long> invites = new ConcurrentHashMap<>();

        public TeamData(String id) {
            this.id = id;
        }

        public NamedTextColor namedColor() {
            return switch (color) {
                case "gold" -> NamedTextColor.GOLD;
                case "yellow" -> NamedTextColor.YELLOW;
                case "green" -> NamedTextColor.GREEN;
                case "aqua" -> NamedTextColor.AQUA;
                case "blue" -> NamedTextColor.BLUE;
                case "light_purple" -> NamedTextColor.LIGHT_PURPLE;
                case "dark_red" -> NamedTextColor.DARK_RED;
                case "white" -> NamedTextColor.WHITE;
                case "gray" -> NamedTextColor.GRAY;
                default -> NamedTextColor.RED;
            };
        }
    }

    private final SharpPlugin plugin;
    private final File file;
    private final Map<String, TeamData> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> byMember = new ConcurrentHashMap<>();

    public Teams(SharpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    public void load() {
        teams.clear();
        byMember.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("teams");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            TeamData team = new TeamData(id.toLowerCase(Locale.ROOT));
            team.name = section.getString("name", id);
            team.color = Settings.colorName(section.getString("color", "red"));
            team.leader = UUID.fromString(section.getString("leader"));
            for (String member : section.getStringList("members")) {
                team.members.add(UUID.fromString(member));
            }
            ConfigurationSection invites = section.getConfigurationSection("invites");
            if (invites != null) {
                for (String invite : invites.getKeys(false)) {
                    team.invites.put(UUID.fromString(invite), invites.getLong(invite));
                }
            }
            teams.put(team.id, team);
        }
        reindex();
    }

    public void save() {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (TeamData team : teams.values()) {
            String path = "teams." + team.id;
            yaml.set(path + ".name", team.name);
            yaml.set(path + ".color", team.color);
            yaml.set(path + ".leader", team.leader.toString());
            List<String> members = new ArrayList<>();
            for (UUID member : team.members) {
                members.add(member.toString());
            }
            yaml.set(path + ".members", members);
            for (Map.Entry<UUID, Long> invite : team.invites.entrySet()) {
                yaml.set(path + ".invites." + invite.getKey(), invite.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save teams", ex);
        }
    }

    public TeamData of(UUID uuid) {
        String id = byMember.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public TeamData byName(String name) {
        if (name == null) {
            return null;
        }
        return teams.get(name.toLowerCase(Locale.ROOT));
    }

    public List<TeamData> all() {
        return List.copyOf(teams.values());
    }

    public String create(UUID leader, String name, String color) {
        if (of(leader) != null) {
            return "in-team";
        }
        if (name == null || !NAME.matcher(name).matches()) {
            return "bad-name";
        }
        String id = name.toLowerCase(Locale.ROOT);
        if (teams.containsKey(id)) {
            return "taken";
        }
        TeamData team = new TeamData(id);
        team.name = name;
        team.color = Settings.colorName(color);
        team.leader = leader;
        team.members.add(leader);
        teams.put(id, team);
        reindex();
        save();
        return null;
    }

    public String invite(UUID leader, UUID target, long expiresAt) {
        TeamData team = of(leader);
        if (team == null) {
            return "team-none";
        }
        if (!team.leader.equals(leader)) {
            return "team-not-leader";
        }
        if (of(target) != null) {
            return "target-in-team";
        }
        if (team.members.size() >= plugin.getConfig().getInt("teams.max-members", 8)) {
            return "team-full";
        }
        team.invites.put(target, expiresAt);
        save();
        return null;
    }

    public String join(UUID player, String name) {
        if (of(player) != null) {
            return "in-team";
        }
        TeamData team = byName(name);
        if (team == null) {
            return "missing";
        }
        Long until = team.invites.get(player);
        if (until == null || until < System.currentTimeMillis()) {
            team.invites.remove(player);
            return "team-no-invite";
        }
        if (team.members.size() >= plugin.getConfig().getInt("teams.max-members", 8)) {
            return "team-full";
        }
        team.invites.remove(player);
        team.members.add(player);
        reindex();
        save();
        return null;
    }

    public String leave(UUID player) {
        TeamData team = of(player);
        if (team == null) {
            return "team-none";
        }
        if (team.leader.equals(player)) {
            team.members.remove(player);
            if (team.members.isEmpty()) {
                teams.remove(team.id);
            } else {
                team.leader = team.members.iterator().next();
            }
        } else {
            team.members.remove(player);
        }
        reindex();
        save();
        return null;
    }

    public String kick(UUID leader, UUID target) {
        TeamData team = of(leader);
        if (team == null) {
            return "team-none";
        }
        if (!team.leader.equals(leader)) {
            return "team-not-leader";
        }
        if (leader.equals(target) || !team.members.contains(target)) {
            return "missing";
        }
        team.members.remove(target);
        reindex();
        save();
        return null;
    }

    public String disband(UUID leader) {
        TeamData team = of(leader);
        if (team == null) {
            return "team-none";
        }
        if (!team.leader.equals(leader)) {
            return "team-not-leader";
        }
        teams.remove(team.id);
        reindex();
        save();
        return null;
    }

    public String color(UUID leader, String color) {
        TeamData team = of(leader);
        if (team == null) {
            return "team-none";
        }
        if (!team.leader.equals(leader)) {
            return "team-not-leader";
        }
        team.color = Settings.colorName(color);
        save();
        return null;
    }

    public String transfer(UUID leader, UUID target) {
        TeamData team = of(leader);
        if (team == null) {
            return "team-none";
        }
        if (!team.leader.equals(leader)) {
            return "team-not-leader";
        }
        if (!team.members.contains(target)) {
            return "missing";
        }
        team.leader = target;
        save();
        return null;
    }

    private void reindex() {
        byMember.clear();
        for (TeamData team : teams.values()) {
            for (UUID member : team.members) {
                byMember.put(member, team.id);
            }
        }
    }
}
