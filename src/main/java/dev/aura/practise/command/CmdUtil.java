package dev.aura.practise.command;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.Arena;
import dev.aura.practise.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 子命令公共工具：权限、玩家校验、地图世界自动传送、参数解析 */
public final class CmdUtil {

    public static final String ADMIN_PERM = "practiseaura.admin";

    private CmdUtil() {
    }

    public static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERM);
    }

    public static boolean adminCheck(CommandSender sender) {
        if (!isAdmin(sender)) {
            Msg.send(sender, "error.no-permission");
            return false;
        }
        return true;
    }

    public static boolean isPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, "error.player-only");
            return false;
        }
        return true;
    }

    /**
     * 配置地图时：玩家不在地图所在世界就先传送过去（避免配错世界/位置）。
     * 返回 false 表示已传送、本次命令中断，需玩家就位后重新执行。
     */
    public static boolean teleportToArenaWorld(PractiseAuraPlugin plugin, CommandSender sender, Arena arena) {
        if (!(sender instanceof Player p)) return true; // 控制台不受影响
        String worldName = arena.worldName();
        if (worldName == null) return true; // 还没配置任何点位，无从传送
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null || p.getWorld().equals(world)) return true;
        Location target = arena.firstSpawn() != null ? arena.firstSpawn() : world.getSpawnLocation();
        p.teleport(target);
        Msg.send(p, "setup.world-teleported", world.getName());
        return false;
    }

    /** 解析可选的组号参数（默认 1，范围 1-16） */
    public static int parseGroup(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            int v = Integer.parseInt(args[index]);
            return Math.max(1, Math.min(16, v));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    public static boolean isLiquid(Material type) {
        return type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN;
    }
}
