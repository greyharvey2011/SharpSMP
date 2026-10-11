package dev.sharpsmp.game;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Settings {
    public record Option(String id, String name, String detail, Material icon) {
    }

    public static final List<Option> ALL = List.of(
            new Option("scoreboard", "Scoreboard", "Sidebar with Sharps, kills, and your bounty.", Material.PAINTING),
            new Option("sounds", "Sounds", "Menu clicks and Edge ambience.", Material.NOTE_BLOCK),
            new Option("tpa", "TPA Requests", "Let other players request a teleport.", Material.ENDER_PEARL),
            new Option("bounty-alerts", "Bounty Alerts", "Hear when a new price is placed.", Material.NAME_TAG),
            new Option("pay-alerts", "Payment Alerts", "Hear when someone pays you.", Material.GOLD_INGOT),
            new Option("particles", "Particles", "Extra effects while you stand on The Edge.", Material.BLAZE_POWDER),
            new Option("public-chat", "Public Chat", "See messages in public chat.", Material.OAK_SIGN),
            new Option("death-messages", "Death Messages", "See the kill feed.", Material.SKELETON_SKULL),
            new Option("join-messages", "Join Messages", "See who arrives at The Edge.", Material.OAK_DOOR),
            new Option("bossbar", "Edge Bar", "Show the SharpSMP bar while at spawn.", Material.RED_STAINED_GLASS),
            new Option("messages", "Private Messages", "Allow /msg from other players.", Material.WRITABLE_BOOK),
            new Option("star", "Menu Star", "Keep a nether star that opens the menu.", Material.NETHER_STAR)
    );

    private Settings() {
    }

    public static Optional<Option> find(String id) {
        for (Option option : ALL) {
            if (option.id().equals(id)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    public static String colorName(String input) {
        if (input == null) {
            return "red";
        }
        String color = input.toLowerCase(Locale.ROOT);
        return switch (color) {
            case "red", "gold", "yellow", "green", "aqua", "blue", "light_purple", "dark_red", "white", "gray" -> color;
            default -> "red";
        };
    }

    public static List<String> colors() {
        return List.of("red", "gold", "yellow", "green", "aqua", "blue", "light_purple", "dark_red", "white", "gray");
    }
}
