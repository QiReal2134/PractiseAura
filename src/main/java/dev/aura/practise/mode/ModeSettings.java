package dev.aura.practise.mode;

import dev.aura.practise.PractiseAuraPlugin;

/**
 * 模式级开关（每模式独立，持久化在 config.yml 的 modes.<id> 下）。
 * 各模式在构造时给出默认值，refresh() 用配置覆盖。
 */
public class ModeSettings {

    private boolean needsBeds = true;         // 是否有床（床=重生机制+可拆床目标）
    private boolean needsGuard = true;        // 是否需要围床结构（/pa guard 记录并每局重放）
    private boolean damageEnabled = true;     // 玩家间是否有伤害（false=拳击式：只有击退，如 boxing）
    private boolean pvp = true;               // 是否允许攻击其他玩家
    private boolean allowBreakMap = false;    // 是否允许破坏地图方块（挖掘区之外）
    private boolean allowBreakPlaced = true;  // 是否允许破坏玩家放置的方块
    private boolean allowPlace = true;        // 是否允许放置方块
    private boolean voidKill = true;          // 掉到虚空线以下是否处死

    public ModeSettings setNeedsBeds(boolean v) { needsBeds = v; return this; }
    public ModeSettings setNeedsGuard(boolean v) { needsGuard = v; return this; }
    public ModeSettings setDamageEnabled(boolean v) { damageEnabled = v; return this; }
    public ModeSettings setPvp(boolean v) { pvp = v; return this; }
    public ModeSettings setAllowBreakMap(boolean v) { allowBreakMap = v; return this; }
    public ModeSettings setAllowBreakPlaced(boolean v) { allowBreakPlaced = v; return this; }
    public ModeSettings setAllowPlace(boolean v) { allowPlace = v; return this; }
    public ModeSettings setVoidKill(boolean v) { voidKill = v; return this; }

    public boolean isNeedsBeds() { return needsBeds; }
    public boolean isNeedsGuard() { return needsGuard; }
    public boolean isDamageEnabled() { return damageEnabled; }
    public boolean isPvp() { return pvp; }
    public boolean isAllowBreakMap() { return allowBreakMap; }
    public boolean isAllowBreakPlaced() { return allowBreakPlaced; }
    public boolean isAllowPlace() { return allowPlace; }
    public boolean isVoidKill() { return voidKill; }

    /** 用 config.yml 的 modes.<id>.<flag> 覆盖默认值 */
    public void refresh(PractiseAuraPlugin plugin, String modeId) {
        String base = "modes." + modeId.toLowerCase() + ".";
        needsBeds = plugin.getConfig().getBoolean(base + "needs-beds", needsBeds);
        needsGuard = plugin.getConfig().getBoolean(base + "needs-guard", needsGuard);
        damageEnabled = plugin.getConfig().getBoolean(base + "damage", damageEnabled);
        pvp = plugin.getConfig().getBoolean(base + "pvp", pvp);
        allowBreakMap = plugin.getConfig().getBoolean(base + "allow-break-map", allowBreakMap);
        allowBreakPlaced = plugin.getConfig().getBoolean(base + "allow-break-placed", allowBreakPlaced);
        allowPlace = plugin.getConfig().getBoolean(base + "allow-place", allowPlace);
        voidKill = plugin.getConfig().getBoolean(base + "void-kill", voidKill);
    }
}
