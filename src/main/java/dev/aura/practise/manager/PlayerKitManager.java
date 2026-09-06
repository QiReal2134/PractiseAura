package dev.aura.practise.manager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 玩家个人 kit（变化量）：对局中玩家自己调整过背包则按 (玩家, 模式) 记录**变化量**——
 * 存储槽位里 null = 跟随原 kit、AIR = 该槽位被清空、item = 用玩家调整后的版本。
 * 发放时先发原 kit 再叠加变化量，管理员更新 kit 后未调整的槽位自动同步。
 * playerkits.yml 持久化（AIR 以 clear 标记显式保存，防止空槽位序列化丢失）；
 * 记录/清除只标脏，由定时任务（30 秒）和关服钩子落盘——对局内的死亡/退出路径零磁盘 I/O。
 */
public class PlayerKitManager {

    private final PractiseAuraPlugin plugin;
    private final Map<UUID, Map<String, KitManager.Kit>> kits = new HashMap<>();
    private File file;
    private boolean dirty = false;

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
                if (sec != null) byMode.put(modeId.toLowerCase(), deltaFromSection(sec));
            }
            if (!byMode.isEmpty()) {
                try {
                    kits.put(UUID.fromString(id), byMode);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** 该玩家在该模式的个人变化量深拷贝（没有则返回 null，按模式 kit/默认发放） */
    public KitManager.Kit get(UUID playerId, String modeId) {
        Map<String, KitManager.Kit> byMode = kits.get(playerId);
        KitManager.Kit kit = byMode == null ? null : byMode.get(modeId.toLowerCase());
        return kit == null ? null : KitManager.copy(kit);
    }

    public void record(UUID playerId, String modeId, KitManager.Kit delta) {
        kits.computeIfAbsent(playerId, k -> new HashMap<>()).put(modeId.toLowerCase(), KitManager.copy(delta));
        dirty = true;
    }

    /** 清除个人变化量，返回是否确有可删的 */
    public boolean clear(UUID playerId, String modeId) {
        Map<String, KitManager.Kit> byMode = kits.get(playerId);
        if (byMode == null || byMode.remove(modeId.toLowerCase()) == null) return false;
        if (byMode.isEmpty()) kits.remove(playerId);
        dirty = true;
        return true;
    }

    /** 已记录个人 kit 的玩家 UUID（/pa kit forget 按名查找用，名字解析走本地缓存不发网络请求） */
    public java.util.Set<UUID> storedPlayers() {
        return java.util.Set.copyOf(kits.keySet());
    }

    /** 有未落盘的改动才写文件（30 秒定时任务 + onDisable 兜底） */
    public void flushIfDirty() {
        if (!dirty) return;
        save();
        dirty = false;
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, KitManager.Kit>> pe : kits.entrySet()) {
            for (Map.Entry<String, KitManager.Kit> me : pe.getValue().entrySet()) {
                deltaToSection(me.getValue(),
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

    // ------------------------------------------------------------------
    // 变化量序列化：null 不写（跟随原 kit）；AIR 写 clear 标记；物品原样写
    // ------------------------------------------------------------------

    private static void deltaToSection(KitManager.Kit kit, ConfigurationSection sec) {
        StringBuilder cleared = null;
        List<ItemStack> storage = kit.storage();
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.get(i);
            if (stack == null) continue;
            if (stack.getType().isAir()) {
                cleared = cleared == null ? new StringBuilder() : cleared.append(',');
                cleared.append(i);
            } else {
                sec.getConfigurationSection("items").set(String.valueOf(i), stack);
            }
        }
        if (cleared != null) sec.set("cleared-storage", cleared.toString());
        writeArmor(sec, "helmet", kit.helmet());
        writeArmor(sec, "chestplate", kit.chestplate());
        writeArmor(sec, "leggings", kit.leggings());
        writeArmor(sec, "boots", kit.boots());
    }

    private static void writeArmor(ConfigurationSection sec, String key, ItemStack stack) {
        if (stack == null) return;
        if (stack.getType().isAir()) sec.set(key + ".clear", true);
        else sec.set(key, stack);
    }

    private static KitManager.Kit deltaFromSection(ConfigurationSection sec) {
        List<ItemStack> storage = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) storage.add(null);
        String cleared = sec.getString("cleared-storage", "");
        for (String slot : cleared.split(",")) {
            try {
                int idx = Integer.parseInt(slot.trim());
                if (idx >= 0 && idx < 36) storage.set(idx, new ItemStack(Material.AIR));
            } catch (NumberFormatException ignored) {
            }
        }
        var items = sec.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    int idx = Integer.parseInt(key);
                    ItemStack stack = items.getItemStack(key);
                    if (idx >= 0 && idx < 36 && stack != null) storage.set(idx, stack);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new KitManager.Kit(storage,
                readArmor(sec, "helmet"), readArmor(sec, "chestplate"),
                readArmor(sec, "leggings"), readArmor(sec, "boots"));
    }

    private static ItemStack readArmor(ConfigurationSection sec, String key) {
        if (sec.getBoolean(key + ".clear")) return new ItemStack(Material.AIR);
        return sec.getItemStack(key);
    }
}
