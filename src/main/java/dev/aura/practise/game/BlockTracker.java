package dev.aura.practise.game;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * 追踪一场游戏内玩家放置/破坏的方块，游戏结束后全部回滚。
 * 规则（零配置）：
 * - 玩家放置的方块：随时可拆、可被火球炸掉
 * - 地图方块：默认受保护；竞技场配置了可建设区域（/pa setbuild）时区域内可拆
 * - 床：不受爆炸影响，只能由敌方玩家手动拆
 */
public class BlockTracker {

    /** 玩家放置方块的位置 -> 放置前该位置的方块（通常是 AIR） */
    private final Map<Location, BlockData> placed = new HashMap<>();
    /** 玩家/火球破坏掉的地图方块位置 -> 原方块 */
    private final Map<Location, BlockData> broken = new HashMap<>();

    public void onPlace(Block block, BlockData replaced) {
        placed.put(block.getLocation(), replaced);
    }

    public boolean isPlaced(Block block) {
        return placed.containsKey(block.getLocation());
    }

    /** 玩家方块被拆只移除记录；地图方块被拆则记录原样以便回滚 */
    public void onBreak(Block block) {
        Location key = block.getLocation();
        if (placed.containsKey(key)) {
            placed.remove(key);
            return;
        }
        broken.putIfAbsent(key, block.getBlockData());
    }

    /** 恢复所有改动：先还原放置点（一般是变回空气），再恢复被挖的地图方块 */
    public void rollback() {
        for (Map.Entry<Location, BlockData> entry : placed.entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Location, BlockData> entry : broken.entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
        placed.clear();
        broken.clear();
    }

    private void apply(Location loc, BlockData data) {
        if (loc.getWorld() == null) return;
        loc.getBlock().setBlockData(data, false);
    }
}
