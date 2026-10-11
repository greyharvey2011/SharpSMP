package dev.sharpsmp.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Point(String world, double x, double y, double z, float yaw, float pitch) {
    public Location location() {
        World loaded = Bukkit.getWorld(world);
        if (loaded == null) {
            return null;
        }
        return new Location(loaded, x, y, z, yaw, pitch);
    }

    public static Point of(Location location) {
        return new Point(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }
}
