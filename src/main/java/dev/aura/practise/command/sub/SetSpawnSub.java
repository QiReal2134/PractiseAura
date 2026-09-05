package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.Team;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa setspawn —— 设置某组点位的队伍出生点（站在出生点执行） */
public class SetSpawnSub implements SubCommand {

    @Override
    public String name() {
        return "setspawn";
    }

    @Override
    public String description() {
        return "设置出生点（可带组号）";
    }

    @Override
    public String params() {
        return "<名字> <red|blue> [组号]";
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
            Msg.send(p, "setspawn.usage");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "error.arena-missing", "arena", args[1]);
            return;
        }
        Team team = Team.parse(args[2]);
        if (team == null) {
            Msg.send(p, "setspawn.bad-team");
            return;
        }
        int group = CmdUtil.parseGroup(args, 3);
        arena.ensurePosition(group);
        // 未带组号且地图已有配置时：自动传送到地图世界，防止配错世界
        if (args.length <= 3 && arena.firstSpawn() != null
                && !CmdUtil.teleportToArenaWorld(plugin, p, arena)) {
            return;
        }
        arena.position(group).setSpawn(team, p.getLocation());
        plugin.arenas().saveAll();
        Msg.send(p, "setspawn.success", "arena", arena.getName(), "group", String.valueOf(group), "team", team.display());
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) return new ArrayList<>(plugin.arenas().names());
        if (args.length == 3) return List.of("red", "blue");
        if (args.length == 4) return List.of("1", "2", "3");
        return List.of();
    }
}
