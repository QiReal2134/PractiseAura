package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.PendingSetting;
import dev.aura.practise.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /pa setting —— 全局参数设置。
 * 静态 apply/value 供 setup 菜单与聊天输入（PendingSetting）复用。
 */
public class SettingSub implements SubCommand {

    /** key → 可循环的预设值（next 用） */
    public static final Map<String, List<String>> CYCLES = Map.ofEntries(
            Map.entry("void-below-spawn", List.of("0", "5", "8", "12", "20", "32")),
            Map.entry("respawn-seconds", List.of("0", "1", "3", "5", "8")),
            Map.entry("spawn-protection-seconds", List.of("0", "1", "2", "3", "5")),
            Map.entry("fireball-power-x", List.of("0.8", "1.2", "1.6", "2.0", "2.5", "3.0")),
            Map.entry("fireball-power-y", List.of("0.4", "0.8", "1.2", "1.6")),
            Map.entry("fireball-damage", List.of("0", "1", "2", "4", "6")),
            Map.entry("fireball-radius", List.of("2", "2.5", "3", "4")),
            Map.entry("fireball-cooldown-seconds", List.of("0", "0.5", "1", "1.5", "2", "3")),
            Map.entry("countdown-seconds", List.of("5", "10", "15", "30")),
            Map.entry("round-countdown-seconds", List.of("1", "2", "3", "5", "10")),
            Map.entry("team-size", List.of("1", "2", "3", "4")),
            Map.entry("rounds", List.of("1", "2", "3", "5")),
            Map.entry("duel-invite-seconds", List.of("15", "30", "60")),
            Map.entry("duel-cooldown-seconds", List.of("0", "5", "10", "30")),
            Map.entry("kill-credit-window-seconds", List.of("0", "5", "8", "15")),
            Map.entry("totem-duration-seconds", List.of("1", "1.5", "2.5", "4")),
            Map.entry("totem-scale", List.of("1", "1.4", "1.8", "2.5")),
            Map.entry("kit-blocks", List.of("16", "24", "32", "48")),
            Map.entry("guard-scan-radius", List.of("3", "4", "5", "6")),
            Map.entry("show-admin-commands", List.of("true", "false")));

    @Override
    public String name() {
        return "setting";
    }

    @Override
    public String description() {
        return "设置全局参数";
    }

