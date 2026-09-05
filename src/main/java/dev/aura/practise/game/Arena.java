package dev.aura.practise.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.LocUtil;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * 一张地图：模式 + 多组对局点位（同图多场并发）+ 可选可建设区域 + kit。
 * 点位从 1 开始编号；命令默认编辑第 1 组。
 */
public class Arena {

    private final String name;
    private final ModeHandler type;
    private final List<ArenaPosition> positions = new ArrayList<>();
    private final Set<Integer> usedPositions = new HashSet<>();
    /** 围床结构：相对红床头的偏移 + 方块数据（/pa guard ready 记录，游戏开始时同步放到两张床周围） */
    private List<GuardEntry> guardEntries = new ArrayList<>();
    private Location buildPos1;
    private Location buildPos2;
    /** 自定义 kit：36 格背包（null = 空格）+ 四件盔甲；null 表示未配置用默认 */
    private List<ItemStack> kitItems;
    private ItemStack kitHelmet;
    private ItemStack kitChestplate;
    private ItemStack kitLeggings;
    private ItemStack kitBoots;

    public Arena(String name, ModeHandler type) {
        this.name = name;
        this.type = type;
        positions.add(new ArenaPosition());
    }

    public String getName() {
        return name;
    }

    public ModeHandler getType() {
        return type;
    }

    // ------------------------------------------------------------------
    // 点位
    // ------------------------------------------------------------------

    /** 取点位（1-based，越界自动扩容） */
    public ArenaPosition position(int oneBased) {
        ensurePosition(oneBased);
        return positions.get(oneBased - 1);
    }

    public void ensurePosition(int oneBased) {
        while (positions.size() < oneBased) {
            positions.add(new ArenaPosition());
        }
    }

    public int positionsCount() {
        return positions.size();
    }

    public int usedCount() {
        return usedPositions.size();
    }

    /** 第一个"配置完整且空闲"的点位（0-based），没有返回 -1 */
    public int freePosition() {
        for (int i = 0; i < positions.size(); i++) {
            if (!usedPositions.contains(i) && positions.get(i).isComplete(type.needsBeds())) {
                return i;
            }
        }
        return -1;
    }

    /** 配置完整且空闲的点位数量 */
    public int freePositionCount() {
        int n = 0;
        for (int i = 0; i < positions.size(); i++) {
            if (!usedPositions.contains(i) && positions.get(i).isComplete(type.needsBeds())) n++;
        }
        return n;
    }

    public void reservePosition(int index) {
        usedPositions.add(index);
    }

    public void releasePosition(int index) {
        usedPositions.remove(index);
    }

    /** 是否至少有一组完整点位，可以被游戏使用 */
    public boolean isReady() {
        return freePosition() >= 0 || !usedPositions.isEmpty();
    }

    /** 第一个已配置的出生点（自动传送用） */
    public Location firstSpawn() {
        for (ArenaPosition pos : positions) {
            Location s = pos.spawn(Team.RED);
            if (s != null) return s;
            s = pos.spawn(Team.BLUE);
            if (s != null) return s;
        }
        return null;
    }

    /** 地图所在世界名（未配置返回 null） */
    public String worldName() {
        Location s = firstSpawn();
        return s == null || s.getWorld() == null ? null : s.getWorld().getName();
    }

    public String missingHint() {
        String hint = positions.get(0).missingHint(type.needsBeds());
        return hint.isEmpty() ? "所有点位都在使用中" : "缺少 " + hint + "（第 1 组）";
    }

    /** 观战点：第 1 组两出生点中点上方 */
    public Location spectatorPoint() {
        return spectatorPoint(0);
    }

    public Location spectatorPoint(int index) {
        if (index < 0 || index >= positions.size()) return null;
        ArenaPosition pos = positions.get(index);
        Location a = pos.spawn(Team.RED);
        Location b = pos.spawn(Team.BLUE);
        if (a == null || b == null || !a.getWorld().equals(b.getWorld())) return a;
        return new Location(a.getWorld(),
                (a.getX() + b.getX()) / 2,
                Math.max(a.getY(), b.getY()) + 10,
                (a.getZ() + b.getZ()) / 2);
    }

