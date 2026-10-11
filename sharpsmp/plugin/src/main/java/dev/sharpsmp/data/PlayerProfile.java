package dev.sharpsmp.data;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProfile {
    public static final long MAX_BALANCE = 9_000_000_000_000L;

    private final UUID uuid;
    private String name;
    private long balance;
    private int kills;
    private int deaths;
    private int streak;
    private int bestStreak;
    private long playtimeMinutes;
    private int keyProgress;
    private long bounty;
    private boolean welcomed;
    private long dailyAt;
    private final Map<String, Boolean> settings = new ConcurrentHashMap<>();
    private final Map<String, Point> homes = new LinkedHashMap<>();
    private final Map<String, Integer> keys = new LinkedHashMap<>();
    private final Map<String, Long> kitReadyAt = new LinkedHashMap<>();
    private final Set<String> kitsClaimed = new HashSet<>();
    private final List<ItemStack> mailbox = new ArrayList<>();

    public PlayerProfile(UUID uuid, String name, long balance) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public long balance() {
        return balance;
    }

    public void balance(long balance) {
        this.balance = Math.max(0, Math.min(MAX_BALANCE, balance));
    }

    public boolean withdraw(long amount) {
        if (amount <= 0 || balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public void deposit(long amount) {
        if (amount <= 0) {
            return;
        }
        if (amount > MAX_BALANCE - balance) {
            balance = MAX_BALANCE;
        } else {
            balance += amount;
        }
    }

    public int kills() {
        return kills;
    }

    public void kills(int kills) {
        this.kills = kills;
    }

    public int deaths() {
        return deaths;
    }

    public void deaths(int deaths) {
        this.deaths = deaths;
    }

    public int streak() {
        return streak;
    }

    public void streak(int streak) {
        this.streak = streak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public void bestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public void addKill() {
        kills++;
        streak++;
        if (streak > bestStreak) {
            bestStreak = streak;
        }
    }

    public void addDeath() {
        deaths++;
        streak = 0;
    }

    public long playtimeMinutes() {
        return playtimeMinutes;
    }

    public void playtimeMinutes(long playtimeMinutes) {
        this.playtimeMinutes = playtimeMinutes;
    }

    public int keyProgress() {
        return keyProgress;
    }

    public void keyProgress(int keyProgress) {
        this.keyProgress = keyProgress;
    }

    public boolean tickMinute(int minutesPerKey) {
        playtimeMinutes++;
        if (minutesPerKey <= 0) {
            return false;
        }
        keyProgress++;
        if (keyProgress < minutesPerKey) {
            return false;
        }
        keyProgress = 0;
        addKeys("common", 1);
        return true;
    }

    public long bounty() {
        return bounty;
    }

    public void bounty(long bounty) {
        this.bounty = Math.max(0, bounty);
    }

    public void addBounty(long amount) {
        if (amount > 0) {
            bounty += amount;
        }
    }

    public long claimBounty() {
        long claimed = bounty;
        bounty = 0;
        return claimed;
    }

    public boolean welcomed() {
        return welcomed;
    }

    public void welcomed(boolean welcomed) {
        this.welcomed = welcomed;
    }

    public long dailyAt() {
        return dailyAt;
    }

    public void dailyAt(long dailyAt) {
        this.dailyAt = dailyAt;
    }

    public boolean setting(String id) {
        return settings.getOrDefault(id, true);
    }

    public void setting(String id, boolean value) {
        settings.put(id, value);
    }

    public Map<String, Boolean> settings() {
        return settings;
    }

    public Map<String, Point> homes() {
        return homes;
    }

    public int keys(String id) {
        return keys.getOrDefault(id, 0);
    }

    public void keys(String id, int amount) {
        if (amount <= 0) {
            keys.remove(id);
        } else {
            keys.put(id, amount);
        }
    }

    public void addKeys(String id, int amount) {
        keys(id, keys(id) + amount);
    }

    public Map<String, Integer> keys() {
        return keys;
    }

    public long kitReadyAt(String id) {
        return kitReadyAt.getOrDefault(id, 0L);
    }

    public void kitReadyAt(String id, long when) {
        kitReadyAt.put(id, when);
    }

    public Map<String, Long> kitReadyAt() {
        return kitReadyAt;
    }

    public boolean kitClaimed(String id) {
        return kitsClaimed.contains(id);
    }

    public void claimKit(String id) {
        kitsClaimed.add(id);
    }

    public Set<String> kitsClaimed() {
        return kitsClaimed;
    }

    public List<ItemStack> mailbox() {
        return mailbox;
    }

    public boolean offerMailbox(ItemStack item) {
        if (item == null || item.getType().isAir() || mailbox.size() >= 54) {
            return false;
        }
        mailbox.add(item.clone());
        return true;
    }
}
