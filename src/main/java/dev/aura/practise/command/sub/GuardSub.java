package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.PlayerState;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.ArenaPosition;
import dev.aura.practise.game.Team;
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

/** /pa guard —— 记录/清除围床结构（ready 记录并自动同步两床，局内可拆可炸） */
public class GuardSub implements SubCommand {

    @Override
    public java.util.Set<PlayerState> states() {
        return java.util.EnumSet.of(PlayerState.SETUPING);
    }

    @Override
    public String name() {
        return "guard";
    }

    @Override
    public String description() {
        return "记录/清除围床结构（自动同步两床）";
    }

    @Override
    public String params() {
        return "<名字> ready|clear";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender) || !CmdUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        if (args.length < 3) {
            Msg.send(p, "guard.usage");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "error.arena-missing", args[1]);
            return;
        }
        if (plugin.games().arenaInUse(arena)) Msg.send(p, "setup.in-use-hint");
        if (args[2].equalsIgnoreCase("clear")) {
            arena.clearGuards();
            plugin.arenas().saveAll();
            Msg.send(p, "guard.cleared", arena.getName());
            return;
        }
        if (!args[2].equalsIgnoreCase("ready")) {
            Msg.send(p, "guard.bad-action");
            return;
        }
        ArenaPosition pos = arena.position(1);
        if (!pos.hasBed(Team.RED) || !pos.hasBed(Team.BLUE)) {
            Msg.send(p, "guard.beds-missing");
            return;
        }
        if (!CmdUtil.checkSetupArena(plugin, p, arena)) return;
        if (!CmdUtil.teleportToArenaWorld(plugin, p, arena)) return;
        if (!arena.getType().settings().isNeedsGuard()) {
            Msg.send(p, "guard.disabled-hint",
                    arena.getType().display(), arena.getType().id());
        }
        Location head = pos.bedHead(Team.RED);
        int radius = plugin.settings().guardScanRadius();
        List<Arena.GuardEntry> entries = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = head.getBlock().getRelative(dx, dy, dz);
                    Material type = block.getType();
                    if (type.isAir() || CmdUtil.isLiquid(type) || Tag.BEDS.isTagged(type)) continue;
                    entries.add(new Arena.GuardEntry(dx, dy, dz, block.getBlockData().getAsString()));
                }
            }
        }
        if (entries.isEmpty()) {
            Msg.send(p, "guard.empty", radius);
            return;
        }
        arena.setGuards(entries);
        // 立即同步到蓝床（按两床朝向镜像变换，占用位置跳过）
        Location blue = pos.bedHead(Team.BLUE);
        int placed = 0;
        int skipped = 0;
        for (Arena.GuardEntry entry : entries) {
            Arena.GuardEntry be = arena.entryForBlue(entry);
            Block target = blue.getBlock().getRelative(be.dx(), be.dy(), be.dz());
            if (target.getType().isAir() || CmdUtil.isLiquid(target.getType())) {
                target.setBlockData(Bukkit.createBlockData(entry.data()));
                placed++;
            } else {
                skipped++;
            }
        }
        plugin.arenas().saveAll();
        Msg.send(p, "guard.recorded", entries.size());
        Msg.send(p, "guard.sync", placed);
        if (skipped > 0) Msg.send(p, "guard.sync-skipped", skipped);
        Msg.send(p, "guard.replay-hint");
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) return new ArrayList<>(plugin.arenas().names());
        if (args.length == 3) return List.of("ready", "clear");
        return List.of();
    }
}