    // ------------------------------------------------------------------
    // 围床结构（/pa guard <场名> ready 在红床周围放好方块后记录，自动同步蓝床）
    // ------------------------------------------------------------------

    /** 相对床头的偏移与方块数据快照 */
    public record GuardEntry(int dx, int dy, int dz, String data) {
    }

    public void setGuards(List<GuardEntry> entries) {
        this.guardEntries = new ArrayList<>(entries);
    }

    public void clearGuards() {
        guardEntries = new ArrayList<>();
    }

    public List<GuardEntry> getGuardEntries() {
        return guardEntries;
    }

    /**
     * 把相对红床记录的围床偏移变换到蓝床坐标系（默认第 1 组点位）。
     * 两床朝向相反时自动镜像（红床靠后侧的墙 → 蓝床靠后侧的墙），
     * 朝向相同或无法确定时原样返回。
     */
    public GuardEntry entryForBlue(GuardEntry e) {
        return entryForBlue(positions.get(0), e);
    }

    /** 同上，但按指定点位的两床朝向镜像——对局实际用哪组点位就传哪组 */
    public GuardEntry entryForBlue(ArenaPosition pos, GuardEntry e) {
        BlockFace f1 = pos.bedFacing(Team.RED);
        BlockFace f2 = pos.bedFacing(Team.BLUE);
        if (f1 == null || f2 == null || f2 != f1.getOppositeFace()) return e;
        int sx = -f1.getModZ(), sz = f1.getModX(); // 侧轴 = 朝向旋转 90°
        int forward = e.dx() * f1.getModX() + e.dz() * f1.getModZ();
        int side = e.dx() * sx + e.dz() * sz;
        return new GuardEntry(
                f2.getModX() * forward + sx * side,
                e.dy(),
                f2.getModZ() * forward + sz * side,
                e.data());
    }

    // ------------------------------------------------------------------
    // 遗留 kit 字段：仅用于加载旧版 arenas.yml 时一次性迁移到模式级 kit
    // ------------------------------------------------------------------

    public boolean hasCustomKit() {
        return kitItems != null;
    }

    public void clearKit() {
        kitItems = null;
        kitHelmet = null;
        kitChestplate = null;
        kitLeggings = null;
        kitBoots = null;
    }

    public List<ItemStack> getKitItems() {
        return kitItems;
    }

    public ItemStack getKitHelmet() {
        return kitHelmet;
    }

    public ItemStack getKitChestplate() {
        return kitChestplate;
    }

    public ItemStack getKitLeggings() {
        return kitLeggings;
    }

    public ItemStack getKitBoots() {
        return kitBoots;
    }

    // ------------------------------------------------------------------
    // 可建设区域：站两个对角点用 /pa setbuild <场名> pos1|pos2 配置。
    // 未配置时游戏内只有玩家自己放的方块可以拆/炸，地图方块全保护。
    // ------------------------------------------------------------------

    public void setBuildPos(int index, Location loc) {
        if (index == 1) buildPos1 = loc.getBlock().getLocation();
        else buildPos2 = loc.getBlock().getLocation();
    }

    public boolean hasBuildRegion() {
        return buildPos1 != null && buildPos2 != null
                && buildPos1.getWorld() != null
                && buildPos1.getWorld().equals(buildPos2.getWorld());
    }

