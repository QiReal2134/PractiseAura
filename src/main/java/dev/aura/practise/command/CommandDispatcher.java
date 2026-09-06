package dev.aura.practise.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.PlayerState;
import dev.aura.practise.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /pa 命令分发器：注册所有子命令，统一处理执行、权限、help 与 Tab 补全 */
public class CommandDispatcher implements CommandExecutor, TabCompleter {

    private final PractiseAuraPlugin plugin;
    private final Map<String, SubCommand> subs = new LinkedHashMap<>();

    public CommandDispatcher(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(SubCommand sub) {
        subs.put(sub.name().toLowerCase(), sub);
        for (String alias : sub.aliases()) {
            subs.putIfAbsent(alias.toLowerCase(), sub);
        }
    }

    private List<SubCommand> distinct() {
        Set<SubCommand> seen = new HashSet<>();
        List<SubCommand> out = new ArrayList<>();
        for (SubCommand sub : subs.values()) {
            if (seen.add(sub)) out.add(sub);
        }
        return out;
    }

    private boolean visible(CommandSender sender, SubCommand sub) {
        if (sub.permission() == null) return true; // 玩家命令（注意：hasPermission(null) 会抛异常）
        if (!sender.hasPermission(sub.permission())) return false;
        return sub.alwaysShow() || plugin.settings().showAdminCommands();
    }

    private void help(CommandSender sender) {
        Msg.send(sender, "help.header");
        PlayerState state = sender instanceof Player p ? PlayerState.of(plugin, p) : null;
        for (SubCommand sub : distinct()) {
            if (!visible(sender, sub)) continue;
            if (state != null && !stateAllowed(state, sub)) continue; // 当前状态不可用的不显示
            String params = sub.params().isEmpty() ? "" : " " + sub.params();
            Msg.send(sender, "help.entry", sub.name(), params, sub.description());
        }
    }

    /** 玩家当前状态是否允许该子命令（控制台/无状态限制恒真） */
    private boolean stateAllowed(PlayerState state, SubCommand sub) {
        return sub.states().contains(state);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        SubCommand sub = subs.get(args[0].toLowerCase());
        if (sub == null) {
            help(sender);
            return true;
        }
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            Msg.send(sender, "error.no-permission");
            return true;
        }
        if (sender instanceof Player p && !sub.states().contains(PlayerState.of(plugin, p))) {
            Msg.send(sender, "state.blocked",
                    PlayerState.of(plugin, p).display(), sub.name());
            return true;
        }
        sub.execute(plugin, sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        PlayerState state = sender instanceof Player p ? PlayerState.of(plugin, p) : null;
        if (args.length <= 1) {
            List<String> out = new ArrayList<>();
            for (SubCommand sub : distinct()) {
                if (!visible(sender, sub)) continue;
                if (state != null && !stateAllowed(state, sub)) continue; // 按状态动态过滤
                out.add(sub.name());
                out.addAll(sub.aliases()); // 别名（如 arenas/lobby）也参与补全
            }
            return filter(out, args.length == 0 ? "" : args[0]);
        }
        SubCommand sub = subs.get(args[0].toLowerCase());
        if (sub == null) return List.of();
        // 参数补全只看权限；不用 visible()——隐藏开关不该挡住有权限的人补全参数
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) return List.of();
        return filter(sub.tab(plugin, sender, args), args[args.length - 1]);
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> out = new ArrayList<>();
        String low = prefix.toLowerCase();
        for (String s : list) {
            if (s.toLowerCase().startsWith(low)) out.add(s);
        }
        return out;
    }
}
