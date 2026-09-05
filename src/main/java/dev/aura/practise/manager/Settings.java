package dev.aura.practise.manager;

import org.bukkit.Material;

import dev.aura.practise.PractiseAuraPlugin;

/**
 * 全局配置的缓存层：启动与每次修改后一次性读入字段，
 * 运行期（含每 tick 热路径）只做字段读取，不做 YAML 查找。
 * 修改入口：/pa setting（写入后调用 refresh()）或重启。
 */
public class Settings {

    private final PractiseAuraPlugin plugin;

    private int minPlayers;
    private int teamSize;
    private int rounds;
    private int roundCountdownSeconds;
    private int duelInviteSeconds;
    private int duelCooldownSeconds;
    private int killCreditWindowSeconds;
    private double totemDurationSeconds;
    private double totemScale;
    private int countdownSeconds;
    private int kitBlocks;
    private int guardScanRadius;
    private int respawnSeconds;
    private int voidBelowSpawn;
    private int placeLimitBelowSpawn;
    private int spawnProtectionSeconds;
    private double fireballPowerX;
    private double fireballPowerY;
    private double fireballDamage;
    private double fireballRadius;
    private double fireballCooldownSeconds;
    private boolean showAdminCommands;
    private Material lobbyItem;
    private Material rejoinItem;

    public Settings(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** 从 config.yml 重新读入所有值（/pa setting 修改后调用） */
    public void refresh() {
        minPlayers = Math.max(2, plugin.getConfig().getInt("settings.min-players", 2));
        teamSize = clamp(plugin.getConfig().getInt("settings.team-size", 1), 1, 4);
        rounds = clamp(plugin.getConfig().getInt("settings.rounds", 1), 1, 5);
        roundCountdownSeconds = clamp(plugin.getConfig().getInt("settings.round-countdown-seconds", 3), 1, 15);
        duelInviteSeconds = clamp(plugin.getConfig().getInt("settings.duel-invite-seconds", 30), 5, 120);
        duelCooldownSeconds = clamp(plugin.getConfig().getInt("settings.duel-cooldown-seconds", 10), 0, 60);
        killCreditWindowSeconds = clamp(plugin.getConfig().getInt("settings.kill-credit-window-seconds", 8), 0, 30);
        totemDurationSeconds = clamp(plugin.getConfig().getDouble("settings.totem-duration-seconds", 2.5), 0.5, 10.0);
        totemScale = clamp(plugin.getConfig().getDouble("settings.totem-scale", 1.8), 0.5, 4.0);
        countdownSeconds = clamp(plugin.getConfig().getInt("settings.countdown-seconds", 5), 3, 120);
        kitBlocks = clamp(plugin.getConfig().getInt("settings.kit-blocks", 24), 0, 128);
        guardScanRadius = clamp(plugin.getConfig().getInt("settings.guard-scan-radius", 4), 1, 8);
        respawnSeconds = clamp(plugin.getConfig().getInt("settings.respawn-seconds", 3), 0, 30);
        voidBelowSpawn = clamp(plugin.getConfig().getInt("settings.void-below-spawn", 12), 0, 64);
        placeLimitBelowSpawn = clamp(plugin.getConfig().getInt("settings.place-limit-below-spawn", 12), 0, 64);
        spawnProtectionSeconds = clamp(plugin.getConfig().getInt("settings.spawn-protection-seconds", 2), 0, 10);
        fireballPowerX = clamp(plugin.getConfig().getDouble("settings.fireball-power-x", 1.6), 0, 5);
        fireballPowerY = clamp(plugin.getConfig().getDouble("settings.fireball-power-y", 0.8), 0, 5);
        fireballDamage = clamp(plugin.getConfig().getDouble("settings.fireball-damage", 4.0), 0, 20);
        fireballRadius = clamp(plugin.getConfig().getDouble("settings.fireball-radius", 2.5), 1, 6);
        fireballCooldownSeconds = clamp(plugin.getConfig().getDouble("settings.fireball-cooldown-seconds", 1.5), 0, 10);
        showAdminCommands = plugin.getConfig().getBoolean("settings.show-admin-commands", false);
        lobbyItem = material("settings.lobby-item", "IRON_SWORD");
        rejoinItem = material("settings.rejoin-item", "PAPER");
    }

    public int minPlayers() {
        return minPlayers;
    }

    /** 每队人数（1=1v1，2=2v2，最多 4v4） */
    public int teamSize() {
        return teamSize;
    }

    /** 默认回合数（1 = 单局；排队对局使用；duel 可单独指定更多局） */
    public int rounds() {
        return rounds;
    }

    /** 多局制局间倒计时秒数 */
    public int roundCountdownSeconds() {
        return roundCountdownSeconds;
    }

    /** 约战邀请有效期（秒） */
    public int duelInviteSeconds() {
        return duelInviteSeconds;
    }

    /** 约战发送冷却（秒），0 = 无冷却 */
    public int duelCooldownSeconds() {
        return duelCooldownSeconds;
    }

    /** 击杀归属窗口（秒）：被记录攻击者在此时间内死亡会计入击杀，0 = 不归属 */
    public int killCreditWindowSeconds() {
        return killCreditWindowSeconds;
    }

    /** 图腾特效时长（秒） */
    public double totemDurationSeconds() {
        return totemDurationSeconds;
    }

    /** 图腾图标缩放 */
    public double totemScale() {
        return totemScale;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int kitBlocks() {
        return kitBlocks;
    }

    public int guardScanRadius() {
        return guardScanRadius;
    }

    public int respawnSeconds() {
        return respawnSeconds;
    }

    public int voidBelowSpawn() {
        return voidBelowSpawn;
    }

    public int placeLimitBelowSpawn() {
        return placeLimitBelowSpawn;
    }

    public int spawnProtectionSeconds() {
        return spawnProtectionSeconds;
    }

    public double fireballPowerX() {
        return fireballPowerX;
    }

    public double fireballPowerY() {
        return fireballPowerY;
    }

    public double fireballDamage() {
        return fireballDamage;
    }

    public double fireballRadius() {
        return fireballRadius;
    }

    public double fireballCooldownSeconds() {
        return fireballCooldownSeconds;
    }

    public boolean showAdminCommands() {
        return showAdminCommands;
    }

    public Material lobbyItem() {
        return lobbyItem;
    }

    public Material rejoinItem() {
        return rejoinItem;
    }

    private Material material(String path, String def) {
        String name = plugin.getConfig().getString(path, def);
        Material m = Material.matchMaterial(name == null ? def : name, false);
        return m == null ? Material.matchMaterial(def) : m;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
