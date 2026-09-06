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

/** /pa kit —— 把当前背包+盔甲存为模式级 kit（该模式所有竞技场共用） */
public class KitSub implements SubCommand {

    @Override
    public String name() {
        return "kit";
    }

    @Override
    public String description() {
        return "把当前背包存为模式 kit（该模式所有竞技场共用）";
    }

    @Override
    public String params() {
        return "<模式> [clear|forget <玩家>]";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender) || !CmdUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        if (args.length < 2) {
            Msg.send(p, "kit.usage");
            Msg.send(p, "error.modes", ModeRegistry.ids());
            return;
        }
        ModeHandler mode = ModeRegistry.parse(args[1]);
        if (mode == null) {
            Msg.send(p, "kit.unknown-mode", args[1], ModeRegistry.ids());
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("clear")) {
            plugin.kits().clear(mode.id());
            Msg.send(p, "kit.cleared", mode.display());
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("forget")) {
            // /pa kit <模式> forget <玩家>：清除该玩家的个人 kit，恢复跟随模式 kit
            if (args.length < 4) {
                Msg.send(p, "kit.forget-usage");
                return;
            }
            // 只按在线玩家名和本地缓存里的存档名解析；
            // 不用 Bukkit.getOfflinePlayer(名字)——未知名字会触发阻塞式 Mojang API 查询（主线程）
            java.util.UUID target = null;
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(args[3])) {
                    target = online.getUniqueId();
                    break;
                }
            }
            if (target == null) {
                for (java.util.UUID id : plugin.playerKits().storedPlayers()) {
                    String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
                    if (name != null && name.equalsIgnoreCase(args[3])) {
                        target = id;
                        break;
                    }
                }
            }
            if (target != null && plugin.playerKits().clear(target, mode.id())) {
                Msg.send(p, "kit.forgot", args[3], mode.display());
            } else {
                Msg.send(p, "kit.forgot-none", args[3], mode.display());
            }
            return;
        }
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        // 带 PDC 标记的大厅/排队物品（菜单剑、快速加入、退出排队染料）不存进 kit：会被原样发给全场玩家
        List<org.bukkit.inventory.ItemStack> storage = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack stack : inv.getStorageContents()) {
            storage.add(plugin.lobbyMenu().tagOf(stack) == null ? stack : null);
        }
        plugin.kits().set(mode.id(), new dev.aura.practise.manager.KitManager.Kit(
                storage, inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()));
        Msg.send(p, "kit.saved", mode.display());
        Msg.send(p, "kit.shared");
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            for (ModeHandler mode : ModeRegistry.all()) out.add(mode.id());
            return out;
        }
        if (args.length == 3) return List.of("clear", "forget");
        if (args.length == 4 && args[2].equalsIgnoreCase("forget")) {
            List<String> out = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) out.add(online.getName());
            return out;
        }
        return List.of();
    }
}
