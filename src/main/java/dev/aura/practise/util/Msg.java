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
 * 消息出口：所有玩家可见文本都从 messages.yml 按键读取（用户可改）。
 * - send / broadcast：前缀 + 正文
 * - title：主/副标题
 * - 占位符 {name} 由调用方以 "name", value 变参传入；值里可含 § 或 & 颜色码
 */
public final class Msg {

    private static Messages messages;

    private Msg() {
    }

    /** 主类 onEnable 时注入（先于任何消息使用） */
    public static void init(Messages msg) {
        messages = msg;
    }

    private static String raw(String key, String... replacements) {
        String text = messages.get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }

    /** & 和 § 颜色码 → Component */
    public static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text.replace('§', '&'));
    }

    private static Component body(String key, String... replacements) {
        return legacy(raw(key, replacements));
    }

    private static Component prefixComponent() {
        return legacy(raw("prefix"));
    }

    /** 可配置前缀 Component（菜单/邀请等需要自行拼装的场合） */
    public static Component prefix() {
        return prefixComponent();
    }

    /** 原始消息文本（占位符已替换、& 颜色码保留），供 BossBar 等旧式 API 转换使用 */
    public static String text(String key, String... replacements) {
        return raw(key, replacements);
    }

    /** 按键取消息并转为 Component（等价于 send 的正文，无前缀） */
    public static Component component(String key, String... replacements) {
        return body(key, replacements);
    }

    // ------------------------------------------------------------------
    // 聊天
    // ------------------------------------------------------------------

    public static void send(CommandSender to, String key, String... replacements) {
        to.sendMessage(prefixComponent().append(body(key, replacements)));
    }

    // ------------------------------------------------------------------
    // 标题
    // ------------------------------------------------------------------

    public static void title(Player p, String mainKey, String subKey, String... replacements) {
        p.showTitle(Title.title(
                body(mainKey, replacements),
                body(subKey, replacements),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))));
    }

    /** 纯文本组件（菜单等需要自定义结构的场合），支持 & 颜色码 */
    public static Component plain(String text, net.kyori.adventure.text.format.NamedTextColor color) {
        return legacy(text).colorIfAbsent(color);
    }
}
