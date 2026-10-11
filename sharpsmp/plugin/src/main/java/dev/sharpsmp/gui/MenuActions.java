package dev.sharpsmp.gui;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.AuctionHouse;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.data.Point;
import dev.sharpsmp.data.Teams;
import dev.sharpsmp.data.WarpBook;
import dev.sharpsmp.game.Catalog;
import dev.sharpsmp.game.Settings;
import dev.sharpsmp.text.Format;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuActions {
    private final SharpPlugin plugin;
    private final Map<UUID, Catalog.Reward> spinning = new ConcurrentHashMap<>();

    public MenuActions(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(Player player, String action, InventoryClickEvent event) {
        int times = event != null && event.isShiftClick() ? 5 : 1;
        if (action.equals("nav:close")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return true;
        }
        if (action.equals("nav:menu")) {
            plugin.menus().openMain(player);
            return true;
        }
        if (action.equals("nav:shop")) {
            plugin.menus().openShop(player);
            return true;
        }
        if (action.equals("nav:auction")) {
            plugin.menus().openAuction(player, 0);
            return true;
        }
        if (action.equals("nav:bounties")) {
            plugin.menus().openBounties(player);
            return true;
        }
        if (action.equals("nav:settings")) {
            plugin.menus().openSettings(player);
            return true;
        }
        if (action.equals("nav:crates")) {
            plugin.menus().openCrates(player);
            return true;
        }
        if (action.equals("nav:warps")) {
            plugin.menus().openWarps(player);
            return true;
        }
        if (action.equals("nav:kits")) {
            plugin.menus().openKits(player);
            return true;
        }
        if (action.equals("nav:teams")) {
            plugin.menus().openTeams(player);
            return true;
        }
        if (action.equals("nav:homes")) {
            plugin.menus().openHomes(player);
            return true;
        }
        if (action.equals("nav:sell")) {
            plugin.menus().openSell(player);
            return true;
        }
        if (action.equals("nav:mailbox")) {
            plugin.menus().openMailbox(player);
            return true;
        }
        if (action.equals("nav:stats")) {
            plugin.menus().openStats(player, plugin.profiles().get(player));
            return true;
        }
        if (action.equals("nav:lb")) {
            plugin.menus().openLeaderboard(player, "balance");
            return true;
        }
        if (action.equals("daily")) {
            return claimDaily(player);
        }
        if (action.equals("rtp")) {
            plugin.rtp().go(player);
            return true;
        }
        if (action.equals("spawn")) {
            return teleportSpawn(player);
        }
        if (action.equals("sell-all")) {
            return sellAll(player);
        }
        if (action.equals("sell-hand")) {
            return sellHand(player);
        }
        if (action.equals("ah-sell")) {
            return askListing(player);
        }
        if (action.equals("home-set")) {
            plugin.prompts().ask(player, plugin.text().msg("prompt-home"), text -> setHome(player, text));
            return true;
        }
        if (action.equals("team-create")) {
            plugin.prompts().ask(player, plugin.text().msg("prompt-team"), text -> createTeam(player, text));
            return true;
        }
        if (action.equals("team-leave")) {
            return leaveTeam(player);
        }
        if (action.equals("team-disband")) {
            return disbandTeam(player);
        }
        if (action.equals("mail-all")) {
            return claimMail(player, -1);
        }
        if (action.startsWith("toggle:")) {
            return toggle(player, action.substring(7));
        }
        if (action.startsWith("shop-cat:")) {
            plugin.menus().openCategory(player, action.substring(9), 0);
            return true;
        }
        if (action.startsWith("shop-page:")) {
            String[] parts = action.substring(10).split(":", 2);
            int page = parts.length == 2 ? parseInt(parts[1]) : 0;
            plugin.menus().openCategory(player, parts[0], page);
            return true;
        }
        if (action.startsWith("shop-buy:")) {
            String[] parts = action.substring(9).split(":", 2);
            if (parts.length < 2) {
                return false;
            }
            return buy(player, parts[0], parts[1], times);
        }
        if (action.startsWith("bounty-pick:")) {
            plugin.menus().openBountyAmounts(player, UUID.fromString(action.substring(12)));
            return true;
        }
        if (action.startsWith("bounty-custom:")) {
            UUID targetId = UUID.fromString(action.substring("bounty-custom:".length()));
            plugin.prompts().ask(player, plugin.text().msg("prompt-bounty"), text -> {
                Long amount = Format.amount(text);
                PlayerProfile target = plugin.profiles().get(targetId);
                if (amount == null || target == null) {
                    player.sendMessage(plugin.text().msg("invalid-amount"));
                    return;
                }
                placeBounty(player, target, amount);
            });
            return true;
        }
        if (action.startsWith("bounty-add:")) {
            String rest = action.substring("bounty-add:".length());
            int cut = rest.lastIndexOf(':');
            PlayerProfile target = plugin.profiles().get(UUID.fromString(rest.substring(0, cut)));
            long amount = Long.parseLong(rest.substring(cut + 1));
            if (target == null) {
                return false;
            }
            return placeBounty(player, target, amount);
        }
        if (action.startsWith("ah-page:")) {
            plugin.menus().openAuction(player, parseInt(action.substring(8)));
            return true;
        }
        if (action.startsWith("ah-buy:")) {
            AuctionHouse.Listing listing = plugin.auctions().find(UUID.fromString(action.substring(7)));
            if (listing == null) {
                plugin.menus().openAuction(player, 0);
                return false;
            }
            plugin.menus().openConfirm(player, "<gold>Buy listing", "Buy for " + plugin.text().money(listing.price) + "?", "ah-confirm-buy:" + listing.id, "nav:auction");
            return true;
        }
        if (action.startsWith("ah-cancel:")) {
            plugin.menus().openConfirm(player, "<gold>Collect listing", "Take this listing back?", "ah-confirm-cancel:" + action.substring(10), "nav:auction");
            return true;
        }
        if (action.startsWith("ah-confirm-buy:")) {
            return buyListing(player, UUID.fromString(action.substring("ah-confirm-buy:".length())));
        }
        if (action.startsWith("ah-confirm-cancel:")) {
            return cancelListing(player, UUID.fromString(action.substring("ah-confirm-cancel:".length())));
        }
        if (action.startsWith("crate:")) {
            return openCrate(player, action.substring(6));
        }
        if (action.startsWith("kit:")) {
            return giveKit(player, action.substring(4));
        }
        if (action.startsWith("warp:")) {
            return warp(player, action.substring(5));
        }
        if (action.startsWith("home:")) {
            return home(player, action.substring(5));
        }
        if (action.startsWith("lb:")) {
            plugin.menus().openLeaderboard(player, action.substring(3));
            return true;
        }
        if (action.startsWith("lb-stats:")) {
            PlayerProfile profile = plugin.profiles().get(UUID.fromString(action.substring(9)));
            if (profile == null) {
                return false;
            }
            plugin.menus().openStats(player, profile);
            return true;
        }
        if (action.startsWith("sell-slot:")) {
            return sellSlot(player, parseInt(action.substring(10)));
        }
        if (action.startsWith("mail:")) {
            return claimMail(player, parseInt(action.substring(5)));
        }
        return false;
    }

    public void flush() {
        for (Map.Entry<UUID, Catalog.Reward> entry : List.copyOf(spinning.entrySet())) {
            spinning.remove(entry.getKey());
            grant(entry.getKey(), entry.getValue(), false);
        }
    }

    public boolean claimDaily(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        long cooldown = plugin.getConfig().getInt("daily.cooldown-hours", 24) * 3_600_000L;
        long left = profile.dailyAt() + cooldown - System.currentTimeMillis();
        if (left > 0) {
            player.sendMessage(plugin.text().msg("daily-wait", plugin.text().vars("time", Format.duration(left / 1000))));
            return false;
        }
        long amount = plugin.getConfig().getLong("daily.amount", 500);
        String key = plugin.getConfig().getString("daily.key", "common");
        profile.dailyAt(System.currentTimeMillis());
        profile.deposit(amount);
        profile.addKeys(key, 1);
        player.sendMessage(plugin.text().msg("daily-claimed", plugin.text().vars("amount", plugin.text().money(amount), "key", key)));
        plugin.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.15f);
        plugin.refresh(player);
        plugin.profiles().markDirty();
        return true;
    }

    public boolean sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        long price = plugin.catalog().stackWorth(hand);
        if (price <= 0) {
            player.sendMessage(plugin.text().msg(hand.getType().isAir() ? "sell-none" : "sell-special"));
            return false;
        }
        player.getInventory().setItemInMainHand(null);
        plugin.profiles().get(player).deposit(price);
        player.sendMessage(plugin.text().msg("sell-done", plugin.text().vars("amount", plugin.text().money(price), "count", String.valueOf(hand.getAmount()))));
        plugin.refresh(player);
        plugin.profiles().markDirty();
        return true;
    }

    public boolean sellAll(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        long total = 0;
        int count = 0;
        for (int slot = 0; slot < storage.length; slot++) {
            long price = plugin.catalog().stackWorth(storage[slot]);
            if (price <= 0) {
                continue;
            }
            count += storage[slot].getAmount();
            total += price;
            storage[slot] = null;
        }
        if (total <= 0) {
            player.sendMessage(plugin.text().msg("sell-none"));
            return false;
        }
        player.getInventory().setStorageContents(storage);
        plugin.profiles().get(player).deposit(total);
        player.sendMessage(plugin.text().msg("sell-done", plugin.text().vars("amount", plugin.text().money(total), "count", String.valueOf(count))));
        plugin.refresh(player);
        plugin.profiles().markDirty();
        plugin.menus().openSell(player);
        return true;
    }

    public boolean placeBounty(Player setter, PlayerProfile target, long amount) {
        if (target.uuid().equals(setter.getUniqueId())) {
            setter.sendMessage(plugin.text().msg("bounty-self"));
            return false;
        }
        long min = plugin.getConfig().getLong("bounty.min", 100);
        long max = plugin.getConfig().getLong("bounty.max", 1_000_000_000L);
        if (amount < min) {
            setter.sendMessage(plugin.text().msg("bounty-min", plugin.text().vars("amount", plugin.text().money(min))));
            return false;
        }
        if (target.bounty() > max - amount) {
            setter.sendMessage(plugin.text().msg("bounty-max", plugin.text().vars("amount", plugin.text().money(max))));
            return false;
        }
        Long cost = Format.cost(amount, plugin.getConfig().getInt("bounty.tax-percent", 5));
        if (cost == null) {
            setter.sendMessage(plugin.text().msg("invalid-amount"));
            return false;
        }
        PlayerProfile from = plugin.profiles().get(setter);
        if (!from.withdraw(cost)) {
            setter.sendMessage(plugin.text().msg("broke"));
            return false;
        }
        target.addBounty(amount);
        setter.sendMessage(plugin.text().msg("bounty-set", plugin.text().vars("player", target.name(), "amount", plugin.text().money(amount), "price", plugin.text().money(cost))));
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            online.sendMessage(plugin.text().msg("bounty-received", plugin.text().vars("player", setter.getName(), "amount", plugin.text().money(target.bounty()))));
            plugin.refresh(online);
        }
        if (amount >= plugin.getConfig().getLong("bounty.broadcast-minimum", 5000)) {
            Component banner = plugin.text().banner("bounty-broadcast", plugin.text().vars("player", setter.getName(), "target", target.name(), "amount", plugin.text().money(target.bounty())));
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(setter) || viewer.equals(online) || plugin.profiles().get(viewer).setting("bounty-alerts")) {
                    if (!viewer.equals(setter) && !viewer.equals(online)) {
                        viewer.sendMessage(banner);
                    }
                }
            }
            setter.sendMessage(banner);
            if (online != null && !online.equals(setter)) {
                online.sendMessage(banner);
            }
        }
        plugin.refresh(setter);
        plugin.profiles().markDirty();
        plugin.spawn().refreshBoard();
        return true;
    }

    public boolean setHome(Player player, String name) {
        if (name == null || !WarpBook.NAME.matcher(name).matches()) {
            player.sendMessage(plugin.text().msg("home-bad"));
            return false;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (!profile.homes().containsKey(key) && profile.homes().size() >= plugin.getConfig().getInt("homes.max", 3)) {
            player.sendMessage(plugin.text().msg("home-limit", plugin.text().vars("count", String.valueOf(plugin.getConfig().getInt("homes.max", 3)))));
            return false;
        }
        profile.homes().put(key, Point.of(player.getLocation()));
        player.sendMessage(plugin.text().msg("home-set", plugin.text().vars("home", key)));
        plugin.profiles().markDirty();
        return true;
    }

    public boolean createTeam(Player player, String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            player.sendMessage(plugin.text().msg("team-bad-name"));
            return false;
        }
        String color = parts.length > 1 ? parts[1] : "red";
        String error = plugin.teams().create(player.getUniqueId(), parts[0], color);
        if (error != null) {
            String key = switch (error) {
                case "in-team" -> "team-already";
                case "bad-name" -> "team-bad-name";
                case "taken" -> "team-taken";
                default -> error;
            };
            player.sendMessage(plugin.text().msg(key));
            return false;
        }
        player.sendMessage(plugin.text().msg("team-created", plugin.text().vars("team", parts[0])));
        plugin.refresh(player);
        return true;
    }

    public boolean askListing(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || plugin.isStar(hand)) {
            player.sendMessage(plugin.text().msg("ah-hand"));
            return false;
        }
        if (plugin.auctions().count(player.getUniqueId()) >= plugin.getConfig().getInt("auction.max-listings", 10)) {
            player.sendMessage(plugin.text().msg("ah-full"));
            return false;
        }
        ItemStack snapshot = hand.clone();
        plugin.prompts().ask(player, plugin.text().msg("prompt-price"), text -> finishListing(player, snapshot, text));
        return true;
    }

    public boolean finishListing(Player player, ItemStack snapshot, String text) {
        Long price = Format.amount(text);
        if (price == null) {
            player.sendMessage(plugin.text().msg("invalid-amount"));
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !hand.isSimilar(snapshot) || hand.getAmount() < snapshot.getAmount()) {
            player.sendMessage(plugin.text().msg("ah-changed"));
            return false;
        }
        if (plugin.auctions().count(player.getUniqueId()) >= plugin.getConfig().getInt("auction.max-listings", 10)) {
            player.sendMessage(plugin.text().msg("ah-full"));
            return false;
        }
        int left = hand.getAmount() - snapshot.getAmount();
        if (left <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            player.getInventory().setItemInMainHand(hand);
        }
        AuctionHouse.Listing listing = new AuctionHouse.Listing(UUID.randomUUID());
        listing.seller = player.getUniqueId();
        listing.sellerName = player.getName();
        listing.price = price;
        listing.expiresAt = System.currentTimeMillis() + plugin.getConfig().getInt("auction.expire-hours", 48) * 3_600_000L;
        listing.item = snapshot;
        plugin.auctions().add(listing);
        player.sendMessage(plugin.text().msg("ah-listed", plugin.text().vars("price", plugin.text().money(price))));
        return true;
    }

    private boolean buy(Player player, String categoryId, String itemId, int times) {
        Catalog.ShopItem item = plugin.catalog().item(categoryId, itemId);
        if (item == null || item.price() <= 0) {
            return false;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        int affordable = (int) Math.min(times, profile.balance() / item.price());
        if (affordable <= 0) {
            player.sendMessage(plugin.text().msg("broke"));
            return false;
        }
        long total;
        try {
            total = Math.multiplyExact(item.price(), affordable);
        } catch (ArithmeticException ex) {
            return false;
        }
        if (!profile.withdraw(total)) {
            player.sendMessage(plugin.text().msg("broke"));
            return false;
        }
        if ("key".equals(item.type())) {
            profile.addKeys(item.keyId(), item.amount() * affordable);
        } else {
            int count = item.amount() * affordable;
            while (count > 0) {
                int stack = Math.min(64, count);
                plugin.give(player, new ItemStack(item.material(), stack));
                count -= stack;
            }
        }
        player.sendMessage(plugin.text().msg("purchased", plugin.text().vars(
                "item", item.plainName(),
                "count", String.valueOf(item.amount() * affordable),
                "price", plugin.text().money(total)
        )));
        plugin.refresh(player);
        plugin.profiles().markDirty();
        return true;
    }

    private boolean toggle(Player player, String id) {
        if (Settings.find(id).isEmpty()) {
            return false;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        boolean enabled = !profile.setting(id);
        profile.setting(id, enabled);
        if (id.equals("star") && enabled) {
            plugin.spawn().giveStar(player);
        }
        plugin.refresh(player);
        plugin.profiles().markDirty();
        plugin.menus().openSettings(player);
        return true;
    }

    private boolean teleportSpawn(Player player) {
        int seconds = plugin.spawn().inSpawn(player.getLocation()) ? 0 : plugin.getConfig().getInt("spawn.delay-seconds", 5);
        plugin.teleports().later(player, plugin.spawn().location(), seconds, "The Edge");
        return true;
    }

    private boolean warp(Player player, String name) {
        Point point = plugin.warps().get(name);
        if (point == null || point.location() == null) {
            player.sendMessage(plugin.text().msg("warp-missing"));
            return false;
        }
        plugin.teleports().later(player, point.location(), plugin.getConfig().getInt("warps.delay-seconds", 3), name);
        return true;
    }

    private boolean home(Player player, String name) {
        Point point = plugin.profiles().get(player).homes().get(name.toLowerCase(java.util.Locale.ROOT));
        if (point == null || point.location() == null) {
            player.sendMessage(plugin.text().msg("home-missing"));
            return false;
        }
        plugin.teleports().later(player, point.location(), plugin.getConfig().getInt("homes.delay-seconds", 3), point == null ? name : name);
        return true;
    }

    private boolean giveKit(Player player, String id) {
        Catalog.Kit kit = plugin.catalog().kit(id);
        Catalog.KitResult result = plugin.catalog().giveKit(player, id);
        switch (result) {
            case OK -> {
                player.sendMessage(plugin.text().msg("kit-claimed", plugin.text().vars("kit", kit == null ? id : kit.display.replaceAll("<[^>]+>", ""))));
                plugin.play(player, Sound.ENTITY_ITEM_PICKUP, 1f);
                return true;
            }
            case COOLDOWN -> {
                long left = (plugin.profiles().get(player).kitReadyAt(id) - System.currentTimeMillis()) / 1000;
                player.sendMessage(plugin.text().msg("kit-cooldown", plugin.text().vars("time", Format.duration(left))));
            }
            case ONCE -> player.sendMessage(plugin.text().msg("kit-once"));
            case SPACE -> player.sendMessage(plugin.text().msg("kit-space"));
            case COMBAT -> player.sendMessage(plugin.text().msg("combat-blocked", plugin.text().vars("time", String.valueOf(plugin.combat().secondsLeft(player)))));
            case UNKNOWN -> player.sendMessage(plugin.text().msg("kit-unknown"));
        }
        return false;
    }

    private boolean openCrate(Player player, String id) {
        if (spinning.containsKey(player.getUniqueId())) {
            return false;
        }
        Catalog.Crate crate = plugin.catalog().crate(id);
        PlayerProfile profile = plugin.profiles().get(player);
        if (crate == null) {
            return false;
        }
        if (profile.keys(id) <= 0) {
            player.sendMessage(plugin.text().msg("crate-no-key"));
            return false;
        }
        if (plugin.combat().tagged(player)) {
            player.sendMessage(plugin.text().msg("combat-blocked", plugin.text().vars("time", String.valueOf(plugin.combat().secondsLeft(player)))));
            return false;
        }
        Catalog.Reward reward = plugin.catalog().roll(id);
        if (reward == null) {
            player.sendMessage(plugin.text().msg("crate-empty"));
            return false;
        }
        profile.addKeys(id, -1);
        spinning.put(player.getUniqueId(), reward);
        plugin.profiles().markDirty();
        GuiHolder holder = new GuiHolder("crate-spin");
        org.bukkit.inventory.Inventory inventory = Bukkit.createInventory(holder, 27, plugin.text().mini(crate.name()));
        holder.attach(inventory);
        Items.frame(inventory);
        plugin.play(player, Sound.BLOCK_CHEST_OPEN, 1f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(inventory);
            }
        });
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    Catalog.Reward pending = spinning.remove(player.getUniqueId());
                    if (pending != null) {
                        grant(player.getUniqueId(), pending, false);
                    }
                    cancel();
                    return;
                }
                step++;
                if (step < 10) {
                    Catalog.Reward shown = crate.rewards().get(java.util.concurrent.ThreadLocalRandom.current().nextInt(crate.rewards().size()));
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder open && "crate-spin".equals(open.id())) {
                        player.getOpenInventory().getTopInventory().setItem(13, Items.button(shown.material(), plugin.text().mini("<gray>" + shown.plainName()), List.of(), false));
                    }
                    return;
                }
                if (step == 10) {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder open && "crate-spin".equals(open.id())) {
                        player.getOpenInventory().getTopInventory().setItem(13, Items.button(reward.material(), plugin.text().mini("<white>" + reward.plainName()), List.of(plugin.text().mini("<green>This one is yours")), true));
                    }
                    plugin.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                    return;
                }
                if (step >= 16) {
                    spinning.remove(player.getUniqueId());
                    grant(player.getUniqueId(), reward, true);
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder open && "crate-spin".equals(open.id())) {
                        player.closeInventory();
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
        return true;
    }

    private void grant(UUID uuid, Catalog.Reward reward, boolean tell) {
        PlayerProfile profile = plugin.profiles().get(uuid);
        if (profile == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if ("money".equals(reward.type())) {
            long payout = reward.money() > 0 ? reward.money() : reward.amount();
            profile.deposit(payout);
        } else if ("key".equals(reward.type())) {
            profile.addKeys(reward.keyId(), reward.amount());
        } else {
            int count = reward.amount();
            while (count > 0) {
                int stack = Math.min(64, count);
                ItemStack item = new ItemStack(reward.material(), stack);
                if (player != null) {
                    plugin.give(player, item);
                } else if (!profile.offerMailbox(item)) {
                    break;
                }
                count -= stack;
            }
        }
        if (tell && player != null) {
            player.sendMessage(plugin.text().msg("crate-reward", plugin.text().vars("reward", reward.plainName())));
            plugin.refresh(player);
        }
        plugin.profiles().markDirty();
    }

    private boolean buyListing(Player player, UUID id) {
        AuctionHouse.Listing listing = plugin.auctions().find(id);
        if (listing == null) {
            player.sendMessage(plugin.text().msg("ah-missing"));
            return false;
        }
        if (listing.seller.equals(player.getUniqueId())) {
            return cancelListing(player, id);
        }
        PlayerProfile buyer = plugin.profiles().get(player);
        if (!buyer.withdraw(listing.price)) {
            player.sendMessage(plugin.text().msg("broke"));
            return false;
        }
        if (!plugin.auctions().remove(id)) {
            buyer.deposit(listing.price);
            player.sendMessage(plugin.text().msg("ah-missing"));
            return false;
        }
        plugin.give(player, listing.item);
        PlayerProfile seller = plugin.profiles().get(listing.seller);
        long payout = listing.price - Format.tax(listing.price, plugin.getConfig().getInt("auction.tax-percent", 5));
        if (seller != null) {
            seller.deposit(payout);
        }
        player.sendMessage(plugin.text().msg("ah-bought", plugin.text().vars("price", plugin.text().money(listing.price), "player", listing.sellerName)));
        Player sellerPlayer = Bukkit.getPlayer(listing.seller);
        if (sellerPlayer != null) {
            sellerPlayer.sendMessage(plugin.text().msg("ah-sold", plugin.text().vars("price", plugin.text().money(payout), "player", player.getName())));
            plugin.refresh(sellerPlayer);
        }
        plugin.refresh(player);
        plugin.profiles().markDirty();
        plugin.menus().openAuction(player, 0);
        return true;
    }

    private boolean cancelListing(Player player, UUID id) {
        AuctionHouse.Listing listing = plugin.auctions().find(id);
        if (listing == null || !listing.seller.equals(player.getUniqueId())) {
            player.sendMessage(plugin.text().msg("ah-missing"));
            return false;
        }
        plugin.auctions().remove(id);
        plugin.give(player, listing.item);
        player.sendMessage(plugin.text().msg("ah-cancelled"));
        plugin.menus().openAuction(player, 0);
        return true;
    }

    private boolean sellSlot(Player player, int slot) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        if (slot < 0 || slot >= storage.length) {
            return false;
        }
        long price = plugin.catalog().stackWorth(storage[slot]);
        if (price <= 0) {
            player.sendMessage(plugin.text().msg("sell-special"));
            return false;
        }
        int count = storage[slot].getAmount();
        storage[slot] = null;
        player.getInventory().setStorageContents(storage);
        plugin.profiles().get(player).deposit(price);
        player.sendMessage(plugin.text().msg("sell-done", plugin.text().vars("amount", plugin.text().money(price), "count", String.valueOf(count))));
        plugin.refresh(player);
        plugin.profiles().markDirty();
        plugin.menus().openSell(player);
        return true;
    }

    private boolean claimMail(Player player, int index) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile.mailbox().isEmpty()) {
            player.sendMessage(plugin.text().msg("mailbox-empty"));
            return false;
        }
        if (index < 0) {
            List<ItemStack> stacks = new ArrayList<>(profile.mailbox());
            profile.mailbox().clear();
            for (ItemStack stack : stacks) {
                plugin.give(player, stack);
            }
        } else if (index < profile.mailbox().size()) {
            plugin.give(player, profile.mailbox().remove(index));
        } else {
            return false;
        }
        player.sendMessage(plugin.text().msg("mailbox-claim"));
        plugin.profiles().markDirty();
        plugin.menus().openMailbox(player);
        return true;
    }

    private boolean leaveTeam(Player player) {
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        if (team == null) {
            player.sendMessage(plugin.text().msg("team-none"));
            return false;
        }
        String name = team.name;
        List<UUID> members = List.copyOf(team.members);
        String error = plugin.teams().leave(player.getUniqueId());
        if (error != null) {
            player.sendMessage(plugin.text().msg(error));
            return false;
        }
        Component message = plugin.text().msg("team-left", plugin.text().vars("player", player.getName(), "team", name));
        for (UUID member : members) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(message);
                plugin.refresh(online);
            }
        }
        return true;
    }

    private boolean disbandTeam(Player player) {
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        if (team == null) {
            player.sendMessage(plugin.text().msg("team-none"));
            return false;
        }
        String name = team.name;
        List<UUID> members = List.copyOf(team.members);
        String error = plugin.teams().disband(player.getUniqueId());
        if (error != null) {
            player.sendMessage(plugin.text().msg(error));
            return false;
        }
        Component message = plugin.text().msg("team-disbanded", plugin.text().vars("team", name));
        for (UUID member : members) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(message);
                plugin.refresh(online);
            }
        }
        return true;
    }

    private int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
