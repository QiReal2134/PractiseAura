package dev.aura.practise.game;

import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum Team {
    RED("红队", NamedTextColor.RED),
    BLUE("蓝队", NamedTextColor.BLUE);

    private final String display;
    private final NamedTextColor color;

    Team(String display, NamedTextColor color) {
        this.display = display;
        this.color = color;
    }

    public String display() {
        return display;
    }

    public NamedTextColor color() {
        return color;
    }

    public Component displayName() {
        return Component.text(display, color);
    }

    /** 带颜色码的旧式字符串（消息占位符用） */
    public String legacyName() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(displayName());
    }

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }

    /** 解析 red/blue/红/蓝 等，失败返回 null */
    public static Team parse(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "red", "r", "红", "红队" -> RED;
            case "blue", "b", "蓝", "蓝队" -> BLUE;
            default -> null;
        };
    }
}
