package dev.sharpsmp.cmd;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.data.Point;
import dev.sharpsmp.data.Teams;
import dev.sharpsmp.game.Settings;
import dev.sharpsmp.text.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Commands implements CommandExecutor, TabCompleter {
    private record Request(UUID from, boolean here, long expires) {
    }

    private final SharpPlugin plugin;
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();

    public Commands(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "sharpsmp" -> admin(sender, args);
                case "spawn" -> spawn(sender);
                case "balance" -> balance(sender, args);
                case "pay" -> pay(sender, args);
                case "bounty" -> bounty(sender, args);
                case "menu" -> open(sender, player -> plugin.menus().openMain(player));
                case "settings" -> open(sender, player -> plugin.menus().openSettings(player));
                case "shop" -> open(sender, player -> plugin.menus().openShop(player));
                case "sell" -> sell(sender, args);
                case "auction" -> auction(sender, args);
                case "rtp" -> open(sender, player -> plugin.rtp().go(player));
                case "home" -> home(sender, args);
                case "sethome" -> setHome(sender, args);
                case "delhome" -> deleteHome(sender, args);
                case "homes" -> open(sender, player -> plugin.menus().openHomes(player));
                case "tpa" -> tpa(sender, args, false);
                case "tpahere" -> tpa(sender, args, true);
                case "tpaccept" -> tpaAnswer(sender, true);
                case "tpdeny" -> tpaAnswer(sender, false);
                case "team" -> team(sender, args);
                case "teamchat" -> teamChat(sender, args);
                case "kit" -> kit(sender, args);
                case "warp" -> warp(sender, args);
                case "crates" -> open(sender, player -> plugin.menus().openCrates(player));
                case "leaderboard" -> open(sender, player -> plugin.menus().openLeaderboard(player, "balance"));
                case "stats" -> stats(sender, args);
                case "daily" -> open(sender, player -> plugin.actions().claimDaily(player));
                case "msg" -> message(sender, args);
                case "reply" -> reply(sender, args);
                default -> false;
            };
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Command /" + label + " failed: " + ex.getMessage());
            sender.sendMessage(plugin.text().msg("invalid-amount"));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        String token = args.length == 0 ? "" : args[args.length - 1];
        return switch (name) {
            case "sharpsmp" -> args.length == 1
                    ? match(token, List.of("help", "give", "take", "set", "key", "reload", "save", "buildspawn", "setwarp", "delwarp"))
                    : args.length == 2 ? players(token) : List.of();
            case "balance", "pay", "msg", "stats", "tpa", "tpahere" -> args.length == 1 ? players(token) : List.of();
            case "bounty" -> args.length == 1 ? match(token, List.of("set", "check")) : args.length == 2 ? players(token) : List.of();
            case "sell" -> args.length == 1 ? match(token, List.of("hand", "all")) : List.of();
            case "auction" -> args.length == 1 ? match(token, List.of("sell")) : List.of();
            case "home", "delhome" -> args.length == 1 && sender instanceof Player player
                    ? match(token, new ArrayList<>(plugin.profiles().get(player).homes().keySet()))
                    : List.of();
            case "kit" -> args.length == 1 ? match(token, plugin.catalog().kits().stream().map(kit -> kit.id).toList()) : List.of();
            case "warp" -> args.length == 1 ? match(token, plugin.warps().names()) : List.of();
            case "team" -> teamComplete(sender, args, token);
            case "reply", "teamchat" -> List.of();
            default -> List.of();
        };
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(plugin.text().mini("<gradient:#ff3355:#ffffff><bold>SharpSMP</bold></gradient> <gray>commands"));
            for (String line : List.of(
                    "/menu  /settings  /daily  /stats",
                    "/balance  /pay  /sell  /shop  /ah",
                    "/bounty  /crates  /kit  /team  /tc",
                    "/spawn  /rtp  /home  /sethome  /warp",
                    "/tpa  /tpaccept  /msg"
            )) {
                sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
            return true;
        }
        if (!sender.hasPermission("sharpsmp.admin")) {
            sender.sendMessage(plugin.text().msg("no-permission"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.catalog().load();
                plugin.boards().clear();
                sender.sendMessage(plugin.text().msg("reloaded"));
            }
            case "save" -> {
                plugin.profiles().save();
                plugin.auctions().save();
                plugin.teams().save();
                plugin.warps().save();
                sender.sendMessage(plugin.text().msg("saved"));
            }
            case "buildspawn" -> {
                if (plugin.spawn().rebuild()) {
                    sender.sendMessage(plugin.text().msg("building"));
                } else {
                    sender.sendMessage(plugin.text().msg("building"));
                }
            }
            case "setwarp" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage(plugin.text().msg("players-only"));
                    return true;
                }
                if (plugin.warps().set(args[1], Point.of(player.getLocation()))) {
                    sender.sendMessage(plugin.text().msg("warp-set", plugin.text().vars("warp", args[1].toLowerCase(Locale.ROOT))));
                } else {
                    sender.sendMessage(plugin.text().msg("home-bad"));
                }
            }
            case "delwarp" -> {
                if (args.length < 2) {
                    return true;
                }
                sender.sendMessage(plugin.text().msg(plugin.warps().delete(args[1]) ? "warp-deleted" : "warp-missing", plugin.text().vars("warp", args[1])));
            }
            case "give", "take", "set" -> adjust(sender, args);
            case "key" -> giveKey(sender, args);
            default -> sender.sendMessage(plugin.text().msg("no-permission"));
        }
        return true;
    }

    private void adjust(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.text().mini("<gray>/sharpsmp " + args[0] + " <player> <amount>"));
            return;
        }
        PlayerProfile profile = plugin.profiles().find(args[1]);
        Long amount = Format.amount(args[2]);
        if (profile == null) {
            sender.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[1])));
            return;
        }
        if (amount == null) {
            sender.sendMessage(plugin.text().msg("invalid-amount"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> profile.deposit(amount);
            case "take" -> {
                if (!profile.withdraw(amount)) {
                    sender.sendMessage(plugin.text().msg("broke"));
                    return;
                }
            }
            default -> profile.balance(amount);
        }
        plugin.profiles().markDirty();
        Player online = Bukkit.getPlayer(profile.uuid());
        if (online != null) {
            plugin.refresh(online);
        }
        sender.sendMessage(plugin.text().msg("balance-other", plugin.text().vars("player", profile.name(), "amount", plugin.text().money(profile.balance()))));
    }

    private void giveKey(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.text().mini("<gray>/sharpsmp key <player> <crate> <amount>"));
            return;
        }
        PlayerProfile profile = plugin.profiles().find(args[1]);
        Integer amount = parseSigned(args[3]);
        if (profile == null || plugin.catalog().crate(args[2]) == null || amount == null || amount == 0) {
            sender.sendMessage(plugin.text().msg("invalid-amount"));
            return;
        }
        profile.addKeys(args[2].toLowerCase(Locale.ROOT), amount);
        plugin.profiles().markDirty();
        sender.sendMessage(plugin.text().msg("key-given", plugin.text().vars("player", profile.name(), "key", args[2], "count", String.valueOf(profile.keys(args[2])))));
    }

    private boolean spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        int seconds = plugin.spawn().inSpawn(player.getLocation()) ? 0 : plugin.getConfig().getInt("spawn.delay-seconds", 5);
        plugin.teleports().later(player, plugin.spawn().location(), seconds, "The Edge");
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        PlayerProfile profile;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.text().msg("players-only"));
                return true;
            }
            profile = plugin.profiles().get(player);
            sender.sendMessage(plugin.text().msg("balance", plugin.text().vars("amount", plugin.text().money(profile.balance()))));
            return true;
        }
        profile = plugin.profiles().find(args[0]);
        if (profile == null) {
            sender.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[0])));
            return true;
        }
        sender.sendMessage(plugin.text().msg("balance-other", plugin.text().vars("player", profile.name(), "amount", plugin.text().money(profile.balance()))));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            sender.sendMessage(plugin.text().mini("<gray>/pay <player> <amount>"));
            return true;
        }
        PlayerProfile target = plugin.profiles().find(args[0]);
        Long amount = Format.amount(args[1]);
        if (target == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[0])));
            return true;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.text().msg("pay-self"));
            return true;
        }
        if (amount == null) {
            player.sendMessage(plugin.text().msg("invalid-amount"));
            return true;
        }
        PlayerProfile from = plugin.profiles().get(player);
        if (!from.withdraw(amount)) {
            player.sendMessage(plugin.text().msg("broke"));
            return true;
        }
        target.deposit(amount);
        player.sendMessage(plugin.text().msg("paid", plugin.text().vars("player", target.name(), "amount", plugin.text().money(amount))));
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null && plugin.profiles().get(online).setting("pay-alerts")) {
            online.sendMessage(plugin.text().msg("paid-received", plugin.text().vars("player", player.getName(), "amount", plugin.text().money(amount))));
            plugin.refresh(online);
        }
        plugin.refresh(player);
        plugin.profiles().markDirty();
        return true;
    }

    private boolean bounty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openBounties(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("check")) {
            PlayerProfile profile = args.length >= 2 ? plugin.profiles().find(args[1]) : plugin.profiles().get(player);
            if (profile == null) {
                player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args.length >= 2 ? args[1] : player.getName())));
                return true;
            }
            player.sendMessage(plugin.text().msg("bounty-check", plugin.text().vars("player", profile.name(), "amount", plugin.text().money(profile.bounty()))));
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 3) {
            PlayerProfile target = plugin.profiles().find(args[1]);
            Long amount = Format.amount(args[2]);
            if (target == null) {
                player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[1])));
                return true;
            }
            if (amount == null) {
                player.sendMessage(plugin.text().msg("invalid-amount"));
                return true;
            }
            plugin.actions().placeBounty(player, target, amount);
            return true;
        }
        player.sendMessage(plugin.text().mini("<gray>/bounty set <player> <amount>"));
        return true;
    }

    private boolean sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openSell(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("hand")) {
            plugin.actions().sellHand(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("all")) {
            plugin.actions().sellAll(player);
            return true;
        }
        player.sendMessage(plugin.text().mini("<gray>/sell [hand|all]"));
        return true;
    }

    private boolean auction(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("sell")) {
            Long price = Format.amount(args[1]);
            if (price == null) {
                player.sendMessage(plugin.text().msg("invalid-amount"));
                return true;
            }
            plugin.actions().finishListing(player, player.getInventory().getItemInMainHand().clone(), args[1]);
            return true;
        }
        plugin.menus().openAuction(player, 0);
        return true;
    }

    private boolean home(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (args.length == 0) {
            if (profile.homes().size() == 1) {
                plugin.actions().handle(player, "home:" + profile.homes().keySet().iterator().next(), null);
            } else {
                plugin.menus().openHomes(player);
            }
            return true;
        }
        plugin.actions().handle(player, "home:" + args[0], null);
        return true;
    }

    private boolean setHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        String name = args.length == 0 ? "home" : args[0];
        plugin.actions().setHome(player, name);
        return true;
    }

    private boolean deleteHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) {
            sender.sendMessage(plugin.text().mini("<gray>/delhome <name>"));
            return true;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        if (plugin.profiles().get(player).homes().remove(key) == null) {
            player.sendMessage(plugin.text().msg("home-missing"));
            return true;
        }
        player.sendMessage(plugin.text().msg("home-deleted", plugin.text().vars("home", key)));
        plugin.profiles().markDirty();
        return true;
    }

    private boolean tpa(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player player) || args.length < 1) {
            sender.sendMessage(plugin.text().mini(here ? "<gray>/tpahere <player>" : "<gray>/tpa <player>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[0])));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.text().msg("tpa-self"));
            return true;
        }
        if (!plugin.profiles().get(target).setting("tpa")) {
            player.sendMessage(plugin.text().msg("tpa-disabled", plugin.text().vars("player", target.getName())));
            return true;
        }
        requests.put(target.getUniqueId(), new Request(player.getUniqueId(), here, System.currentTimeMillis() + plugin.getConfig().getInt("tpa.timeout-seconds", 60) * 1000L));
        player.sendMessage(plugin.text().msg("tpa-sent", plugin.text().vars("player", target.getName())));
        target.sendMessage(plugin.text().msg(here ? "tpahere-received" : "tpa-received", plugin.text().vars("player", player.getName())));
        return true;
    }

    private boolean tpaAnswer(CommandSender sender, boolean accept) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        Request request = requests.remove(player.getUniqueId());
        if (request == null || request.expires < System.currentTimeMillis()) {
            player.sendMessage(plugin.text().msg("tpa-none"));
            return true;
        }
        Player from = Bukkit.getPlayer(request.from);
        if (from == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", "them")));
            return true;
        }
        if (!accept) {
            player.sendMessage(plugin.text().msg("tpa-denied", plugin.text().vars("player", from.getName())));
            from.sendMessage(plugin.text().msg("tpa-denied", plugin.text().vars("player", player.getName())));
            return true;
        }
        int delay = plugin.getConfig().getInt("tpa.delay-seconds", 3);
        if (request.here) {
            plugin.teleports().later(player, from.getLocation(), delay, from.getName());
        } else {
            plugin.teleports().later(from, player.getLocation(), delay, player.getName());
        }
        player.sendMessage(plugin.text().msg("tpa-accepted", plugin.text().vars("player", from.getName())));
        from.sendMessage(plugin.text().msg("tpa-accepted", plugin.text().vars("player", player.getName())));
        return true;
    }

    private boolean team(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openTeams(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.text().mini("<gray>/team create <name> [color]"));
                    return true;
                }
                plugin.actions().createTeam(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "invite" -> invite(player, args);
            case "join" -> join(player, args);
            case "leave" -> plugin.actions().handle(player, "team-leave", null);
            case "kick" -> kick(player, args);
            case "disband" -> plugin.actions().handle(player, "team-disband", null);
            case "color" -> color(player, args);
            case "transfer" -> transfer(player, args);
            default -> player.sendMessage(plugin.text().mini("<gray>/team create, invite, join, leave, kick, color, transfer, disband"));
        }
        return true;
    }

    private void invite(Player player, String[] args) {
        if (args.length < 2) {
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[1])));
            return;
        }
        long expires = System.currentTimeMillis() + 60_000L;
        String error = plugin.teams().invite(player.getUniqueId(), target.getUniqueId(), expires);
        if (error != null) {
            player.sendMessage(plugin.text().msg(mapTeam(error)));
            return;
        }
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        player.sendMessage(plugin.text().msg("team-invited", plugin.text().vars("player", target.getName())));
        target.sendMessage(plugin.text().msg("team-invite", plugin.text().vars("player", player.getName(), "team", team == null ? "team" : team.name)));
    }

    private void join(Player player, String[] args) {
        if (args.length < 2) {
            return;
        }
        String error = plugin.teams().join(player.getUniqueId(), args[1]);
        if (error != null) {
            player.sendMessage(plugin.text().msg(mapTeam(error)));
            return;
        }
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        player.sendMessage(plugin.text().msg("team-joined", plugin.text().vars("team", team == null ? args[1] : team.name)));
        plugin.refresh(player);
    }

    private void kick(Player player, String[] args) {
        if (args.length < 2) {
            return;
        }
        PlayerProfile target = plugin.profiles().find(args[1]);
        if (target == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[1])));
            return;
        }
        String error = plugin.teams().kick(player.getUniqueId(), target.uuid());
        if (error != null) {
            player.sendMessage(plugin.text().msg(mapTeam(error)));
            return;
        }
        player.sendMessage(plugin.text().msg("team-kicked", plugin.text().vars("player", target.name())));
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            online.sendMessage(plugin.text().msg("team-kicked", plugin.text().vars("player", target.name())));
            plugin.refresh(online);
        }
    }

    private void color(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.text().mini("<gray>Colors: " + String.join(", ", Settings.colors())));
            return;
        }
        String error = plugin.teams().color(player.getUniqueId(), args[1]);
        if (error != null) {
            player.sendMessage(plugin.text().msg(mapTeam(error)));
            return;
        }
        player.sendMessage(plugin.text().msg("team-color", plugin.text().vars("color", Settings.colorName(args[1]))));
        plugin.refresh(player);
    }

    private void transfer(Player player, String[] args) {
        if (args.length < 2) {
            return;
        }
        PlayerProfile target = plugin.profiles().find(args[1]);
        if (target == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[1])));
            return;
        }
        String error = plugin.teams().transfer(player.getUniqueId(), target.uuid());
        if (error != null) {
            player.sendMessage(plugin.text().msg(mapTeam(error)));
            return;
        }
        player.sendMessage(plugin.text().msg("team-transfer", plugin.text().vars("player", target.name())));
    }

    private boolean teamChat(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) {
            sender.sendMessage(plugin.text().mini("<gray>/tc <message>"));
            return true;
        }
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        if (team == null) {
            player.sendMessage(plugin.text().msg("team-none"));
            return true;
        }
        String text = String.join(" ", args);
        if (text.length() > 256) {
            text = text.substring(0, 256);
        }
        Component message = Component.text("[Team] ", NamedTextColor.DARK_GRAY)
                .append(Component.text(player.getName(), team.namedColor()))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(text, NamedTextColor.GRAY));
        for (UUID member : team.members) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(message);
            }
        }
        return true;
    }

    private boolean kit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openKits(player);
            return true;
        }
        plugin.actions().handle(player, "kit:" + args[0].toLowerCase(Locale.ROOT), null);
        return true;
    }

    private boolean warp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openWarps(player);
            return true;
        }
        plugin.actions().handle(player, "warp:" + args[0], null);
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        PlayerProfile profile;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.text().msg("players-only"));
                return true;
            }
            profile = plugin.profiles().get(player);
        } else {
            profile = plugin.profiles().find(args[0]);
        }
        if (profile == null) {
            sender.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args.length == 0 ? sender.getName() : args[0])));
            return true;
        }
        if (sender instanceof Player player) {
            plugin.menus().openStats(player, profile);
            return true;
        }
        sender.sendMessage(Component.text(profile.name() + " " + plugin.text().money(profile.balance())
                + " K" + profile.kills() + " D" + profile.deaths(), NamedTextColor.GRAY));
        return true;
    }

    private boolean message(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            sender.sendMessage(plugin.text().mini("<gray>/msg <player> <message>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.text().msg("not-found", plugin.text().vars("player", args[0])));
            return true;
        }
        plugin.whispers().send(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    private boolean reply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) {
            sender.sendMessage(plugin.text().mini("<gray>/r <message>"));
            return true;
        }
        Player target = plugin.whispers().last(player);
        if (target == null) {
            player.sendMessage(plugin.text().msg("reply-none"));
            return true;
        }
        plugin.whispers().send(player, target, String.join(" ", args));
        return true;
    }

    private boolean open(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().msg("players-only"));
            return true;
        }
        action.accept(player);
        plugin.play(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.05f);
        return true;
    }

    private List<String> teamComplete(CommandSender sender, String[] args, String token) {
        if (args.length == 1) {
            return match(token, List.of("create", "invite", "join", "leave", "kick", "disband", "color", "transfer"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            return match(token, Settings.colors());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("transfer"))) {
            return players(token);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return match(token, plugin.teams().all().stream().map(team -> team.name).toList());
        }
        return List.of();
    }

    private List<String> players(String token) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (PlayerProfile profile : plugin.profiles().all()) {
            if (profile.name() != null && !names.contains(profile.name())) {
                names.add(profile.name());
            }
        }
        return match(token, names);
    }

    private List<String> match(String token, List<String> options) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matched.add(option);
            }
            if (matched.size() >= 40) {
                break;
            }
        }
        return matched;
    }

    private String mapTeam(String error) {
        return switch (error) {
            case "in-team", "target-in-team" -> "team-already";
            case "bad-name" -> "team-bad-name";
            case "taken" -> "team-taken";
            case "missing" -> "not-found";
            default -> error;
        };
    }

    private Integer parseSigned(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
