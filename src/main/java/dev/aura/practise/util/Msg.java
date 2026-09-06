package dev.aura.practise.util;

import java.time.Duration;
import java.util.Map;

import dev.aura.practise.PractiseAuraPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 消息出口：所有玩家可见文本从 messages.yml 按键读取（用户可改）。
 * 占位符用位置参数 {0} {1} {2}...，调用形如：
 *   Msg.send(p, "game.killed", victimName, killerName);
 * 参数值可含 & / § 颜色码（如 Team.legacyName()）。
 */
public final class Msg {

    private static Messages messages;

    private Msg() {
    }

    /** 主类 onEnable 时注入（先于任何消息使用） */
    public static void init(Messages msg) {
        messages = msg;
    }

    /** 占位符 {0} {1}...（预编译，消息发送热路径复用） */
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{(\\d+)}");

    /**
     * 单遍替换占位符：参数值里再出现 "{1}" 字样也不会被后续参数误替换
     * （顺序 replace 会把前面参数值里的占位符样文本再次展开）。
     */
    private static String raw(String key, Object... args) {
        String text = messages.get(key);
        if (args.length == 0) return text;
        java.util.regex.Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            String replacement = i < args.length ? String.valueOf(args[i]) : m.group(0);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** & 和 § 颜色码 → Component */
    public static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text.replace('§', '&'));
    }

    private static Component body(String key, Object... args) {
        return legacy(raw(key, args));
    }

    /** 可配置前缀 Component（邀请/菜单等需要自行拼装的场合） */
    public static Component prefix() {
        return legacy(raw("prefix"));
    }

    /** 原始消息文本（占位符已替换、颜色码保留），供 BossBar 等旧式 API 转换使用 */
    public static String text(String key, Object... args) {
        return raw(key, args);
    }

    /** 按键取消息并转为 Component（等价于 send 的正文，无前缀） */
    public static Component component(String key, Object... args) {
        return body(key, args);
    }

    /** 字面文本转 Component（菜单等需要自定义结构的场合），支持 & 颜色码 */
    public static Component plain(String text, net.kyori.adventure.text.format.NamedTextColor color) {
        return legacy(text).colorIfAbsent(color);
    }

    // ------------------------------------------------------------------
    // 聊天
    // ------------------------------------------------------------------

    public static void send(CommandSender to, String key, Object... args) {
        to.sendMessage(prefix().append(body(key, args)));
    }

    // ------------------------------------------------------------------
    // 标题（主/副标题共享同一组参数）
    // ------------------------------------------------------------------

    public static void title(Player p, String mainKey, String subKey, Object... args) {
        p.showTitle(Title.title(
                body(mainKey, args),
                body(subKey, args),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))));
    }
}
