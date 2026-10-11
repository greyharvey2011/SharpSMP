package dev.sharpsmp.text;

import java.util.Locale;

public final class Format {
    public static final long MAX = 9_000_000_000_000L;

    private Format() {
    }

    public static String money(long amount, String symbol) {
        long abs = Math.abs(amount);
        if (abs < 10_000L) {
            return symbol + String.format(Locale.US, "%,d", amount);
        }
        double scaled;
        String suffix;
        if (abs >= 1_000_000_000L) {
            scaled = amount / 1_000_000_000d;
            suffix = "B";
        } else if (abs >= 1_000_000L) {
            scaled = amount / 1_000_000d;
            suffix = "M";
        } else {
            scaled = amount / 1_000d;
            suffix = "K";
        }
        return symbol + compact(scaled) + suffix;
    }

    public static String playtime(long minutes) {
        if (minutes < 0) {
            minutes = 0;
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }

    public static String duration(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    public static String kdr(int kills, int deaths) {
        if (deaths <= 0) {
            return kills <= 0 ? "0.00" : String.format(Locale.US, "%.2f", (double) kills);
        }
        return String.format(Locale.US, "%.2f", kills / (double) deaths);
    }

    public static Long amount(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replace(",", "").replace("_", "");
        if (text.startsWith("$") || text.startsWith("◆")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty()) {
            return null;
        }
        long multiplier = 1L;
        char last = Character.toLowerCase(text.charAt(text.length() - 1));
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
            text = text.substring(0, text.length() - 1).trim();
        }
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || value < 0) {
                return null;
            }
            double scaled = value * multiplier;
            if (!Double.isFinite(scaled) || Math.abs(scaled - Math.rint(scaled)) > 0.001d) {
                return null;
            }
            long rounded = Math.round(scaled);
            if (rounded <= 0 || rounded > MAX) {
                return null;
            }
            return rounded;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static long tax(long amount, int percent) {
        if (amount <= 0 || percent <= 0) {
            return 0L;
        }
        percent = Math.min(percent, 100);
        return (amount * percent) / 100L;
    }

    public static Long cost(long amount, int percent) {
        if (amount <= 0) {
            return null;
        }
        try {
            return Math.addExact(amount, tax(amount, percent));
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static String compact(double value) {
        double rounded = Math.round(value * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001d) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.US, "%.1f", rounded);
    }
}
