package dev.sharpsmp.game;

import dev.sharpsmp.SharpPlugin;
import dev.sharpsmp.data.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Catalog {
    public enum KitResult {
        OK, UNKNOWN, ONCE, COOLDOWN, SPACE, COMBAT
    }

    public record ShopItem(String id, String type, Material material, int amount, long price, String display, String keyId) {
        public String plainName() {
            if (display != null && !display.isBlank()) {
                return display.replaceAll("<[^>]+>", "");
            }
            return pretty(material);
        }
    }

    public record ShopCategory(String id, String name, Material icon, List<ShopItem> items) {
    }

    public record Reward(String type, Material material, int amount, long money, String keyId, int weight, String display) {
        public String plainName() {
            if (display != null && !display.isBlank()) {
                return display.replaceAll("<[^>]+>", "");
            }
            if ("money".equals(type)) {
                return "Sharps";
            }
            if ("key".equals(type)) {
                return keyId + " key";
            }
            return pretty(material);
        }
    }

    public record Crate(String id, String name, Material icon, List<Reward> rewards) {
    }

    public static final class Kit {
        public final String id;
        public final String display;
        public final Material icon;
        public final boolean once;
        public final int cooldownSeconds;
        public final List<ItemStack> items;

        public Kit(String id, String display, Material icon, boolean once, int cooldownSeconds, List<ItemStack> items) {
            this.id = id;
            this.display = display;
            this.icon = icon;
            this.once = once;
            this.cooldownSeconds = cooldownSeconds;
            this.items = items;
        }
    }

    private final SharpPlugin plugin;
    private final Map<String, ShopCategory> shop = new LinkedHashMap<>();
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<Material, Long> worth = new LinkedHashMap<>();

    public Catalog(SharpPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        shop.clear();
        crates.clear();
        kits.clear();
        worth.clear();
        loadShop();
        loadCrates();
        loadKits();
        loadWorth();
        plugin.getLogger().info("Loaded " + shop.size() + " shop categories, " + crates.size() + " crates, " + kits.size() + " kits.");
    }

    public List<ShopCategory> shop() {
        return List.copyOf(shop.values());
    }

    public ShopCategory category(String id) {
        return shop.get(id);
    }

    public ShopItem item(String categoryId, String itemId) {
        ShopCategory category = shop.get(categoryId);
        if (category == null) {
            return null;
        }
        for (ShopItem item : category.items()) {
            if (item.id().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    public List<Crate> crates() {
        return List.copyOf(crates.values());
    }

    public Crate crate(String id) {
        return crates.get(id);
    }

    public Reward roll(String crateId) {
        Crate crate = crates.get(crateId);
        if (crate == null || crate.rewards().isEmpty()) {
            return null;
        }
        int total = 0;
        for (Reward reward : crate.rewards()) {
            total += Math.max(0, reward.weight());
        }
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (Reward reward : crate.rewards()) {
            cursor += Math.max(0, reward.weight());
            if (roll < cursor) {
                return reward;
            }
        }
        return crate.rewards().get(crate.rewards().size() - 1);
    }

    public List<Kit> kits() {
        return List.copyOf(kits.values());
    }

    public Kit kit(String id) {
        return kits.get(id);
    }

    public KitResult giveKit(Player player, String id) {
        Kit kit = kits.get(id);
        if (kit == null) {
            return KitResult.UNKNOWN;
        }
        if (plugin.combat().tagged(player)) {
            return KitResult.COMBAT;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (kit.once && profile.kitClaimed(id)) {
            return KitResult.ONCE;
        }
        long now = System.currentTimeMillis();
        if (!kit.once && kit.cooldownSeconds > 0 && now < profile.kitReadyAt(id)) {
            return KitResult.COOLDOWN;
        }
        int empty = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                empty++;
            }
        }
        if (empty < kit.items.size()) {
            return KitResult.SPACE;
        }
        for (ItemStack stack : kit.items) {
            plugin.give(player, stack.clone());
        }
        if (kit.once) {
            profile.claimKit(id);
        } else if (kit.cooldownSeconds > 0) {
            profile.kitReadyAt(id, now + kit.cooldownSeconds * 1000L);
        }
        plugin.profiles().markDirty();
        return KitResult.OK;
    }

    public long worth(Material material) {
        return worth.getOrDefault(material, 0L);
    }

    public boolean sellable(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || plugin.isStar(stack)) {
            return false;
        }
        if (!stack.getEnchantments().isEmpty()) {
            return false;
        }
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return false;
        }
        if (stack.getType().name().contains("SHULKER") && stack.getItemMeta() instanceof BlockStateMeta meta
                && meta.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (inner != null && !inner.getType().isAir()) {
                    return false;
                }
            }
        }
        return worth(stack.getType()) > 0;
    }

    public long stackWorth(ItemStack stack) {
        if (!sellable(stack)) {
            return 0L;
        }
        try {
            return Math.multiplyExact(worth(stack.getType()), stack.getAmount());
        } catch (ArithmeticException ex) {
            return 0L;
        }
    }

    public static String pretty(Material material) {
        if (material == null) {
            return "Item";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private void loadShop() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("shop");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemSection = section.getConfigurationSection("items");
            if (itemSection != null) {
                for (String itemId : itemSection.getKeys(false)) {
                    ConfigurationSection item = itemSection.getConfigurationSection(itemId);
                    if (item == null) {
                        continue;
                    }
                    String type = item.getString("type", "item");
                    Material material = material(item.getString("material", item.getString("icon", "STONE")), Material.STONE);
                    items.add(new ShopItem(
                            itemId,
                            type,
                            material,
                            Math.max(1, item.getInt("amount", 1)),
                            item.getLong("price"),
                            item.getString("display"),
                            item.getString("key", itemId)
                    ));
                }
            }
            shop.put(id, new ShopCategory(id, section.getString("name", id), material(section.getString("icon"), Material.CHEST), items));
        }
    }

    private void loadCrates() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("crates.types");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Reward> rewards = new ArrayList<>();
            ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
            if (rewardSection != null) {
                for (String rewardId : rewardSection.getKeys(false)) {
                    ConfigurationSection reward = rewardSection.getConfigurationSection(rewardId);
                    if (reward == null) {
                        continue;
                    }
                    rewards.add(new Reward(
                            reward.getString("type", "item"),
                            material(reward.getString("material", "CHEST"), Material.CHEST),
                            Math.max(1, reward.getInt("amount", 1)),
                            reward.getLong("amount"),
                            reward.getString("key", "common"),
                            reward.getInt("weight", 1),
                            reward.getString("display")
                    ));
                }
            }
            crates.put(id, new Crate(id, section.getString("name", id), material(section.getString("icon"), Material.CHEST), rewards));
        }
    }

    private void loadKits() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            ConfigurationSection itemSection = section.getConfigurationSection("items");
            if (itemSection != null) {
                for (String itemId : itemSection.getKeys(false)) {
                    ConfigurationSection item = itemSection.getConfigurationSection(itemId);
                    if (item == null) {
                        continue;
                    }
                    Material material = material(item.getString("material"), null);
                    if (material == null) {
                        continue;
                    }
                    ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, item.getInt("amount", 1))));
                    ConfigurationSection enchants = item.getConfigurationSection("enchants");
                    if (enchants != null) {
                        ItemMeta meta = stack.getItemMeta();
                        for (String enchantName : enchants.getKeys(false)) {
                            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantName.toLowerCase(Locale.ROOT)));
                            if (enchantment != null) {
                                meta.addEnchant(enchantment, enchants.getInt(enchantName), true);
                            } else {
                                plugin.getLogger().warning("Unknown enchantment " + enchantName + " on kit " + id);
                            }
                        }
                        stack.setItemMeta(meta);
                    }
                    items.add(stack);
                }
            }
            kits.put(id, new Kit(
                    id,
                    section.getString("display", id),
                    material(section.getString("icon"), Material.CHEST),
                    section.getBoolean("once"),
                    section.getInt("cooldown-seconds"),
                    items
            ));
        }
    }

    private void loadWorth() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("worth");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            Material material = material(key, null);
            if (material != null) {
                worth.put(material, root.getLong(key));
            }
        }
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("Unknown material " + name);
            return fallback;
        }
        return material;
    }
}
