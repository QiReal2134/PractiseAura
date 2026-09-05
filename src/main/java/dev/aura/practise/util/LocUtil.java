package dev.aura.practise.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class LocUtil {

    private LocUtil() {
    }

    public static void write(ConfigurationSection parent, String key, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            parent.set(key, null);
            return;
        }
        ConfigurationSection sec = parent.createSection(key);
        sec.set("world", loc.getWorld().getName());
        sec.set("x", loc.getX());
        sec.set("y", loc.getY());
        sec.set("z", loc.getZ());
        sec.set("yaw", loc.getYaw());
        sec.set("pitch", loc.getPitch());
    }

    public static Location read(ConfigurationSection parent, String key) {
        ConfigurationSection sec = parent.getConfigurationSection(key);
        if (sec == null) return null;
        World world = Bukkit.getWorld(sec.getString("world", ""));
        if (world == null) return null;
        return new Location(
                world,
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw"),
                (float) sec.getDouble("pitch"));
    }

    /** 同一世界的同一方块位置（用于避免无意义 teleport 触发加载屏） */
    public static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
