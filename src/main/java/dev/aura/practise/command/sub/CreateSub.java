package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.PlayerState;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;

/** /pa create —— 创建竞技场 */
public class CreateSub implements SubCommand {

    @Override
    public java.util.Set<PlayerState> states() {
        return java.util.EnumSet.of(PlayerState.LOBBYING);
    }

    @Override
    public String name() {
        return "create";
    }

    @Override
    public String description() {
        return "创建竞技场";
    }

    @Override
    public String params() {
        return "<名字> <模式>";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender)) return;
        if (args.length < 3) {
            Msg.send(sender, "create.usage", ModeRegistry.ids());
            return;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) {
            Msg.send(sender, "create.bad-name");
            return;
        }
        ModeHandler mode = ModeRegistry.parse(args[2]);
        if (mode == null) {
            Msg.send(sender, "error.unknown-mode", args[2], ModeRegistry.ids());
            return;
        }
        Arena arena = plugin.arenas().create(name, mode);
        if (arena == null) {
            Msg.send(sender, "create.exists", name);
            return;
        }
        Msg.send(sender, "create.success", name, mode.display());
        Msg.send(sender, "create.hint", name);
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 3) return List.of();
        List<String> out = new ArrayList<>();
        for (ModeHandler mode : ModeRegistry.all()) out.add(mode.id());
        return out;
    }
}
