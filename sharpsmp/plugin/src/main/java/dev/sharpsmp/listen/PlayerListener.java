package dev.sharpsmp.listen;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.game.CombatLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerListener implements Listener {
    private final SharpPlugin plugin;
    private final Map<UUID, Long> debounce = new ConcurrentHashMap<>();

    public PlayerListener(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = plugin.profiles().get(player);
        boolean first = !profile.welcomed();
        event.joinMessage(null);
        if (!first) {
            Component joined = plugin.text().banner("joined", plugin.text().vars("player", player.getName()));
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(player) || plugin.profiles().get(viewer).setting("join-messages")) {
                    viewer.sendMessage(joined);
                }
            }
        }
        if (!plugin.spawn().ready()) {
            player.sendMessage(plugin.text().msg("spawn-rising"));
        }
        plugin.spawn().whenReady(() -> welcome(player, first));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        Component left = plugin.text().banner("quit", plugin.text().vars("player", player.getName()));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && plugin.profiles().get(viewer).setting("join-messages")) {
                viewer.sendMessage(left);
            }
        }
        if (plugin.combat().tagged(player)) {
            punishLogout(player);
        }
        plugin.boards().forget(player);
        plugin.profiles().markDirty();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (plugin.combat().loggingOut(victim)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.deathMessage(null);
            return;
        }
        event.deathMessage(null);
        event.getDrops().removeIf(plugin::isStar);
        Player killer = victim.getKiller();
        if (killer == null) {
            killer = plugin.combat().attacker(victim);
        }
        plugin.deaths().handle(victim, killer, false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!event.isBedSpawn() && !event.isAnchorSpawn()) {
            Location location = plugin.spawn().location();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                return;
            }
            plugin.spawn().giveStar(event.getPlayer());
            plugin.refresh(event.getPlayer());
        }, 1L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.isStar(event.getItemDrop().getItemStack()) && plugin.profiles().get(event.getPlayer()).setting("star")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntity(PlayerInteractEntityEvent event) {
        String id = plugin.spawn().npcId(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (!tap(event.getPlayer())) {
            return;
        }
        openNpc(event.getPlayer(), id);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.isStar(event.getItem())) {
            return;
        }
        Entity target = event.getPlayer().getTargetEntity(4);
        if (plugin.spawn().npcId(target) != null) {
            return;
        }
        event.setCancelled(true);
        if (!tap(event.getPlayer())) {
            return;
        }
        plugin.menus().openMain(event.getPlayer());
        plugin.play(event.getPlayer(), Sound.UI_BUTTON_CLICK, 1.1f);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.combat().tagged(event.getPlayer())) {
            return;
        }
        String raw = event.getMessage().toLowerCase(Locale.ROOT);
        if (raw.length() < 2) {
            return;
        }
        String name = raw.substring(1).split(" ", 2)[0];
        int split = name.indexOf(':');
        if (split >= 0) {
            name = name.substring(split + 1);
        }
        if (CombatLog.BLOCKED.contains(name)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.text().msg("combat-blocked", plugin.text().vars("time", String.valueOf(plugin.combat().secondsLeft(event.getPlayer())))));
        }
    }

    private void welcome(Player player, boolean first) {
        if (!player.isOnline()) {
            return;
        }
        boolean teleport = first || plugin.getConfig().getBoolean("spawn.on-join", true);
        if (teleport) {
            Location location = plugin.spawn().location();
            if (location != null) {
                player.teleport(location);
                player.setFallDistance(0);
            }
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (first) {
            profile.welcomed(true);
            plugin.catalog().giveKit(player, "starter");
            player.showTitle(Title.title(
                    plugin.text().mini("<gradient:#ff3355:#ffffff><bold>SHARPSMP</bold></gradient>"),
                    plugin.text().mini("<gray>The Edge"),
                    Title.Times.times(Duration.ofMillis(350), Duration.ofMillis(2800), Duration.ofMillis(700))
            ));
            player.sendMessage(plugin.text().banner("welcome-1", plugin.text().vars("player", player.getName())));
            player.sendMessage(plugin.text().banner("welcome-2"));
            player.sendMessage(plugin.text().banner("welcome-3"));
            plugin.profiles().markDirty();
        } else {
            player.sendActionBar(plugin.text().mini("<gray>Welcome back to <white>The Edge"));
        }
        plugin.spawn().giveStar(player);
        if (!profile.mailbox().isEmpty()) {
            player.sendMessage(plugin.text().msg("mailbox-waiting", plugin.text().vars("count", String.valueOf(profile.mailbox().size()))));
        }
        plugin.refresh(player);
    }

    private void punishLogout(Player player) {
        plugin.combat().markLogout(player);
        List<ItemStack> drops = new ArrayList<>();
        collect(drops, player.getInventory().getStorageContents());
        collect(drops, player.getInventory().getArmorContents());
        collect(drops, player.getInventory().getExtraContents());
        player.getInventory().clear();
        Location location = player.getLocation();
        for (ItemStack drop : drops) {
            location.getWorld().dropItemNaturally(location, drop);
        }
        Player killer = plugin.combat().attacker(player);
        plugin.deaths().handle(player, killer, true);
        player.setHealth(0);
    }

    private void collect(List<ItemStack> drops, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir() && !plugin.isStar(stack)) {
                drops.add(stack.clone());
            }
        }
    }

    private void openNpc(Player player, String id) {
        plugin.play(player, Sound.UI_BUTTON_CLICK, 1.05f);
        switch (id) {
            case "shop" -> plugin.menus().openShop(player);
            case "auction" -> plugin.menus().openAuction(player, 0);
            case "bounties" -> plugin.menus().openBounties(player);
            case "crates" -> plugin.menus().openCrates(player);
            case "warps" -> plugin.menus().openWarps(player);
            case "settings" -> plugin.menus().openSettings(player);
            case "teams" -> plugin.menus().openTeams(player);
            case "leaderboard" -> plugin.menus().openLeaderboard(player, "balance");
            default -> plugin.menus().openMain(player);
        }
    }

    private boolean tap(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long previous = debounce.put(player.getUniqueId(), now);
        return previous == null || now - previous > 250;
    }
}
