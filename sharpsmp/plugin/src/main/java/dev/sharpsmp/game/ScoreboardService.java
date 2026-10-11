package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.data.Teams;
import dev.sharpsmp.text.Format;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardService {
    private static final String[] ENTRIES = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r",
            "§6§r", "§7§r", "§8§r", "§9§r", "§a§r", "§b§r"
    };
    private static final Component RULE = Component.text("--------------", NamedTextColor.DARK_GRAY);

    private final SharpPlugin plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    public ScoreboardService(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void clear() {
        views.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void tick() {
        Component header = plugin.text().mini(plugin.getConfig().getString("scoreboard.header", "<gradient:#ff3355:#ffffff><bold>SharpSMP</bold></gradient>\n<gray>The Edge"));
        int online = Bukkit.getOnlinePlayers().size();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = plugin.profiles().get(player);
            Component footer = plugin.text().mini("<gray>" + plugin.text().money(profile.balance())
                    + " <dark_gray>| <gray>Online <white>" + online);
            player.sendPlayerListHeaderAndFooter(header, footer);
            identity(player, profile);
            if (!profile.setting("scoreboard")) {
                if (views.remove(player.getUniqueId()) != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }
            View view = views.get(player.getUniqueId());
            if (view == null) {
                view = create(player);
                views.put(player.getUniqueId(), view);
                player.setScoreboard(view.board);
            }
            Component[] lines = lines(player, profile);
            for (int i = 0; i < view.teams.length; i++) {
                view.teams[i].prefix(lines[i]);
            }
        }
    }

    public void forget(Player player) {
        views.remove(player.getUniqueId());
    }

    private void identity(Player player, PlayerProfile profile) {
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        Component name = Component.text(player.getName(), NamedTextColor.WHITE);
        if (team != null) {
            name = Component.text(team.name, team.namedColor())
                    .append(Component.space())
                    .append(Component.text(player.getName(), NamedTextColor.WHITE));
        }
        if (profile.bounty() > 0) {
            name = name.append(Component.text(" " + plugin.text().money(profile.bounty()), NamedTextColor.RED));
        }
        player.playerListName(name);
    }

    private View create(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Component title = plugin.text().mini(plugin.getConfig().getString("scoreboard.title", "<gradient:#ff3355:#ffffff><bold>SharpSMP</bold></gradient>"));
        Objective objective = board.registerNewObjective("sharp", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team[] teams = new Team[ENTRIES.length];
        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = board.registerNewTeam("l" + i);
            team.addEntry(ENTRIES[i]);
            objective.getScore(ENTRIES[i]).setScore(ENTRIES.length - i);
            objective.getScore(ENTRIES[i]).numberFormat(NumberFormat.blank());
            teams[i] = team;
        }
        return new View(board, teams);
    }

    private Component[] lines(Player player, PlayerProfile profile) {
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        String teamName = team == null ? "None" : team.name;
        NamedTextColor teamColor = team == null ? NamedTextColor.DARK_GRAY : team.namedColor();
        Component bounty = profile.bounty() <= 0
                ? Component.text("None", NamedTextColor.DARK_GRAY)
                : Component.text(plugin.text().money(profile.bounty()), NamedTextColor.RED);
        return new Component[]{
                RULE,
                Component.text(player.getName(), NamedTextColor.WHITE),
                RULE,
                label("Sharps ", plugin.text().money(profile.balance())),
                label("Kills ", String.valueOf(profile.kills())),
                label("Deaths ", String.valueOf(profile.deaths())),
                label("Streak ", String.valueOf(profile.streak())),
                Component.text("Bounty ", NamedTextColor.GRAY).append(bounty),
                Component.text("Team ", NamedTextColor.GRAY).append(Component.text(teamName, teamColor)),
                label("Playtime ", Format.playtime(profile.playtimeMinutes())),
                RULE,
                label("Online ", String.valueOf(Bukkit.getOnlinePlayers().size()))
        };
    }

    private Component label(String caption, String value) {
        return Component.text(caption, NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private record View(Scoreboard board, Team[] teams) {
    }
}
