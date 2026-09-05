package dev.aura.practise.command;

import dev.aura.practise.PractiseAuraPlugin;

/** 把 SubCommand 包装成独立顶层命令（/duel 等），自动带上 Tab 补全 */
public class SubCommandAdapter implements org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {

    private final PractiseAuraPlugin plugin;
    private final SubCommand sub;

    public SubCommandAdapter(PractiseAuraPlugin plugin, SubCommand sub) {
        this.plugin = plugin;
        this.sub = sub;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                             String label, String[] args) {
        // 子命令实现约定 args[0] 为命令名，这里补上
        String[] full = new String[args.length + 1];
        full[0] = sub.name();
        System.arraycopy(args, 0, full, 1, args.length);
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            dev.aura.practise.util.Msg.send(sender, "error.no-permission");
            return true;
        }
        sub.execute(plugin, sender, full);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender,
                                                org.bukkit.command.Command command,
                                                String alias, String[] args) {
        String[] full = new String[args.length + 1];
        full[0] = sub.name();
        System.arraycopy(args, 0, full, 1, args.length);
        return sub.tab(plugin, sender, full);
    }
}
