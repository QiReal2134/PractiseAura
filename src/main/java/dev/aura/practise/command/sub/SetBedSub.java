package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.PendingBed;
import dev.aura.practise.game.Team;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa setbed —— 设置某组点位的床（执行后 60 秒内左键点击目标床） */
public class SetBedSub implements SubCommand {

    @Override
    public String name() {
        return "setbed";
    }

    @Override
    public String description() {
        return "设置床（执行后左键点床，可带组号）";
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
            Msg.send(p, "setbed.usage");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "error.arena-missing", args[1]);
            return;
        }
        if (plugin.games().arenaInUse(arena)) Msg.send(p, "setup.in-use-hint");
        Team team = Team.parse(args[2]);
        if (team == null) {
            Msg.send(p, "setspawn.bad-team");
            return;
        }
        int group = CmdUtil.parseGroup(args, 3);
        arena.ensurePosition(group);
        if (!CmdUtil.teleportToArenaWorld(plugin, p, arena)) return;
        plugin.pendingBeds().put(p.getUniqueId(),
                new PendingBed(arena.getName(), team, group, System.currentTimeMillis() + 60_000L));
        Msg.send(p, "setbed.pending", group, team.display());
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) return new ArrayList<>(plugin.arenas().names());
        if (args.length == 3) return List.of("red", "blue");
        if (args.length == 4) return List.of("1", "2", "3");
        return List.of();
    }
}
