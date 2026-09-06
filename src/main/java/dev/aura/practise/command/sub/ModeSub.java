package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.PlayerState;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import org.bukkit.command.CommandSender;

/** /pa mode —— 模式级开关（有床/围床/伤害/PVP/破坏规则/虚空处死） */
public class ModeSub implements SubCommand {

    private static final List<String> FLAGS = List.of(
            "needs-beds", "needs-guard", "damage", "pvp",
            "allow-break-map", "allow-break-placed", "allow-place", "void-kill");

    @Override
    public java.util.Set<PlayerState> states() {
        return java.util.EnumSet.of(PlayerState.LOBBYING, PlayerState.SETUPING);
    }

    @Override
    public String name() {
        return "mode";
    }

    @Override
    public String description() {
        return "设置模式开关（有床/围床/伤害/PVP/破坏等）";
    }

    @Override
    public String params() {
        return "<模式> [开关] [true|false]";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    private static String flagValue(PractiseAuraPlugin plugin, ModeHandler mode, String flag) {
        var s = mode.settings();
        return switch (flag) {
            case "needs-beds" -> String.valueOf(s.isNeedsBeds());
            case "needs-guard" -> String.valueOf(s.isNeedsGuard());
            case "damage" -> String.valueOf(s.isDamageEnabled());
            case "pvp" -> String.valueOf(s.isPvp());
            case "allow-break-map" -> String.valueOf(s.isAllowBreakMap());
            case "allow-break-placed" -> String.valueOf(s.isAllowBreakPlaced());
            case "allow-place" -> String.valueOf(s.isAllowPlace());
            case "void-kill" -> String.valueOf(s.isVoidKill());
            default -> "?";
        };
    }

    private static void applyFlag(PractiseAuraPlugin plugin, CommandSender to,
                                 ModeHandler mode, String flag, String value) {
        if (!FLAGS.contains(flag)) {
            Msg.send(to, "mode.unknown-flag", flag, String.join(", ", FLAGS));
            return;
        }
        boolean v;
        if (value.equalsIgnoreCase("next")) {
            v = !Boolean.parseBoolean(flagValue(plugin, mode, flag));
        } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            v = Boolean.parseBoolean(value);
        } else {
            Msg.send(to, "mode.bad-value");
            return;
        }
        plugin.getConfig().set("modes." + mode.id().toLowerCase() + "." + flag, v);
        plugin.saveConfig();
        ModeRegistry.refresh(plugin);
        Msg.send(to, "mode.applied", mode.display(), flag, v);
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender)) return;
        if (args.length < 2) {
            Msg.send(sender, "mode.usage");
            Msg.send(sender, "mode.modes", ModeRegistry.ids());
            return;
        }
        ModeHandler mode = ModeRegistry.parse(args[1]);
        if (mode == null) {
            Msg.send(sender, "mode.unknown-mode", args[1], ModeRegistry.ids());
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "mode.list-header", mode.display());
            for (String flag : FLAGS) {
                Msg.send(sender, "mode.list-entry", flag, flagValue(plugin, mode, flag));
            }
            Msg.send(sender, "mode.modify-hint", mode.id());
            return;
        }
        String flag = args[2].toLowerCase(Locale.ROOT);
        String value = args.length >= 4 ? args[3] : "next";
        applyFlag(plugin, sender, mode, flag, value);
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            for (ModeHandler mode : ModeRegistry.all()) out.add(mode.id());
            return out;
        }
        if (args.length == 3) return FLAGS;
        if (args.length == 4) return List.of("true", "false");
        return List.of();
    }
}