    @Override
    public String params() {
        return "<key> [值|next|input]";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    /** 当前生效值（从 Settings 缓存层读取，与实际行为一致） */
    public static String value(PractiseAuraPlugin plugin, String key) {
        var s = plugin.settings();
        return switch (key) {
            case "void-below-spawn" -> String.valueOf(s.voidBelowSpawn());
            case "respawn-seconds" -> String.valueOf(s.respawnSeconds());
            case "spawn-protection-seconds" -> String.valueOf(s.spawnProtectionSeconds());
            case "fireball-power-x" -> String.valueOf(s.fireballPowerX());
            case "fireball-power-y" -> String.valueOf(s.fireballPowerY());
            case "fireball-damage" -> String.valueOf(s.fireballDamage());
            case "fireball-radius" -> String.valueOf(s.fireballRadius());
            case "fireball-cooldown-seconds" -> String.valueOf(s.fireballCooldownSeconds());
            case "countdown-seconds" -> String.valueOf(s.countdownSeconds());
            case "round-countdown-seconds" -> String.valueOf(s.roundCountdownSeconds());
            case "duel-invite-seconds" -> String.valueOf(s.duelInviteSeconds());
            case "duel-cooldown-seconds" -> String.valueOf(s.duelCooldownSeconds());
            case "kill-credit-window-seconds" -> String.valueOf(s.killCreditWindowSeconds());
            case "totem-duration-seconds" -> String.valueOf(s.totemDurationSeconds());
            case "totem-scale" -> String.valueOf(s.totemScale());
            case "kit-blocks" -> String.valueOf(s.kitBlocks());
            case "guard-scan-radius" -> String.valueOf(s.guardScanRadius());
            case "rounds" -> String.valueOf(s.rounds());
            case "team-size" -> String.valueOf(s.teamSize());
            case "show-admin-commands" -> String.valueOf(s.showAdminCommands());
            default -> "?";
        };
    }

    /** 解析并写入配置（供命令与聊天输入共用），写入后刷新 Settings 缓存 */
    public static void apply(PractiseAuraPlugin plugin, CommandSender to, String key, String value) {
        List<String> cycle = CYCLES.get(key);
        if (cycle == null) {
            Msg.send(to, "setting.unknown-key", key);
            return;
        }
        if (value.equalsIgnoreCase("next")) {
            String current = value(plugin, key);
            int index = cycle.indexOf(current);
            value = cycle.get(Math.max(0, (index + 1) % cycle.size()));
        }
        Object parsed;
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            parsed = Boolean.parseBoolean(value);
        } else {
            double d;
            try {
                d = Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                Msg.send(to, "setting.bad-number", value);
                return;
            }
            if (d < 0) {
                Msg.send(to, "setting.negative");
                return;
            }
            parsed = d;
        }
        plugin.getConfig().set("settings." + key, parsed);
        plugin.settings().refresh(); // 先刷新，把越界值 clamp 到实际生效的范围
        // 把 clamp 后的生效值写回 config：文件内容与实际行为一致，整数也不会以 2.0 形式落盘
        String effective = value(plugin, key);
        if (effective.equalsIgnoreCase("true") || effective.equalsIgnoreCase("false")) {
            plugin.getConfig().set("settings." + key, Boolean.parseBoolean(effective));
        } else {
            try {
                double d = Double.parseDouble(effective);
                if (effective.indexOf('.') < 0) {
                    // Integer 落盘（三元表达式会把 long 提升回 double，写成 30.0）
                    plugin.getConfig().set("settings." + key, (int) d);
                } else {
                    plugin.getConfig().set("settings." + key, d);
                }
            } catch (NumberFormatException ignored) {
                // value() 意外返回非数值时保留第一次写入的原始值
            }
        }
        plugin.saveConfig();
        plugin.settings().refresh(); // 以落盘后的配置为准再对齐一次缓存
        String shown = effective.equals("0") ? "0（关闭）" : effective;
        Msg.send(to, "setting.applied", key, shown);
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender)) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            if (sender instanceof Player p) plugin.pendingSettings().remove(p.getUniqueId());
            Msg.send(sender, "setting.cancelled");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "setting.keys", String.join(", ", CYCLES.keySet()));
            Msg.send(sender, "setting.usage");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!CYCLES.containsKey(key)) {
            Msg.send(sender, "setting.unknown-key", key);
            return;
        }
        if (args.length >= 3 && !args[2].equalsIgnoreCase("input")) {
            apply(plugin, sender, key, args[2]);
            return;
        }
        // 无值 / input：进入聊天输入模式（菜单点击走这条路）
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "setting.console");
            return;
        }
        plugin.pendingSettings().put(p.getUniqueId(),
                new PendingSetting(key, System.currentTimeMillis() + 30_000L));
        p.sendMessage(Msg.prefix()
                .append(Msg.component("setting.input-prompt", key)));
        p.sendMessage(Msg.prefix()
                .append(Msg.legacy(Msg.text("setting.cancel-btn"))
                        .clickEvent(ClickEvent.runCommand("/pa setting cancel"))
                        .hoverEvent(HoverEvent.showText(Msg.legacy(Msg.text("setting.cancel-hover"))))));
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) return new ArrayList<>(CYCLES.keySet());
        if (args.length == 3) {
            String key = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>(CYCLES.getOrDefault(key, List.of()));
            if (!out.isEmpty()) { out.add("next"); out.add("input"); }
            return out;
        }
        return List.of();
    }
}
