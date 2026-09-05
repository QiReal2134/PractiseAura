package dev.aura.practise.manager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 玩家个人 kit：对局中玩家自己调整过背包（与发下来的 kit 不同）则按 (玩家, 模式) 自动记录，
 * 下次进入该模式对局优先使用。playerkits.yml 持久化，写穿式保存（记录/清除即落盘）。
 */
public class PlayerKitManager {

    private final PractiseAuraPlugin plugin;
    private final Map<UUID, Map<String, KitManager.Kit>> kits = new HashMap<>();
    private File file;

    public PlayerKitManager(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        kits.clear();
        file = new File(plugin.getDataFolder(), "playerkits.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var root = cfg.getConfigurationSection("players");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            var modes = root.getConfigurationSection(id);
            if (modes == null) continue;
            Map<String, KitManager.Kit> byMode = new HashMap<>();
            for (String modeId : modes.getKeys(false)) {
                var sec = modes.getConfigurationSection(modeId);
                if (sec != null) byMode.put(modeId.toLowerCase(), KitManager.fromSection(sec));
            }
            if (!byMode.isEmpty()) {
                try {
                    kits.put(UUID.fromString(id), byMode);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** 该玩家在该模式的个人 kit（没有返回 null，则按模式 kit/默认发放） */
    public KitManager.Kit get(UUID playerId, String modeId) {
        Map<String, KitManager.Kit> byMode = kits.get(playerId);
        return byMode == null ? null : byMode.get(modeId.toLowerCase());
    }

    public void record(UUID playerId, String modeId, KitManager.Kit kit) {
        kits.computeIfAbsent(playerId, k -> new HashMap<>()).put(modeId.toLowerCase(), kit);
        save();
    }

    /** 清除个人 kit，返回是否确有可删的 */
    public boolean clear(UUID playerId, String modeId) {
        Map<String, KitManager.Kit> byMode = kits.get(playerId);
        if (byMode == null || byMode.remove(modeId.toLowerCase()) == null) return false;
        if (byMode.isEmpty()) kits.remove(playerId);
        save();
        return true;
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, KitManager.Kit>> pe : kits.entrySet()) {
            for (Map.Entry<String, KitManager.Kit> me : pe.getValue().entrySet()) {
                KitManager.toSection(me.getValue(),
                        cfg.createSection("players." + pe.getKey() + "." + me.getKey()));
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (Exception ex) {
            plugin.getLogger().severe("保存 playerkits.yml 失败: " + ex.getMessage());
        }
    }
}
