package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.ArenaPosition;
import dev.aura.practise.game.Team;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa genvoid —— 一键生成虚空地图：两座浮空岛 + 床 + 自动配好出生点（支持控制台） */
public class GenVoidSub implements SubCommand {

    private static final int HEIGHT = 100;   // 平台方块所在 Y
    private static final int SIZE = 5;       // 平台半径（11x11）
    private static final int DISTANCE = 25;  // 岛中心距原点

    @Override
    public String name() {
        return "genvoid";
    }

    @Override
    public String description() {
        return "生成虚空地图（两岛+床+出生点）";
    }

    @Override
    public String params() {
        return "<名字> [世界名]";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "genvoid.usage");
            return;
        }
        org.bukkit.World world = args.length >= 3
                ? Bukkit.getWorld(args[2])
                : Bukkit.getWorlds().get(0);
        if (world == null) {
            Msg.send(sender, "genvoid.world-missing");
            return;
        }
        ModeHandler mode = dev.aura.practise.mode.ModeRegistry.get("bedfight");
        Arena arena = plugin.arenas().create(args[1], mode);
        if (arena == null) {
            Msg.send(sender, "genvoid.exists", "name", args[1]);
            return;
        }
        ArenaPosition pos = arena.position(1);
        buildIsland(world, -DISTANCE, mode, Team.RED, pos);
        buildIsland(world, DISTANCE, mode, Team.BLUE, pos);
        world.save(); // 立即落盘：虚空区块不自动保存，否则重启后地图重置
        plugin.arenas().saveAll();
        Msg.send(sender, "genvoid.success",
                "name", arena.getName(), "world", world.getName(),
                "distance", String.valueOf(DISTANCE), "height", String.valueOf(HEIGHT));
        Msg.send(sender, "genvoid.hint");
        if (sender instanceof Player p) {
            p.teleport(pos.spawn(Team.RED));
            Msg.send(p, "genvoid.teleported");
        }
    }

    /** 生成一座岛：平台 + 床（床头朝内）+ 出生点（面向对方岛） */
    private void buildIsland(org.bukkit.World world, int cx, ModeHandler mode,
                             Team team, ArenaPosition pos) {
        for (int x = cx - SIZE; x <= cx + SIZE; x++) {
            for (int z = -SIZE; z <= SIZE; z++) {
                world.getBlockAt(x, HEIGHT, z).setType(Material.QUARTZ_BLOCK);
            }
        }
        boolean red = team == Team.RED;
        int bedHeadX = cx + (red ? -SIZE + 1 : SIZE - 1);
        BlockFace facing = red ? BlockFace.EAST : BlockFace.WEST;
        Material bedMat = red ? Material.RED_BED : Material.BLUE_BED;
        Block head = world.getBlockAt(bedHeadX, HEIGHT + 1, 0);
        Bed headData = (Bed) bedMat.createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        head.setBlockData(headData);
        Block foot = head.getRelative(facing.getOppositeFace());
        Bed footData = (Bed) bedMat.createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        foot.setBlockData(footData);
        pos.setBed(team, head.getLocation(), facing);
        Location spawn = new Location(world, cx + (red ? 2 : -2), HEIGHT + 1, 0,
                red ? -90f : 90f, 0f); // -90=朝东(+X)，90=朝西(-X)
        pos.setSpawn(team, spawn);
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 3) { // /pa genvoid <名> <世界名>
            List<String> out = new ArrayList<>();
            for (org.bukkit.World world : Bukkit.getWorlds()) out.add(world.getName());
            return out;
        }
        return List.of();
    }
}
