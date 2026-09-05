package dev.aura.practise.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.Arena;
import dev.aura.practise.mode.ModeHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class ArenaManager {

    private final PractiseAuraPlugin plugin;
    private final Map<String, Arena> arenas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private File file;

    public ArenaManager(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("arenas");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;
            Arena arena = Arena.load(name, sec);
            if (arena != null) {
                arenas.put(name.toLowerCase(), arena);
                // 旧版竞技场级 kit 一次性迁移到模式级
                if (arena.hasCustomKit()) {
                    plugin.kits().migrate(arena.getType().id(), new dev.aura.practise.manager.KitManager.Kit(
                            arena.getKitItems(), arena.getKitHelmet(), arena.getKitChestplate(),
                            arena.getKitLeggings(), arena.getKitBoots()));
                    arena.clearKit();
                }
                // 世界缺失会让出生点读不到（常见于世界文件夹被删/未加载），启动时明确提示
                String worldName = arena.worldName();
                if (worldName != null && org.bukkit.Bukkit.getWorld(worldName) == null) {
                    plugin.getLogger().warning("竞技场 " + name + " 所在的世界 " + worldName
                            + " 未加载或不存在，该场暂时不可用（重建世界后重启即可恢复）");
                }
            }
        }
        plugin.getLogger().info("已加载 " + arenas.size() + " 个竞技场");
    }

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            arena.save(cfg.createSection("arenas." + arena.getName()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存 arenas.yml 失败: " + ex.getMessage());
        }
    }

    /** 创建竞技场，重名返回 null */
    public Arena create(String name, ModeHandler mode) {
        if (get(name) != null) return null;
        Arena arena = new Arena(name, mode);
        arenas.put(name.toLowerCase(), arena);
        saveAll();
        return arena;
    }

    public boolean delete(String name) {
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed == null) return false;
        saveAll();
        return true;
    }

    public Arena get(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    /** 找一个有空闲完整点位的竞技场 */
    public Arena findFree(ModeHandler mode) {
        for (Arena arena : arenas.values()) {
            if (arena.getType() == mode && arena.freePosition() >= 0) return arena;
        }
        return null;
    }

    public List<String> names() {
        return new ArrayList<>(arenas.keySet());
    }
}
