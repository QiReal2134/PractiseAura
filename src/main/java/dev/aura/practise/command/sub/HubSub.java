package dev.aura.practise.command.sub;

import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Game;
import dev.aura.practise.util.LocUtil;
import dev.aura.practise.util.Msg;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /hub（/lobby）—— 回到大厅：自动处理观战退出、对局退出（按判负规则结算） */
public class HubSub implements SubCommand {

    @Override
    public String name() {
        return "hub";
    }

    @Override
    public List<String> aliases() {
        return List.of("lobby");
    }

    @Override
    public String description() {
        return "回到大厅";
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        Game spectating = plugin.games().spectatorGameOf(p.getUniqueId());
        if (spectating != null) {
            spectating.removeSpectator(p); // 退出观战并回大厅
            return;
        }
        if (plugin.games().gameOf(p.getUniqueId()) != null) {
            plugin.games().leave(p); // 离开游戏（判负/取消排队）并回大厅
            return;
        }
        Location lobby = plugin.lobby();
        if (lobby == null) {
            Msg.send(p, "lobby.missing");
            return;
        }
        if (!LocUtil.sameBlock(p.getLocation(), lobby)) {
            p.teleport(lobby);
        }
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        plugin.lobbyMenu().giveLobbyItems(p);
        plugin.updateVisibility(p);
        Msg.send(p, "hub.returned");
    }
}
