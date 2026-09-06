package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.PlayerState;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa setbuild —— 设置可挖掘区域对角点（区域内地图方块可破坏） */
public class SetBuildSub implements SubCommand {

    @Override
    public java.util.Set<PlayerState> states() {
        return java.util.EnumSet.of(PlayerState.SETUPING);
    }

    @Override
    public String name() {
        return "setbuild";
    }

    @Override
    public String description() {
        return "设置可挖掘区域对角点";
    }

    @Override
    public String params() {
        return "<名字> <pos1|pos2>";
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
            Msg.send(p, "setbuild.usage");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "error.arena-missing", args[1]);
            return;
        }
        if (plugin.games().arenaInUse(arena)) Msg.send(p, "setup.in-use-hint");
        int index;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "pos1", "1" -> index = 1;
            case "pos2", "2" -> index = 2;
            default -> {
                Msg.send(p, "setbuild.bad-pos");
                return;
            }
        }
        if (!CmdUtil.checkSetupArena(plugin, p, arena)) return;
        if (!CmdUtil.teleportToArenaWorld(plugin, p, arena)) return;
        arena.setBuildPos(index, p.getLocation());
        plugin.arenas().saveAll();
        Msg.send(p, "setbuild.success", arena.getName(), index);
        Msg.send(p, arena.hasBuildRegion() ? "setbuild.done" : "setbuild.need-other");
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) return new ArrayList<>(plugin.arenas().names());
        if (args.length == 3) return List.of("pos1", "pos2");
        return List.of();
    }
}
