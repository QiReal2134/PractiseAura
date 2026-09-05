package dev.aura.practise.command;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /bedfight 与 /fireballfight 等快捷加入命令 */
public class QuickJoinCommand implements CommandExecutor {

    private final PractiseAuraPlugin plugin;
    private final String modeId;

    public QuickJoinCommand(PractiseAuraPlugin plugin, String modeId) {
        this.plugin = plugin;
        this.modeId = modeId;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            plugin.games().join(p, ModeRegistry.get(modeId));
        } else {
            Msg.send(sender, "error.player-only");
        }
        return true;
    }
}
