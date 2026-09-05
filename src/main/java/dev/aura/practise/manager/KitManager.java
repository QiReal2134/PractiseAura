package dev.aura.practise.manager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.configuration.ConfigurationSection;
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
            kits.put(modeId.toLowerCase(), fromSection(sec));
        }
    }

    /** 从配置节读 kit（36 格背包 + 四件盔甲；缺的槽位为 null）——PlayerKitManager 复用 */
    static Kit fromSection(ConfigurationSection sec) {
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
        return new Kit(storage,
                sec.getItemStack("helmet"),
                sec.getItemStack("chestplate"),
                sec.getItemStack("leggings"),
                sec.getItemStack("boots"));
    }

    /** 把 kit 写进配置节（空槽位不写）——PlayerKitManager 复用 */
    static void toSection(Kit kit, ConfigurationSection sec) {
        ConfigurationSection items = sec.createSection("items");
        List<ItemStack> storage = kit.storage();
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.get(i);
            if (stack != null && !stack.getType().isAir()) items.set(String.valueOf(i), stack);
        }
        if (kit.helmet() != null) sec.set("helmet", kit.helmet());
        if (kit.chestplate() != null) sec.set("chestplate", kit.chestplate());
        if (kit.leggings() != null) sec.set("leggings", kit.leggings());
        if (kit.boots() != null) sec.set("boots", kit.boots());
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Kit> entry : kits.entrySet()) {
            toSection(entry.getValue(), cfg.createSection("kits." + entry.getKey()));
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
        kits.put(modeId.toLowerCase(), copy(kit));
        save();
    }

    /** 深拷贝：缓存里的 ItemStack 不能共享调用方的引用（背包里的物品被消耗会污染 kit） */
    private static Kit copy(Kit kit) {
        List<ItemStack> storage = new ArrayList<>(kit.storage().size());
        for (ItemStack stack : kit.storage()) {
            storage.add(stack == null ? null : stack.clone());
        }
        return new Kit(storage,
                kit.helmet() == null ? null : kit.helmet().clone(),
                kit.chestplate() == null ? null : kit.chestplate().clone(),
                kit.leggings() == null ? null : kit.leggings().clone(),
                kit.boots() == null ? null : kit.boots().clone());
    }

    public void clear(String modeId) {
        if (kits.remove(modeId.toLowerCase()) != null) save();
    }

    /** 旧版竞技场级 kit 一次性迁移到模式级（同模式多场地共享） */
    public void migrate(String modeId, Kit kit) {
        if (!hasKit(modeId)) {
            kits.put(modeId.toLowerCase(), copy(kit));
            save();
        }
    }
}
