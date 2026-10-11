package dev.sharpsmp.gui;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.AuctionHouse;
import dev.sharpsmp.data.PlayerProfile;
import dev.sharpsmp.data.Point;
import dev.sharpsmp.data.Teams;
import dev.sharpsmp.game.Catalog;
import dev.sharpsmp.game.Settings;
import dev.sharpsmp.text.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Menus {
    private static final int[] INNER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final SharpPlugin plugin;

    public Menus(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        GuiHolder holder = new GuiHolder("main");
        Inventory inventory = chest(holder, "<gradient:#ff3355:#ffffff><bold>SharpSMP</bold></gradient>");
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        inventory.setItem(10, Items.head(player,
                name("<white>" + player.getName()),
                lines("<gray>Kills <white>" + profile.kills(), "<gray>Deaths <white>" + profile.deaths(), "<gray>Team <white>" + (team == null ? "None" : team.name), "", "<yellow>Click for stats")));
        holder.bind(10, "nav:stats");
        place(holder, inventory, 12, Material.GOLD_INGOT, "<white>" + plugin.text().money(profile.balance()), List.of("<gray>Your Sharps", "<dark_gray>/pay <player> <amount>"), null, true);
        long dailyLeft = dailyLeft(profile);
        place(holder, inventory, 14, Material.CLOCK, "<gold>Daily", List.of(dailyLeft <= 0 ? "<green>Ready to claim" : "<red>Ready in " + Format.duration(dailyLeft)), "daily", dailyLeft <= 0);
        place(holder, inventory, 16, Material.GOLD_BLOCK, "<gold>Leaderboards", List.of("<gray>Richest hunters on The Edge"), "nav:lb", false);
        place(holder, inventory, 19, Material.NETHERITE_SWORD, "<white>Shop", List.of("<gray>Gear, food, keys"), "nav:shop", false);
        place(holder, inventory, 21, Material.GOLD_BLOCK, "<gold>Auction", List.of("<gray>" + plugin.auctions().listings().size() + " listings"), "nav:auction", false);
        place(holder, inventory, 23, Material.HOPPER, "<white>Sell", List.of("<gray>Turn loot into Sharps"), "nav:sell", false);
        place(holder, inventory, 25, Material.NAME_TAG, "<red>Bounties", List.of("<gray>Your head is worth <white>" + plugin.text().money(profile.bounty())), "nav:bounties", profile.bounty() > 0);
        int keys = profile.keys("common") + profile.keys("rare") + profile.keys("legendary");
        place(holder, inventory, 28, Material.CHEST, "<light_purple>Crates", List.of("<gray>Keys <white>" + keys), "nav:crates", keys > 0);
        place(holder, inventory, 30, Material.ENDER_PEARL, "<aqua>RTP", List.of("<gray>Drop into the wild"), "rtp", false);
        place(holder, inventory, 32, Material.IRON_CHESTPLATE, "<white>Kits", List.of("<gray>Starter, member, and Edge"), "nav:kits", false);
        place(holder, inventory, 34, Material.WHITE_BANNER, "<green>Team", List.of("<gray>" + (team == null ? "Create a crew" : team.name)), "nav:teams", false);
        place(holder, inventory, 37, Material.COMPARATOR, "<white>Settings", List.of("<gray>Scoreboard, chat, TPA"), "nav:settings", false);
        place(holder, inventory, 39, Material.RED_BED, "<white>Homes", List.of("<gray>" + profile.homes().size() + " / " + plugin.getConfig().getInt("homes.max", 3)), "nav:homes", false);
        place(holder, inventory, 41, Material.COMPASS, "<white>Warps", List.of("<gray>The Edge and staff warps"), "nav:warps", false);
        place(holder, inventory, 43, Material.BARREL, "<gold>Mailbox", List.of(profile.mailbox().isEmpty() ? "<gray>Empty" : "<green>" + profile.mailbox().size() + " waiting"), "nav:mailbox", !profile.mailbox().isEmpty());
        close(holder, inventory);
        show(player, inventory);
    }

    public void openSettings(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        GuiHolder holder = new GuiHolder("settings");
        Inventory inventory = chest(holder, "<white>Settings");
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
        for (int i = 0; i < Settings.ALL.size() && i < slots.length; i++) {
            Settings.Option option = Settings.ALL.get(i);
            boolean on = profile.setting(option.id());
            place(holder, inventory, slots[i], option.icon(), (on ? "<green>" : "<red>") + option.name(),
                    List.of("<gray>" + option.detail(), "", on ? "<green>Enabled" : "<red>Disabled", "<yellow>Click to toggle"),
                    "toggle:" + option.id(), on);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openShop(Player player) {
        GuiHolder holder = new GuiHolder("shop");
        Inventory inventory = chest(holder, "<white>Shop");
        int index = 0;
        for (Catalog.ShopCategory category : plugin.catalog().shop()) {
            if (index >= INNER.length) {
                break;
            }
            place(holder, inventory, INNER[index++], category.icon(), category.name(), List.of("<gray>" + category.items().size() + " offers", "<yellow>Click to browse"), "shop-cat:" + category.id(), false);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openCategory(Player player, String categoryId, int page) {
        Catalog.ShopCategory category = plugin.catalog().category(categoryId);
        if (category == null) {
            openShop(player);
            return;
        }
        GuiHolder holder = new GuiHolder("shop-items");
        Inventory inventory = chest(holder, category.name());
        int start = Math.max(0, page) * INNER.length;
        List<Catalog.ShopItem> items = category.items();
        for (int i = 0; i < INNER.length && start + i < items.size(); i++) {
            Catalog.ShopItem item = items.get(start + i);
            place(holder, inventory, INNER[i], item.material(), "<white>" + item.plainName(),
                    List.of("<gray>Buy <white>" + item.amount() + " <gray>for <white>" + plugin.text().money(item.price()), "<dark_gray>Shift-click buys 5"),
                    "shop-buy:" + categoryId + ":" + item.id(), false);
        }
        if (page > 0) {
            place(holder, inventory, 48, Material.ARROW, "<gray>Previous", List.of(), "shop-page:" + categoryId + ":" + (page - 1), false);
        }
        if (start + INNER.length < items.size()) {
            place(holder, inventory, 50, Material.ARROW, "<gray>Next", List.of(), "shop-page:" + categoryId + ":" + (page + 1), false);
        }
        back(holder, inventory, "nav:shop");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openBounties(Player player) {
        GuiHolder holder = new GuiHolder("bounties");
        Inventory inventory = chest(holder, "<red>Bounties");
        place(holder, inventory, 4, Material.BOOK, "<red>Set a bounty", List.of("<gray>Click a player, then choose an amount.", "<dark_gray>Tax " + plugin.getConfig().getInt("bounty.tax-percent", 5) + "%"), null, false);
        Map<UUID, PlayerProfile> targets = new LinkedHashMap<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                targets.put(online.getUniqueId(), plugin.profiles().get(online));
            }
        }
        for (PlayerProfile profile : plugin.profiles().all()) {
            if (profile.bounty() > 0 && !profile.uuid().equals(player.getUniqueId())) {
                targets.putIfAbsent(profile.uuid(), profile);
            }
        }
        List<PlayerProfile> ordered = new ArrayList<>(targets.values());
        ordered.sort(Comparator.comparingLong((PlayerProfile profile) -> profile.bounty()).reversed());
        int index = 0;
        for (PlayerProfile profile : ordered) {
            if (index >= INNER.length) {
                break;
            }
            Player online = Bukkit.getPlayer(profile.uuid());
            inventory.setItem(INNER[index], Items.head(
                    online != null ? online : Bukkit.getOfflinePlayer(profile.uuid()),
                    name("<white>" + profile.name()),
                    lines("<gray>Bounty <red>" + plugin.text().money(profile.bounty()), "<gray>Kills <white>" + profile.kills(), "", "<yellow>Click to add Sharps")
            ));
            holder.bind(INNER[index], "bounty-pick:" + profile.uuid());
            index++;
        }
        if (index == 0) {
            place(holder, inventory, 22, Material.BARRIER, "<gray>No targets", List.of("<dark_gray>Wait for someone else to join"), null, false);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openBountyAmounts(Player player, UUID targetId) {
        PlayerProfile target = plugin.profiles().get(targetId);
        if (target == null) {
            openBounties(player);
            return;
        }
        GuiHolder holder = new GuiHolder("bounty-amount");
        Inventory inventory = chest(holder, "<red>Bounty " + target.name());
        inventory.setItem(4, Items.head(Bukkit.getOfflinePlayer(targetId), name("<white>" + target.name()), lines("<gray>Current <red>" + plugin.text().money(target.bounty()))));
        long[] amounts = {100, 500, 1_000, 5_000, 10_000, 50_000, 100_000};
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < amounts.length; i++) {
            long amount = amounts[i];
            Long cost = Format.cost(amount, plugin.getConfig().getInt("bounty.tax-percent", 5));
            place(holder, inventory, slots[i], Material.GOLD_NUGGET, "<white>" + plugin.text().money(amount),
                    List.of("<gray>You pay <white>" + plugin.text().money(cost == null ? amount : cost)),
                    "bounty-add:" + targetId + ":" + amount, false);
        }
        place(holder, inventory, 31, Material.WRITABLE_BOOK, "<yellow>Custom", List.of("<gray>Type an amount in chat", "<dark_gray>Example: 2.5k"), "bounty-custom:" + targetId, false);
        back(holder, inventory, "nav:bounties");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openAuction(Player player, int page) {
        GuiHolder holder = new GuiHolder("auction");
        Inventory inventory = chest(holder, "<gold>Auction House");
        List<AuctionHouse.Listing> listings = plugin.auctions().listings();
        int start = Math.max(0, page) * INNER.length;
        for (int i = 0; i < INNER.length && start + i < listings.size(); i++) {
            AuctionHouse.Listing listing = listings.get(start + i);
            boolean own = listing.seller.equals(player.getUniqueId());
            inventory.setItem(INNER[i], auctionIcon(listing, own));
            holder.bind(INNER[i], (own ? "ah-cancel:" : "ah-buy:") + listing.id);
        }
        if (listings.isEmpty()) {
            place(holder, inventory, 22, Material.BARRIER, "<gray>No listings", List.of("<dark_gray>Hold an item and click sell"), null, false);
        }
        if (page > 0) {
            place(holder, inventory, 48, Material.ARROW, "<gray>Previous", List.of(), "ah-page:" + (page - 1), false);
        }
        if (start + INNER.length < listings.size()) {
            place(holder, inventory, 50, Material.ARROW, "<gray>Next", List.of(), "ah-page:" + (page + 1), false);
        }
        place(holder, inventory, 53, Material.EMERALD, "<green>Sell held item", List.of("<gray>Sets a price in chat"), "ah-sell", false);
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openCrates(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        GuiHolder holder = new GuiHolder("crates");
        Inventory inventory = chest(holder, "<light_purple>Crates");
        int index = 0;
        for (Catalog.Crate crate : plugin.catalog().crates()) {
            if (index >= INNER.length) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Keys <white>" + profile.keys(crate.id()));
            lore.add("");
            int shown = 0;
            for (Catalog.Reward reward : crate.rewards()) {
                if (shown++ >= 5) {
                    break;
                }
                lore.add("<dark_gray>• <gray>" + reward.plainName());
            }
            lore.add("");
            lore.add(profile.keys(crate.id()) > 0 ? "<green>Click to open" : "<red>No key");
            place(holder, inventory, INNER[index++], crate.icon(), crate.name(), lore, "crate:" + crate.id(), profile.keys(crate.id()) > 0);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openKits(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        GuiHolder holder = new GuiHolder("kits");
        Inventory inventory = chest(holder, "<white>Kits");
        int index = 0;
        long now = System.currentTimeMillis();
        for (Catalog.Kit kit : plugin.catalog().kits()) {
            if (index >= INNER.length) {
                break;
            }
            String status;
            if (kit.once && profile.kitClaimed(kit.id)) {
                status = "<red>Already claimed";
            } else if (!kit.once && kit.cooldownSeconds > 0 && now < profile.kitReadyAt(kit.id)) {
                status = "<red>Ready in " + Format.duration((profile.kitReadyAt(kit.id) - now) / 1000);
            } else {
                status = "<green>Ready";
            }
            place(holder, inventory, INNER[index++], kit.icon, kit.display, List.of("<gray>" + kit.items.size() + " items", status), "kit:" + kit.id, status.startsWith("<green>"));
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openWarps(Player player) {
        GuiHolder holder = new GuiHolder("warps");
        Inventory inventory = chest(holder, "<white>Warps");
        place(holder, inventory, 20, Material.LODESTONE, "<white>The Edge", List.of("<gray>Return to spawn"), "spawn", false);
        place(holder, inventory, 22, Material.ENDER_PEARL, "<aqua>Wild", List.of("<gray>Random teleport"), "rtp", false);
        int index = 0;
        int[] slots = {29, 30, 31, 32, 33};
        for (String name : plugin.warps().names()) {
            if (index >= slots.length) {
                break;
            }
            place(holder, inventory, slots[index++], Material.COMPASS, "<white>" + name, List.of("<yellow>Click to warp"), "warp:" + name, false);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openHomes(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        GuiHolder holder = new GuiHolder("homes");
        Inventory inventory = chest(holder, "<white>Homes");
        int index = 0;
        for (Map.Entry<String, Point> entry : profile.homes().entrySet()) {
            if (index >= INNER.length) {
                break;
            }
            Point point = entry.getValue();
            place(holder, inventory, INNER[index++], Material.RED_BED, "<white>" + entry.getKey(),
                    List.of("<gray>" + point.world() + " " + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(), "<yellow>Click to teleport", "<dark_gray>Drop with /delhome"),
                    "home:" + entry.getKey(), false);
        }
        place(holder, inventory, 53, Material.NAME_TAG, "<green>Set home", List.of("<gray>Name it in chat"), "home-set", false);
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openTeams(Player player) {
        GuiHolder holder = new GuiHolder("teams");
        Inventory inventory = chest(holder, "<green>Team");
        Teams.TeamData team = plugin.teams().of(player.getUniqueId());
        if (team == null) {
            place(holder, inventory, 22, Material.WRITABLE_BOOK, "<green>Create a team", List.of("<gray>3-16 letters, then invite with /team invite"), "team-create", true);
        } else {
            place(holder, inventory, 4, Material.WHITE_BANNER, "<white>" + team.name, List.of("<gray>Leader <white>" + nameOf(team.leader), "<gray>Members <white>" + team.members.size()), null, false);
            int index = 0;
            for (UUID member : team.members) {
                if (index >= INNER.length) {
                    break;
                }
                PlayerProfile profile = plugin.profiles().get(member);
                String name = profile == null ? member.toString().substring(0, 8) : profile.name();
                inventory.setItem(INNER[index], Items.head(Bukkit.getOfflinePlayer(member), name("<white>" + name), lines(member.equals(team.leader) ? "<gold>Leader" : "<gray>Member")));
                holder.bind(INNER[index], "lb-stats:" + member);
                index++;
            }
            place(holder, inventory, 47, Material.OAK_DOOR, "<red>Leave", List.of(), "team-leave", false);
            if (team.leader.equals(player.getUniqueId())) {
                place(holder, inventory, 51, Material.BARRIER, "<dark_red>Disband", List.of("<gray>Deletes the team"), "team-disband", false);
            }
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openLeaderboard(Player player, String type) {
        GuiHolder holder = new GuiHolder("lb");
        Inventory inventory = chest(holder, "<gold>Leaderboard");
        place(holder, inventory, 2, Material.GOLD_INGOT, "<white>Sharps", List.of(), "lb:balance", "balance".equals(type));
        place(holder, inventory, 3, Material.DIAMOND_SWORD, "<white>Kills", List.of(), "lb:kills", "kills".equals(type));
        place(holder, inventory, 4, Material.BLAZE_POWDER, "<white>Streak", List.of(), "lb:streak", "streak".equals(type));
        place(holder, inventory, 5, Material.CLOCK, "<white>Playtime", List.of(), "lb:playtime", "playtime".equals(type));
        place(holder, inventory, 6, Material.NAME_TAG, "<white>Bounty", List.of(), "lb:bounty", "bounty".equals(type));
        List<PlayerProfile> profiles = new ArrayList<>(plugin.profiles().all());
        profiles.sort(switch (type) {
            case "kills" -> Comparator.comparingInt((PlayerProfile profile) -> profile.kills()).reversed();
            case "streak" -> Comparator.comparingInt((PlayerProfile profile) -> profile.bestStreak()).reversed();
            case "playtime" -> Comparator.comparingLong((PlayerProfile profile) -> profile.playtimeMinutes()).reversed();
            case "bounty" -> Comparator.comparingLong((PlayerProfile profile) -> profile.bounty()).reversed();
            default -> Comparator.comparingLong((PlayerProfile profile) -> profile.balance()).reversed();
        });
        int index = 0;
        for (PlayerProfile profile : profiles) {
            if (index >= 21) {
                break;
            }
            String value = switch (type) {
                case "kills" -> profile.kills() + " kills";
                case "streak" -> profile.bestStreak() + " best";
                case "playtime" -> Format.playtime(profile.playtimeMinutes());
                case "bounty" -> plugin.text().money(profile.bounty());
                default -> plugin.text().money(profile.balance());
            };
            int slot = INNER[index];
            inventory.setItem(slot, Items.head(Bukkit.getOfflinePlayer(profile.uuid()), name("<white>" + (index + 1) + ". " + profile.name()), lines("<gray>" + value)));
            holder.bind(slot, "lb-stats:" + profile.uuid());
            index++;
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openStats(Player viewer, PlayerProfile profile) {
        GuiHolder holder = new GuiHolder("stats");
        Inventory inventory = chest(holder, "<white>" + profile.name());
        Teams.TeamData team = plugin.teams().of(profile.uuid());
        inventory.setItem(13, Items.head(Bukkit.getOfflinePlayer(profile.uuid()), name("<white>" + profile.name()), lines(
                "<gray>Sharps <white>" + plugin.text().money(profile.balance()),
                "<gray>Kills <white>" + profile.kills(),
                "<gray>Deaths <white>" + profile.deaths(),
                "<gray>K/D <white>" + Format.kdr(profile.kills(), profile.deaths()),
                "<gray>Streak <white>" + profile.streak() + " <dark_gray>(best " + profile.bestStreak() + ")",
                "<gray>Bounty <red>" + plugin.text().money(profile.bounty()),
                "<gray>Playtime <white>" + Format.playtime(profile.playtimeMinutes()),
                "<gray>Team <white>" + (team == null ? "None" : team.name)
        )));
        if (!viewer.getUniqueId().equals(profile.uuid())) {
            place(holder, inventory, 31, Material.NAME_TAG, "<red>Set bounty", List.of(), "bounty-pick:" + profile.uuid(), false);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(viewer, inventory);
    }

    public void openSell(Player player) {
        GuiHolder holder = new GuiHolder("sell");
        Inventory inventory = chest(holder, "<white>Sell");
        ItemStack[] storage = player.getInventory().getStorageContents();
        int index = 0;
        long total = 0;
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            long price = plugin.catalog().stackWorth(stack);
            if (price <= 0) {
                continue;
            }
            total += price;
            if (index >= INNER.length) {
                continue;
            }
            ItemStack icon = stack.clone();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Items.plain(Component.text("Sell for " + plugin.text().money(price), NamedTextColor.WHITE)));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(INNER[index], icon);
            holder.bind(INNER[index], "sell-slot:" + slot);
            index++;
        }
        if (index == 0) {
            place(holder, inventory, 22, Material.BARRIER, "<gray>Nothing to sell", List.of("<dark_gray>Enchanted and named items stay with you"), null, false);
        }
        place(holder, inventory, 51, Material.HOPPER, "<green>Sell all", List.of("<gray>Total <white>" + plugin.text().money(total)), "sell-all", total > 0);
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openMailbox(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        GuiHolder holder = new GuiHolder("mailbox");
        Inventory inventory = chest(holder, "<gold>Mailbox");
        int index = 0;
        for (ItemStack stack : profile.mailbox()) {
            if (index >= INNER.length) {
                break;
            }
            inventory.setItem(INNER[index], stack.clone());
            holder.bind(INNER[index], "mail:" + index);
            index++;
        }
        if (index == 0) {
            place(holder, inventory, 22, Material.BARRIER, "<gray>Mailbox empty", List.of(), null, false);
        } else {
            place(holder, inventory, 51, Material.CHEST, "<green>Claim all", List.of(), "mail-all", false);
        }
        back(holder, inventory, "nav:menu");
        close(holder, inventory);
        show(player, inventory);
    }

    public void openConfirm(Player player, String title, String question, String yesAction, String backAction) {
        GuiHolder holder = new GuiHolder("confirm");
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.text().mini(title));
        holder.attach(inventory);
        Items.frame(inventory);
        place(holder, inventory, 13, Material.PAPER, "<white>" + question, List.of(), null, false);
        place(holder, inventory, 11, Material.LIME_CONCRETE, "<green>Confirm", List.of(), yesAction, false);
        place(holder, inventory, 15, Material.RED_CONCRETE, "<red>Back", List.of(), backAction, false);
        show(player, inventory);
    }

    private ItemStack auctionIcon(AuctionHouse.Listing listing, boolean own) {
        ItemStack icon = listing.item.clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Seller ", NamedTextColor.GRAY).append(Component.text(listing.sellerName, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Price ", NamedTextColor.GRAY).append(Component.text(plugin.text().money(listing.price), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(own ? "Click to collect" : "Click to buy", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private Inventory chest(GuiHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.text().mini(title));
        holder.attach(inventory);
        Items.frame(inventory);
        return inventory;
    }

    private void place(GuiHolder holder, Inventory inventory, int slot, Material material, String itemName, List<String> lore, String action, boolean glint) {
        inventory.setItem(slot, Items.button(material, name(itemName), lines(lore.toArray(String[]::new)), glint));
        if (action != null) {
            holder.bind(slot, action);
        }
    }

    private void back(GuiHolder holder, Inventory inventory, String action) {
        place(holder, inventory, 45, Material.ARROW, "<gray>Back", List.of(), action, false);
    }

    private void close(GuiHolder holder, Inventory inventory) {
        place(holder, inventory, 49, Material.BARRIER, "<red>Close", List.of(), "nav:close", false);
    }

    private Component name(String mini) {
        return plugin.text().mini(mini);
    }

    private List<Component> lines(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(line.isEmpty() ? Component.empty() : plugin.text().mini(line));
        }
        return lore;
    }

    private void show(Player player, Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(inventory);
            }
        });
    }

    private long dailyLeft(PlayerProfile profile) {
        long cooldown = plugin.getConfig().getInt("daily.cooldown-hours", 24) * 3_600_000L;
        return Math.max(0L, (profile.dailyAt() + cooldown - System.currentTimeMillis()) / 1000);
    }

    private String nameOf(UUID uuid) {
        PlayerProfile profile = plugin.profiles().get(uuid);
        return profile == null ? "Unknown" : profile.name();
    }
}
