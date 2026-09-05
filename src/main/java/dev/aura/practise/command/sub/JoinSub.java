package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa join <模式> —— 加入排队 */
public class JoinSub implements SubCommand {

    @Override
    public String name() {
        return "join";
    }

    @Override
    public String description() {
        return "加入游戏";
    }

    @Override
    public String params() {
        return "<" + ModeRegistry.ids() + ">";
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.isPlayer(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "join.usage", ModeRegistry.ids());
            return;
        }
        ModeHandler mode = ModeRegistry.parse(args[1]);
        if (mode == null) {
            Msg.send(sender, "join.unknown-mode", args[1], ModeRegistry.ids());
            return;
        }
        plugin.games().join((Player) sender, mode);
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 2) return List.of();
        List<String> out = new ArrayList<>();
        for (ModeHandler mode : ModeRegistry.all()) out.add(mode.id());
        return out;
    }
}
