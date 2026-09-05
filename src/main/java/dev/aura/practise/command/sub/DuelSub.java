package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.Game;
import dev.aura.practise.game.GameState;
import dev.aura.practise.game.PendingDuel;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa duel —— MMC 式约战：邀请发出后对方在聊天里点接受/拒绝 */
public class DuelSub implements SubCommand {

    @Override
    public String name() {
        return "duel";
    }

    @Override
    public String description() {
        return "约战（对方聊天里点接受）";
    }

    @Override
    public String params() {
        return "<玩家> [模式] [回合数] 或 accept|deny";
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        if (args.length < 2) {
            Msg.send(p, "duel.usage");
            return;
        }
        if (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("deny")) {
            reply(plugin, p, args[1].equalsIgnoreCase("accept"));
            return;
        }
        invite(plugin, p, args[1], args.length >= 3 ? args[2] : null,
                args.length >= 4 ? args[3] : null);
    }

    private void invite(PractiseAuraPlugin plugin, Player p, String targetName,
                        String modeInput, String roundsInput) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Msg.send(p, "duel.not-online", "target", targetName);
            return;
        }
        if (target.equals(p)) {
            Msg.send(p, "duel.self");
            return;
        }
        if (plugin.games().gameOf(p.getUniqueId()) != null
                || plugin.games().gameOf(target.getUniqueId()) != null) {
            Msg.send(p, "duel.in-game");
            return;
        }
        ModeHandler mode;
        if (modeInput != null) {
            mode = ModeRegistry.parse(modeInput);
            if (mode == null) {
                Msg.send(p, "duel.unknown-mode", "input", modeInput);
                return;
            }
        } else {
            ModeHandler last = plugin.lobbyMenu().lastGameOf(p.getUniqueId());
            mode = last != null ? last : ModeRegistry.get("bedfight");
        }
        int rounds = 1;
        if (roundsInput != null) {
            try {
                rounds = Math.max(1, Math.min(5, Integer.parseInt(roundsInput)));
            } catch (NumberFormatException ex) {
                Msg.send(p, "duel.bad-rounds");
                return;
            }
        }
        // 对方已有人约 → 拒绝，避免覆盖骚扰
        if (plugin.duelInvites().containsKey(target.getUniqueId())) {
            Msg.send(p, "duel.target-pending", "target", target.getName());
            return;
        }
        // 发送者防骚扰冷却（duel-cooldown-seconds，0 = 无冷却）
        long cooldownMillis = plugin.settings().duelCooldownSeconds() * 1000L;
        Long lastSent = plugin.duelCooldowns().get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0 && lastSent != null && now - lastSent < cooldownMillis) {
            Msg.send(p, "duel.cooldown", "seconds",
                    String.format("%.0f", (cooldownMillis - (now - lastSent)) / 1000.0 + 1));
            return;
        }
        plugin.duelInvites().values().removeIf(PendingDuel::expired);
        plugin.duelCooldowns().put(p.getUniqueId(), now);
        plugin.duelInvites().put(target.getUniqueId(),
                new PendingDuel(p.getUniqueId(), mode, rounds,
                        now + plugin.settings().duelInviteSeconds() * 1000L));
        Msg.send(p, "duel.sent",
                "target", target.getName(), "mode", mode.display(),
                "info", rounds > 1 ? rounds + " 局制，" : "");
        target.sendMessage(Msg.prefix()
                .append(Msg.component("duel.invite",
                        "player", p.getName(), "mode", mode.display(),
                        "rounds-info", rounds > 1 ? "（" + rounds + " 局制）" : ""))
                .append(Msg.legacy(Msg.text("duel.accept-btn"))
                        .clickEvent(ClickEvent.runCommand("/pa duel accept"))
                        .hoverEvent(HoverEvent.showText(Msg.legacy(Msg.text("duel.accept-hover")))))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Msg.legacy(Msg.text("duel.deny-btn"))
                        .clickEvent(ClickEvent.runCommand("/pa duel deny"))
                        .hoverEvent(HoverEvent.showText(Msg.legacy(Msg.text("duel.deny-hover"))))));
    }

    private void reply(PractiseAuraPlugin plugin, Player p, boolean accept) {
        PendingDuel invite = plugin.duelInvites().remove(p.getUniqueId());
        if (invite == null) {
            Msg.send(p, "duel.none");
            return;
        }
        if (invite.expired()) {
            Msg.send(p, "duel.expired");
            Player old = Bukkit.getPlayer(invite.senderId());
            if (old != null) Msg.send(old, "duel.expired-notice", "player", p.getName());
            return;
        }
        Player inviter = Bukkit.getPlayer(invite.senderId());
        if (inviter == null) {
            Msg.send(p, "duel.inviter-offline");
            return;
        }
        if (!accept) {
            Msg.send(inviter, "duel.denied-inviter", "player", p.getName());
            Msg.send(p, "duel.denied", "player", inviter.getName());
            return;
        }
        if (plugin.games().gameOf(p.getUniqueId()) != null
                || plugin.games().gameOf(inviter.getUniqueId()) != null) {
            Msg.send(p, "duel.in-game");
            return;
        }
        if (plugin.games().startDuel(inviter, p, invite.mode(), invite.rounds())) {
            Msg.send(inviter, "duel.accepted-inviter", "mode", invite.mode().display());
            Msg.send(p, "duel.accepted", "player", inviter.getName());
        } else {
            Msg.send(p, "duel.no-arena");
            Msg.send(inviter, "duel.accept-no-arena", "player", p.getName());
        }
    }

    @Override
    public List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            for (Player online : Bukkit.getOnlinePlayers()) out.add(online.getName());
            out.add("accept");
            out.add("deny");
        } else if (args.length == 3) {
            for (ModeHandler mode : ModeRegistry.all()) out.add(mode.id());
        } else if (args.length == 4) {
            out.addAll(List.of("1", "2", "3", "5"));
        }
        return out;
    }
}
