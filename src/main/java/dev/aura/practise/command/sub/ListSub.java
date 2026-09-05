package dev.aura.practise.command.sub;

import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.Game;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;

/** /pa list —— 查看竞技场与进行中的游戏 */
public class ListSub implements SubCommand {

    @Override
    public String name() {
        return "list";
    }

    @Override
    public List<String> aliases() {
        return List.of("arenas");
    }

    @Override
    public String description() {
        return "查看竞技场与游戏";
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        Msg.send(sender, "list.arenas-header");
        if (plugin.arenas().all().isEmpty()) {
            Msg.send(sender, "list.arenas-empty");
        }
        for (Arena arena : plugin.arenas().all()) {
            String status = arena.usedCount() > 0
                    ? "使用中 " + arena.usedCount() + "/" + arena.positionsCount() + " 组"
                    : (arena.isReady() ? "就绪（" + arena.freePositionCount() + " 组可用）" : "未配置完");
            String hint = arena.isReady() ? "" : " (" + arena.missingHint() + ")";
            Msg.send(sender, "list.arena-entry", "name", arena.getName(), "mode", arena.getType().display(), "status", status, "hint", hint);
        }
        Msg.send(sender, "list.games-header");
        if (plugin.games().active().isEmpty()) {
            Msg.send(sender, "list.games-empty");
        }
        for (Game game : plugin.games().active()) {
            Msg.send(sender, "list.game-entry", "desc", game.describe());
        }
    }
}
