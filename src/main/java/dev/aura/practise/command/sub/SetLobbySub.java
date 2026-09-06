package dev.aura.practise.command.sub;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.PlayerState;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa setlobby —— 把当前位置设为大厅 */
public class SetLobbySub implements SubCommand {

    @Override
    public java.util.Set<PlayerState> states() {
        return java.util.EnumSet.of(PlayerState.LOBBYING);
    }

    @Override
    public String name() {
        return "setlobby";
    }

    @Override
    public String description() {
        return "把当前位置设为大厅";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.isPlayer(sender)) return;
        plugin.setLobby(((Player) sender).getLocation());
        Msg.send(sender, "setup.lobby-set");
    }
}