    public boolean inBuildRegion(Location loc) {
        if (!hasBuildRegion()) return false;
        if (loc.getWorld() == null || !loc.getWorld().equals(buildPos1.getWorld())) return false;
        return loc.getBlockX() >= Math.min(buildPos1.getBlockX(), buildPos2.getBlockX())
                && loc.getBlockX() <= Math.max(buildPos1.getBlockX(), buildPos2.getBlockX())
                && loc.getBlockY() >= Math.min(buildPos1.getBlockY(), buildPos2.getBlockY())
                && loc.getBlockY() <= Math.max(buildPos1.getBlockY(), buildPos2.getBlockY())
                && loc.getBlockZ() >= Math.min(buildPos1.getBlockZ(), buildPos2.getBlockZ())
                && loc.getBlockZ() <= Math.max(buildPos1.getBlockZ(), buildPos2.getBlockZ());
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    public void save(ConfigurationSection sec) {
        sec.set("type", type.id());
        LocUtil.write(sec, "build-pos1", buildPos1);
        LocUtil.write(sec, "build-pos2", buildPos2);
        if (!guardEntries.isEmpty()) {
            ConfigurationSection guards = sec.createSection("guards");
            for (int i = 0; i < guardEntries.size(); i++) {
                GuardEntry entry = guardEntries.get(i);
                guards.set(i + ".rel", entry.dx() + "," + entry.dy() + "," + entry.dz());
                guards.set(i + ".data", entry.data());
            }
        }
        if (kitItems != null) {
            ConfigurationSection kit = sec.createSection("kit");
            ConfigurationSection items = kit.createSection("items");
            for (int i = 0; i < kitItems.size(); i++) {
                ItemStack stack = kitItems.get(i);
                if (stack != null && !stack.getType().isAir()) items.set(String.valueOf(i), stack);
            }
            if (kitHelmet != null) kit.set("helmet", kitHelmet);
            if (kitChestplate != null) kit.set("chestplate", kitChestplate);
            if (kitLeggings != null) kit.set("leggings", kitLeggings);
            if (kitBoots != null) kit.set("boots", kitBoots);
        }
        ConfigurationSection positionsSec = sec.createSection("positions");
        for (int i = 0; i < positions.size(); i++) {
            positions.get(i).save(positionsSec.createSection(String.valueOf(i + 1)));
        }
    }

    public static Arena load(String name, ConfigurationSection sec) {
        ModeHandler type = ModeRegistry.parse(sec.getString("type", ""));
        if (type == null) return null;
        Arena arena = new Arena(name, type);
        arena.buildPos1 = LocUtil.read(sec, "build-pos1");
        arena.buildPos2 = LocUtil.read(sec, "build-pos2");
        ConfigurationSection guards = sec.getConfigurationSection("guards");
        if (guards != null) {
            List<GuardEntry> entries = new ArrayList<>();
            for (String key : guards.getKeys(false)) {
                String rel = guards.getString(key + ".rel", "");
                String data = guards.getString(key + ".data", "");
                String[] parts = rel.split(",");
                if (parts.length != 3) continue;
                try {
                    entries.add(new GuardEntry(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            data));
                } catch (NumberFormatException ignored) {
                }
            }
            arena.guardEntries = entries;
        }
        ConfigurationSection kit = sec.getConfigurationSection("kit");
        if (kit != null) {
            List<ItemStack> storage = new ArrayList<>(36);
            for (int i = 0; i < 36; i++) storage.add(null);
            ConfigurationSection items = kit.getConfigurationSection("items");
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
            arena.kitItems = storage;
            arena.kitHelmet = kit.getItemStack("helmet");
            arena.kitChestplate = kit.getItemStack("chestplate");
            arena.kitLeggings = kit.getItemStack("leggings");
            arena.kitBoots = kit.getItemStack("boots");
        }
        ConfigurationSection positionsSec = sec.getConfigurationSection("positions");
        if (positionsSec != null) {
            arena.positions.clear();
            for (String key : positionsSec.getKeys(false)) {
                ConfigurationSection posSec = positionsSec.getConfigurationSection(key);
                if (posSec == null) continue;
                try {
                    int index = Integer.parseInt(key);
                    arena.ensurePosition(index);
                    arena.positions.set(index - 1, ArenaPosition.load(posSec));
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            // 旧版扁平结构迁移到第 1 组点位
            arena.positions.set(0, ArenaPosition.load(sec));
        }
        return arena;
    }
}
