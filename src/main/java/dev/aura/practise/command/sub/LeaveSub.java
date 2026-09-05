package dev.aura.practise.command.sub;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa leave —— 离开当前游戏/排队 */
public class LeaveSub implements SubCommand {

    @Override
    public String name() {
        return "leave";
    }

    @Override
    public String description() {
        return "离开当前游戏";
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.isPlayer(sender)) return;
        plugin.games().leave((Player) sender);
    }
}
