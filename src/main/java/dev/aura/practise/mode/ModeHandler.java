package dev.aura.practise.mode;

import java.util.List;

import dev.aura.practise.game.Game;
import dev.aura.practise.game.Team;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * 模式抽象：新增玩法 = 实现本接口 + 在 ModeRegistry 注册一行，
 * 无需改动核心（排队/匹配/床/回滚/结算全部复用）。
 */
public interface ModeHandler {

    String id();

    String display();

    /** 模式图标（选择菜单 / 匹配图腾特效） */
    Material icon();

    /** 是否以床为目标方块（床在可重生、可拆床）——默认读模式设置 */
    default boolean needsBeds() {
        return settings().isNeedsBeds();
    }

    /** 死亡后是否重生（床在的前提下）。false = 一条命，死亡即淘汰 */
    default boolean respawnOnDeath() {
        return needsBeds();
    }

    /** 模式级开关（有床/围床/伤害/PVP/破坏规则/虚空处死等，可用 /pa mode 修改） */
    ModeSettings settings();

    /** 未配置自定义 kit 时的默认装备 */
    void giveDefaultKit(Game game, Player p, Team team);

    /** 每秒 tick（仅 RUNNING） */
    default void onSecondTick(Game game) {
    }

    /** 记分板附加行 */
    default void addScoreboardLines(Game game, List<String> lines) {
    }

    /**
     * 玩家右键交互钩子（发射火球等）。返回 true 表示已处理（事件会被取消）。
     * 仅游戏进行中（RUNNING）且玩家存活时被调用，监听器已保证，模式无需重复校验。
     */
    default boolean onRightClick(Game game, Player p) {
        return false;
    }
}
