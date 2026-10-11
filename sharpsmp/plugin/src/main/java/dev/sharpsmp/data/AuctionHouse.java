package dev.sharpsmp.data;

import dev.sharpsmp.SharpPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class AuctionHouse {
    public static final class Listing {
        public final UUID id;
        public UUID seller;
        public String sellerName;
        public long price;
        public long expiresAt;
        public ItemStack item;

        public Listing(UUID id) {
            this.id = id;
        }
    }

    private final SharpPlugin plugin;
    private final File file;
    private final List<Listing> listings = new ArrayList<>();

    public AuctionHouse(SharpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
    }

    public void load() {
        listings.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("listings");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            ItemStack item = section.getItemStack("item");
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Listing listing = new Listing(UUID.fromString(id));
            listing.seller = UUID.fromString(section.getString("seller"));
            listing.sellerName = section.getString("seller-name", "Unknown");
            listing.price = section.getLong("price");
            listing.expiresAt = section.getLong("expires");
            listing.item = item;
            listings.add(listing);
        }
    }

    public void save() {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Listing listing : listings) {
            String path = "listings." + listing.id;
            yaml.set(path + ".seller", listing.seller.toString());
            yaml.set(path + ".seller-name", listing.sellerName);
            yaml.set(path + ".price", listing.price);
            yaml.set(path + ".expires", listing.expiresAt);
            yaml.set(path + ".item", listing.item);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save auctions", ex);
        }
    }

    public List<Listing> listings() {
        listings.sort(Comparator.comparingLong((Listing listing) -> listing.expiresAt).reversed());
        return List.copyOf(listings);
    }

    public Listing find(UUID id) {
        for (Listing listing : listings) {
            if (listing.id.equals(id)) {
                return listing;
            }
        }
        return null;
    }

    public int count(UUID seller) {
        int count = 0;
        for (Listing listing : listings) {
            if (listing.seller.equals(seller)) {
                count++;
            }
        }
        return count;
    }

    public void add(Listing listing) {
        listings.add(listing);
        save();
    }

    public boolean remove(UUID id) {
        boolean removed = listings.removeIf(listing -> listing.id.equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public List<Listing> expire(long now) {
        List<Listing> expired = new ArrayList<>();
        listings.removeIf(listing -> {
            if (listing.expiresAt > now) {
                return false;
            }
            expired.add(listing);
            return true;
        });
        if (!expired.isEmpty()) {
            save();
        }
        return expired;
    }
}
