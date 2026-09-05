package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.command.WorldCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;

/** /pa delete —— 删除竞技场 */
public class DeleteSub implements SubCommand {

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public String description() {
        return "删除竞技场";
    }

    @Override
    public String params() {
        return "<名字>";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "delete.usage");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(sender, "error.arena-missing", args[1]);
            return;
        }
        if (plugin.games().arenaInUse(arena)) {
            Msg.send(sender, "delete.in-use", arena.getName());
            return;
        }
        // 记录地图世界：删除后若该世界不再被任何场使用且是登记的自定义世界，一并真实删除
        String worldName = arena.worldName();
        boolean worldRegistered = worldName != null && WorldCommand.isRegistered(plugin, worldName);
        plugin.arenas().delete(args[1]);
        Msg.send(sender, "delete.success", args[1]);
        if (worldRegistered && plugin.arenas().all().stream()
                .noneMatch(a -> worldName.equals(a.worldName()))) {
            WorldCommand.deleteWorldFiles(plugin, sender, worldName);
        }
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        return args.length == 2 ? new ArrayList<>(plugin.arenas().names()) : List.of();
    }
}
