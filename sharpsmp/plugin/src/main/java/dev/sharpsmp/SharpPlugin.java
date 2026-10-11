package dev.sharpsmp;

import dev.sharpsmp.cmd.Commands;
import dev.sharpsmp.data.AuctionHouse;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.data.ProfileStore;
import dev.sharpsmp.data.Teams;
import dev.sharpsmp.data.WarpBook;
import dev.sharpsmp.game.Catalog;
import dev.sharpsmp.game.CombatLog;
import dev.sharpsmp.game.Deaths;
import dev.sharpsmp.game.Prompts;
import dev.sharpsmp.game.Rtp;
import dev.sharpsmp.game.ScoreboardService;
import dev.sharpsmp.game.SpawnService;
import dev.sharpsmp.game.Teleports;
import dev.sharpsmp.game.Whispers;
import dev.sharpsmp.gui.GuiListener;
import dev.sharpsmp.gui.MenuActions;
import dev.sharpsmp.gui.Menus;
import dev.sharpsmp.listen.ChatListener;
import dev.sharpsmp.listen.PlayerListener;
import dev.sharpsmp.listen.WorldListener;
import dev.sharpsmp.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class SharpPlugin extends JavaPlugin {
    private Text text;
    private ProfileStore profiles;
    private AuctionHouse auctions;
    private Teams teams;
    private WarpBook warps;
    private Catalog catalog;
    private CombatLog combat;
    private Teleports teleports;
    private Prompts prompts;
    private Whispers whispers;
    private Deaths deaths;
    private Rtp rtp;
    private ScoreboardService boards;
    private SpawnService spawn;
    private Menus menus;
    private MenuActions actions;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            reloadConfig();
            text = new Text(this);
            profiles = new ProfileStore(this);
            auctions = new AuctionHouse(this);
            teams = new Teams(this);
            warps = new WarpBook(this);
            catalog = new Catalog(this);
            combat = new CombatLog(this);
            teleports = new Teleports(this);
            prompts = new Prompts(this);
            whispers = new Whispers(this);
            deaths = new Deaths(this);
            rtp = new Rtp(this);
            boards = new ScoreboardService(this);
            spawn = new SpawnService(this);
            menus = new Menus(this);
            actions = new MenuActions(this);

            profiles.load();
            auctions.load();
            teams.load();
            warps.load();
            catalog.load();

            var events = getServer().getPluginManager();
            events.registerEvents(new PlayerListener(this), this);
            events.registerEvents(new WorldListener(this), this);
            events.registerEvents(new ChatListener(this), this);
            events.registerEvents(new GuiListener(this), this);

            Commands commands = new Commands(this);
            for (String name : getDescription().getCommands().keySet()) {
                var command = getCommand(name);
                if (command == null) {
                    getLogger().severe("Command missing from plugin.yml: " + name);
                    continue;
                }
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }

            Bukkit.getScheduler().runTaskTimer(this, () -> {
                boards.tick();
                combat.tick();
                teleports.tick();
            }, 20L, 20L);
            Bukkit.getScheduler().runTaskTimer(this, spawn::tick, 10L, 10L);
            Bukkit.getScheduler().runTaskTimer(this, this::minute, 1200L, 1200L);
            spawn.begin();
            getLogger().info("SharpSMP enabled. The Edge, Sharps, bounties, and the auction house are live.");
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "SharpSMP failed to start", ex);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (actions != null) {
            actions.flush();
        }
        if (spawn != null) {
            spawn.shutdown();
        }
        if (profiles != null) {
            profiles.save();
        }
        if (auctions != null) {
            auctions.save();
        }
        if (teams != null) {
            teams.save();
        }
        if (warps != null) {
            warps.save();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void minute() {
        int perKey = getConfig().getInt("crates.playtime-minutes-per-key", 30);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = profiles.get(player);
            if (profile.tickMinute(perKey)) {
                player.sendMessage(text.msg("playtime-key"));
            }
        }
        long now = System.currentTimeMillis();
        for (AuctionHouse.Listing listing : auctions.expire(now)) {
            PlayerProfile seller = profiles.get(listing.seller);
            boolean stored = seller != null && seller.offerMailbox(listing.item);
            if (!stored) {
                Player online = Bukkit.getPlayer(listing.seller);
                if (online != null) {
                    online.getWorld().dropItemNaturally(online.getLocation(), listing.item);
                } else if (spawn.location() != null) {
                    spawn.location().getWorld().dropItemNaturally(spawn.location(), listing.item);
                }
            }
            Player online = Bukkit.getPlayer(listing.seller);
            if (online != null) {
                online.sendMessage(text.msg("ah-expired"));
            }
        }
        if (spawn != null) {
            spawn.refreshBoard();
        }
        profiles.save();
    }

    public void refresh(Player player) {
        if (player != null && player.isOnline()) {
            boards.tick();
        }
    }

    public void give(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
        if (leftover.isEmpty()) {
            return;
        }
        PlayerProfile profile = profiles.get(player);
        boolean mailed = false;
        for (ItemStack extra : leftover.values()) {
            if (!profile.offerMailbox(extra)) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            } else {
                mailed = true;
            }
        }
        if (mailed) {
            player.sendMessage(text.msg("mailbox-stored"));
        }
    }

    public boolean isStar(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(key("star"), PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public void play(Player player, Sound sound, float pitch) {
        if (player != null && profiles.get(player).setting("sounds")) {
            player.playSound(player.getLocation(), sound, 0.6f, pitch);
        }
    }

    public NamespacedKey key(String id) {
        return new NamespacedKey(this, id);
    }

    public Text text() {
        return text;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public AuctionHouse auctions() {
        return auctions;
    }

    public Teams teams() {
        return teams;
    }

    public WarpBook warps() {
        return warps;
    }

    public Catalog catalog() {
        return catalog;
    }

    public CombatLog combat() {
        return combat;
    }

    public Teleports teleports() {
        return teleports;
    }

    public Prompts prompts() {
        return prompts;
    }

    public Whispers whispers() {
        return whispers;
    }

    public Deaths deaths() {
        return deaths;
    }

    public Rtp rtp() {
        return rtp;
    }

    public ScoreboardService boards() {
        return boards;
    }

    public SpawnService spawn() {
        return spawn;
    }

    public Menus menus() {
        return menus;
    }

    public MenuActions actions() {
        return actions;
    }
}
