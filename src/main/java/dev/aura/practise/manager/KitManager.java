package dev.aura.practise.manager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** 按模式存储的 kit（/pa kit <模式> 设置，该模式所有竞技场共用），kits.yml 持久化 */
public class KitManager {

    public record Kit(List<ItemStack> storage, ItemStack helmet, ItemStack chestplate,
                      ItemStack leggings, ItemStack boots) {
    }

    private final PractiseAuraPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();
    private File file;

    public KitManager(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        kits.clear();
        file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var root = cfg.getConfigurationSection("kits");
        if (root == null) return;
        for (String modeId : root.getKeys(false)) {
            var sec = root.getConfigurationSection(modeId);
            if (sec == null) continue;
            List<ItemStack> storage = new ArrayList<>(36);
            for (int i = 0; i < 36; i++) storage.add(null);
            var items = sec.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack stack = items.getItemStack(key);
                        if (slot >= 0 && slot < 36 && stack != null) storage.set(slot, stack);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            kits.put(modeId.toLowerCase(), new Kit(
                    storage,
                    sec.getItemStack("helmet"),
                    sec.getItemStack("chestplate"),
                    sec.getItemStack("leggings"),
                    sec.getItemStack("boots")));
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Kit> entry : kits.entrySet()) {
            var sec = cfg.createSection("kits." + entry.getKey());
            var items = sec.createSection("items");
            List<ItemStack> storage = entry.getValue().storage();
            for (int i = 0; i < storage.size(); i++) {
                ItemStack stack = storage.get(i);
                if (stack != null && !stack.getType().isAir()) items.set(String.valueOf(i), stack);
            }
            Kit kit = entry.getValue();
            if (kit.helmet() != null) sec.set("helmet", kit.helmet());
            if (kit.chestplate() != null) sec.set("chestplate", kit.chestplate());
            if (kit.leggings() != null) sec.set("leggings", kit.leggings());
            if (kit.boots() != null) sec.set("boots", kit.boots());
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (Exception ex) {
            plugin.getLogger().severe("保存 kits.yml 失败: " + ex.getMessage());
        }
    }

    public boolean hasKit(String modeId) {
        return kits.containsKey(modeId.toLowerCase());
    }

    public Kit get(String modeId) {
        return kits.get(modeId.toLowerCase());
    }

    public void set(String modeId, Kit kit) {
        kits.put(modeId.toLowerCase(), kit);
        save();
    }

    public void clear(String modeId) {
        if (kits.remove(modeId.toLowerCase()) != null) save();
    }

    /** 旧版竞技场级 kit 一次性迁移到模式级（同模式多场地共享） */
    public void migrate(String modeId, Kit kit) {
        if (!hasKit(modeId)) {
            kits.put(modeId.toLowerCase(), kit);
            save();
        }
    }
}
