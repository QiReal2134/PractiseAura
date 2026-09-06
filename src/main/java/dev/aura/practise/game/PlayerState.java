package dev.aura.practise.game;

import java.util.EnumSet;
import java.util.Set;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.entity.Player;

/**
 * 玩家状态机：命令系统按状态放行/拦截。
 * LOBBYING 大厅 → WAITING 排队 → GAMING 对局中 → DIED 阵亡/淘汰观战 → 回到 LOBBYING；
 * SETUPING 管理员配置地图（/pa setup 进入，覆盖大厅态）。
 */
public enum PlayerState {

    LOBBYING("大厅"),
    WAITING("排队中"),
    GAMING("对局中"),
    DIED("阵亡/观战"),
    SPECTATING("观战中"),
    SETUPING("配置地图");

    public static final Set<PlayerState> ALL = EnumSet.allOf(PlayerState.class);

    private final String display;

    PlayerState(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** 推导玩家当前状态（setuping 由主类登记表判断） */
    public static PlayerState of(PractiseAuraPlugin plugin, Player p) {
        String setupArena = plugin.setuping().get(p.getUniqueId());
        if (setupArena != null) return SETUPING;
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game != null) {
            return switch (game.state()) {
                case WAITING -> WAITING;
                case RUNNING -> game.isAlive(p.getUniqueId()) ? GAMING : DIED;
                default -> LOBBYING; // ENDING：马上回大厅
            };
        }
        if (plugin.games().spectatorGameOf(p.getUniqueId()) != null) return SPECTATING;
        return LOBBYING;
    }
}
