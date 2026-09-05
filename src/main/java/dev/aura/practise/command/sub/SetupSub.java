package dev.aura.practise.command.sub;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.CmdUtil;
import dev.aura.practise.command.SubCommand;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.ArenaPosition;
import dev.aura.practise.game.Team;
import dev.aura.practise.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pa setup —— 可视化配置菜单：列出所有配置项，点击即执行对应命令 */
public class SetupSub implements SubCommand {

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public String description() {
        return "打开可视化配置菜单";
    }

    @Override
    public String params() {
        return "<名字>";
    }

    @Override
    public String permission() {
        return CmdUtil.ADMIN_PERM;
    }

    @Override
    public boolean alwaysShow() {
        return true; // 配置入口永远显示（不受 show-admin-commands 影响）
    }

    @Override
    public void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        if (!CmdUtil.adminCheck(sender) || !CmdUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        if (args.length < 2) {
            Msg.send(p, "setup.usage");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "error.arena-missing", "arena", args[1]);
            return;
        }
        if (!CmdUtil.teleportToArenaWorld(plugin, p, arena)) return;
        String name = arena.getName();
        p.sendMessage(Msg.plain("=== " + name + " [" + arena.getType().display() + "] 配置 ===", NamedTextColor.AQUA));
        p.sendMessage(arena.isReady()
                ? Msg.plain("状态: 就绪 ✔", NamedTextColor.GREEN)
                : Msg.plain("状态: " + arena.missingHint(), NamedTextColor.RED));
        p.sendMessage(Msg.plain("点位组数: " + arena.positionsCount()
                + "（同图可并发 " + Math.max(1, arena.positionsCount()) + " 场；setspawn/setbed 可带组号）",
                NamedTextColor.GRAY));
        for (int i = 1; i <= arena.positionsCount(); i++) {
            ArenaPosition pos = arena.position(i);
            boolean spawnsOk = pos.hasSpawn(Team.RED) && pos.hasSpawn(Team.BLUE);
            boolean bedsOk = !arena.getType().needsBeds()
                    || (pos.hasBed(Team.RED) && pos.hasBed(Team.BLUE));
            boolean complete = pos.isComplete(arena.getType().needsBeds());
            p.sendMessage(Msg.plain("  点位 " + i + ": 出生点 " + (spawnsOk ? "✔" : "✘")
                            + " 床 " + (bedsOk ? "✔" : "✘") + (complete ? "" : "（未完成）"),
                    complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        }
        menuLine(p, "大厅", plugin.lobby() != null, "/pa setlobby", "站在大厅位置点击设置");
        menuLine(p, "红队出生点", arena.position(1).hasSpawn(Team.RED), "/pa setspawn " + name + " red", "站在红队出生点位置点击");
        menuLine(p, "蓝队出生点", arena.position(1).hasSpawn(Team.BLUE), "/pa setspawn " + name + " blue", "站在蓝队出生点位置点击");
        menuLine(p, "红队的床", arena.position(1).hasBed(Team.RED), "/pa setbed " + name + " red", "点击后 60 秒内左键点击红队的床");
        menuLine(p, "蓝队的床", arena.position(1).hasBed(Team.BLUE), "/pa setbed " + name + " blue", "点击后 60 秒内左键点击蓝队的床");
        menuLine(p, "挖掘区 pos1", arena.hasBuildRegion(), "/pa setbuild " + name + " pos1", "站在矩形区域的一个对角点点击（可选）");
        menuLine(p, "挖掘区 pos2", arena.hasBuildRegion(), "/pa setbuild " + name + " pos2", "站在矩形区域的另一个对角点点击（可选）");
        actionLine(p, "记录围床并同步两床（已记录 " + arena.getGuardEntries().size() + " 块）",
                NamedTextColor.YELLOW, "/pa guard " + name + " ready",
                "先在第 1 组红队的床周围放好防护方块（半径" + plugin.settings().guardScanRadius() + "内），点击后自动同步到蓝床");
        actionLine(p, "清除围床", NamedTextColor.GRAY, "/pa guard " + name + " clear", "清除已记录的围床结构");
        actionLine(p, "删除竞技场", NamedTextColor.RED, "/pa delete " + name, "删除 " + name);
        p.sendMessage(Msg.plain("=== 全局设置（点击后按提示在聊天栏输入数值）===", NamedTextColor.AQUA));
        actionLine(p, "虚空处死: " + SettingSub.value(plugin, "void-below-spawn") + "格", NamedTextColor.YELLOW,
                "/pa setting void-below-spawn input", "低于本队出生点Y多少格处死，0=关闭");
        actionLine(p, "重生等待: " + SettingSub.value(plugin, "respawn-seconds") + "秒", NamedTextColor.YELLOW,
                "/pa setting respawn-seconds input", "死亡后幽灵状态等待秒数，0=立即重生");
        actionLine(p, "重生保护: " + SettingSub.value(plugin, "spawn-protection-seconds") + "秒", NamedTextColor.YELLOW,
                "/pa setting spawn-protection-seconds input", "重生/开局免伤秒数，主动攻击会打破保护，0=关闭");
        actionLine(p, "火球水平威力: " + SettingSub.value(plugin, "fireball-power-x"), NamedTextColor.YELLOW,
                "/pa setting fireball-power-x input", "爆炸水平击退力度");
        actionLine(p, "火球垂直威力: " + SettingSub.value(plugin, "fireball-power-y"), NamedTextColor.YELLOW,
                "/pa setting fireball-power-y input", "爆炸垂直击退力度（炸上天）");
        actionLine(p, "火球伤害: " + SettingSub.value(plugin, "fireball-damage"), NamedTextColor.YELLOW,
                "/pa setting fireball-damage input", "对敌人的伤害（自己/队友免疫），0=纯击退");
        actionLine(p, "火球半径: " + SettingSub.value(plugin, "fireball-radius"), NamedTextColor.YELLOW,
                "/pa setting fireball-radius input", "爆炸影响半径");
        actionLine(p, "火球冷却: " + SettingSub.value(plugin, "fireball-cooldown-seconds") + "秒", NamedTextColor.YELLOW,
                "/pa setting fireball-cooldown-seconds input", "两次火球发射的最小间隔，0=无冷却");
        actionLine(p, "默认回合数: " + SettingSub.value(plugin, "rounds"), NamedTextColor.YELLOW,
                "/pa setting rounds input", "排队对局的回合数（duel 可单独指定更多局）");
        actionLine(p, "局间倒计时: " + SettingSub.value(plugin, "round-countdown-seconds") + "秒", NamedTextColor.YELLOW,
                "/pa setting round-countdown-seconds input", "多局制局间切换的锁位倒计时");
        actionLine(p, "约战有效期: " + SettingSub.value(plugin, "duel-invite-seconds") + "秒", NamedTextColor.YELLOW,
                "/pa setting duel-invite-seconds input", "约战邀请的有效时长");
        actionLine(p, "击杀归属窗口: " + SettingSub.value(plugin, "kill-credit-window-seconds") + "秒", NamedTextColor.YELLOW,
                "/pa setting kill-credit-window-seconds input", "死亡前打过人计入击杀的窗口，0=不归属");
        actionLine(p, "图腾特效: " + SettingSub.value(plugin, "totem-duration-seconds") + "秒 / x"
                + SettingSub.value(plugin, "totem-scale"), NamedTextColor.YELLOW,
                "/pa setting totem-duration-seconds input", "匹配特效时长（缩放: /pa setting totem-scale input）");
        actionLine(p, "队伍规模: " + SettingSub.value(plugin, "team-size") + "v" + SettingSub.value(plugin, "team-size"),
                NamedTextColor.YELLOW, "/pa setting team-size input", "每队人数（1=1v1，2=2v2，最大 4v4），满员自动开局");
        actionLine(p, "显示管理命令: " + SettingSub.value(plugin, "show-admin-commands"), NamedTextColor.GRAY,
                "/pa setting show-admin-commands next", "点击切换 true/false");
        p.sendMessage(Msg.plain("kit 为按模式设置: /pa kit <模式> [clear]（对该模式所有竞技场生效）",
                NamedTextColor.GRAY));
    }

    private void menuLine(Player p, String label, boolean ok, String cmd, String hover) {
        p.sendMessage(Component.text(" ", NamedTextColor.GRAY)
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(label, ok ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(ok ? "✔ 已设置" : "✘ 未设置",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED))
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)
                        .append(Component.text("\n点击执行: " + cmd, NamedTextColor.DARK_GRAY)))));
    }

    private void actionLine(Player p, String label, NamedTextColor color, String cmd, String hover) {
        p.sendMessage(Component.text(" ", NamedTextColor.GRAY)
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(label, color))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)
                        .append(Component.text("\n点击执行: " + cmd, NamedTextColor.DARK_GRAY)))));
    }
}
