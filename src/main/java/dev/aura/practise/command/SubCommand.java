package dev.aura.practise.command;

import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.command.CommandSender;

/**
 * 子命令接口：新增命令 = 实现本接口并在 CommandDispatcher 注册，
 * 自动获得 help / 权限 / Tab 补全 / 隐藏开关支持。
 */
public interface SubCommand {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    String description();

    /** 用法中的参数部分（如 "<玩家> [模式]"） */
    default String params() {
        return "";
    }

    /** null = 无权限要求（玩家命令） */
    default String permission() {
        return null;
    }

    /** 管理命令隐藏开关（show-admin-commands）不影响其显示 */
    default boolean alwaysShow() {
        return false;
    }

    void execute(PractiseAuraPlugin plugin, CommandSender sender, String[] args);

    /** args[0] 恒为本子命令名，args[1..] 为参数 */
    default List<String> tab(PractiseAuraPlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
