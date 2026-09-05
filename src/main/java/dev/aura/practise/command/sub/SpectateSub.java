package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.Game;
import dev.aura.practise.game.GameState;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa spectate —— 观战进行中的比赛 / 退出观战 */
public class SpectateSub implements SubCommand {

    @Override
    public String name() {
        return "spectate";
    }

    @Override
    public String description() {
        return "观战 / 退出观战";
    }

    @Override
    public String params() {
        return "<场名|leave>";
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        if (args.length < 2) {
            Msg.send(p, "spectate.usage");
            return;
        }
        if (args[1].equalsIgnoreCase("leave")) {
            Game spectating = plugin.games().spectatorGameOf(p.getUniqueId());
            if (spectating == null) {
                Msg.send(p, "spectate.not-spectating");
                return;
            }
            spectating.removeSpectator(p);
            return;
        }
        if (plugin.games().gameOf(p.getUniqueId()) != null) {
            Msg.send(p, "spectate.in-game");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "error.arena-missing", args[1]);
            return;
        }
        Game game = plugin.games().runningGameAt(arena);
        if (game == null) {
            Msg.send(p, "spectate.no-running");
            return;
        }
        if (game.isSpectator(p.getUniqueId())) {
            Msg.send(p, "spectate.already");
            return;
        }
        game.addSpectator(p);
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 2) return List.of();
        List<String> out = new ArrayList<>();
        out.add("leave");
        for (Game game : plugin.games().active()) {
            if (game.state() == GameState.RUNNING) out.add(game.arena().getName());
        }
        return out;
    }
}
