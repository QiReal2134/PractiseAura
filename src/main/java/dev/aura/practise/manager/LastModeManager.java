package dev.aura.practise.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.mode.ModeHandler;

/** 玩家上一次玩的模式：等待区"快速加入"物品与 /duel 缺省模式用；退出时由监听器清除 */
public class LastModeManager {

    private final Map<UUID, ModeHandler> lastMode = new HashMap<>();

    public void remember(UUID id, ModeHandler mode) {
        if (mode != null) lastMode.put(id, mode);
    }

    /** 该玩家上次玩的模式（没玩过返回 null） */
    public ModeHandler lastOf(UUID id) {
        return lastMode.get(id);
    }

    public void forget(UUID id) {
        lastMode.remove(id);
    }
}
