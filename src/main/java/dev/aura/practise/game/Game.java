package dev.aura.practise.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeSettings;
import dev.aura.practise.manager.KitManager;
import dev.aura.practise.util.LocUtil;
import dev.aura.practise.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * 一场进行中的游戏（当前固定 1v1，红蓝两队）。
 * 模式行为全部委托给 {@link ModeHandler}；地图数据来自 Arena 的某一组点位。
 * 职责：排队与倒计时、开局发装、床（目标方块）、围床结构、虚空处死、
 * 重生等待（幽灵状态）、结算与回滚。
 */
public class Game {

    private final PractiseAuraPlugin plugin;
    private final Arena arena;
    private final ModeHandler mode;
    /** 占用的点位（0-based，指向 arena 的第 position+1 组） */
    private final int position;

    private final Map<UUID, Team> players = new LinkedHashMap<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<Team, Boolean> bedAlive = new EnumMap<>(Team.class);
    private final BlockTracker tracker = new BlockTracker();
    private final Set<Location> guardBlocks = new HashSet<>();
    private final Set<UUID> ghosts = new HashSet<>();
    private final Map<UUID, BukkitTask> ghostTasks = new HashMap<>();
    private final Set<UUID> spectators = new HashSet<>();

    private final Map<UUID, Long> protectionUntil = new HashMap<>();
    /** 最近攻击者记录：虚空/环境死亡时把击杀归属给 8 秒内打过你的人 */
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();

    /** 监听器在成功的玩家间伤害后调用（含 0 伤害的拳击） */
    public void setLastAttacker(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    /** 击杀归属窗口内打过 victim 的人（没有则 null） */
    private Player recentAttacker(Player victim) {
        UUID attackerId = lastAttacker.get(victim.getUniqueId());
        Long time = lastAttackTime.get(victim.getUniqueId());
        if (attackerId == null || time == null) return null;
        long window = plugin.settings().killCreditWindowSeconds() * 1000L;
        if (window <= 0 || System.currentTimeMillis() - time > window) return null;
        Player attacker = Bukkit.getPlayer(attackerId);
        return attacker != null && isParticipant(attackerId) ? attacker : null;
    }

    private GameState state = GameState.WAITING;
    private int teamSize;
    private int countdownSeconds;
    private BukkitTask countdownTask;
    private BukkitTask tickTask;
    private org.bukkit.boss.BossBar countdownBar;
    /** true = 首次匹配的排队倒计时；false = 多局制的局间倒计时 */
    private boolean matchCountdown = true;

    // 回合制
    private int roundsTotal = 1;
    private int roundsPlayed = 0;
    private int roundsCurrent = 1;
    private final Map<Team, Integer> roundWins = new EnumMap<>(Team.class);

    public Game(PractiseAuraPlugin plugin, Arena arena, ModeHandler mode, int position) {
        this.plugin = plugin;
        this.arena = arena;
        this.mode = mode;
        this.position = position;
        this.arena.reservePosition(position);
        this.teamSize = Math.max(1, plugin.settings().teamSize());
        for (Team team : Team.values()) {
            bedAlive.put(team, true);
        }
    }

    /** 每队人数（1v1 / 2v2...），总人数 = teamSize * 2；duel 固定 1v1 */
    public void setTeamSize(int size) {
        this.teamSize = Math.max(1, Math.min(4, size));
    }

    /** 本局需要的总人数 */
    public int maxPlayers() {
        return teamSize * 2;
    }

    public PractiseAuraPlugin plugin() {
        return plugin;
    }

    public Arena arena() {
        return arena;
    }

    public ModeHandler mode() {
        return mode;
    }

    /** 本局占用的点位（0-based） */
    public int position() {
        return position;
    }

    public GameState state() {
        return state;
    }

    /** 当前倒计时是否为首次匹配排队倒计时（false = 多局制局间倒计时） */
    public boolean isMatchCountdown() {
        return matchCountdown;
    }

    /** 设置回合数（1 = 单局；2/3 = 先过半胜，必须在开局前调用） */
    public void setRounds(int rounds) {
        this.roundsTotal = Math.max(1, Math.min(5, rounds));
    }

    private ArenaPosition pos() {
        return arena.position(position + 1);
    }

    /** 本局点位中某队的出生点 */
    public Location spawn(Team team) {
        return pos().spawn(team);
    }

    /** 本局点位中某队出生点的 Y 值 */
    public double spawnY(Team team) {
        return pos().spawnY(team);
    }

    // ------------------------------------------------------------------
    // 排队 / 加入
    // ------------------------------------------------------------------

    public boolean addPlayer(Player p) {
        if (state != GameState.WAITING || isFull()) return false;
        Team team = nextTeam();
        players.put(p.getUniqueId(), team);
        alive.add(p.getUniqueId());
        broadcast("game.joined", p.getName(), players.size(), maxPlayers());
        // 排队阶段留在原地（大厅），人齐后再传送；背包清空只保留退出排队染料
        p.setGameMode(GameMode.SURVIVAL);
        clearInventory(p);
        plugin.lobbyMenu().giveQueueItem(p); // 第 9 格：退出排队
        checkCountdown();
        return true;
    }

    /** reasonKey 为 messages.yml 里的消息键（game.left / game.quit） */
    public void leave(Player p, String reasonKey) {
        Team team = players.remove(p.getUniqueId());
        if (team == null) return;
        recordPersonalKit(p); // 掉线/退局也保存调整过的背包（没领过装备时自动跳过）
        alive.remove(p.getUniqueId());
        cancelGhost(p.getUniqueId()); // 幽灵退出：立即还原状态，别等重生任务下一拍（掉线时药水会被写进玩家数据）
        broadcast(reasonKey, p.getName());
        if (state == GameState.STARTING) {
            if (matchCountdown) {
                stopCountdown();
                removeBar();
                state = GameState.WAITING;
                countdownSeconds = 0;
                broadcast("match.cancelled");
                sendBackToLobby(); // 剩下的玩家遣返大厅，别困在空场
            } else {
                // 多局制局间退出：整队没人了才判负整场，还有队友则继续本局（少打多）
                boolean teamEmpty = players.values().stream().noneMatch(t -> t == team);
                transferHealthOnQuit(p, team);
                if (teamEmpty) {
                    stopCountdown();
                    removeBar();
                    state = GameState.RUNNING;
                    end(team.opposite());
                }
            }
        } else if (state == GameState.RUNNING) {
            transferHealthOnQuit(p, team);
            // 无论退出时是否存活（含死亡界面退出），人数变化都可能直接判负
            checkEnd();
        }
    }

    /**
     * 多人模式队友退出：把退出者的剩余血量转移给存活的同队队友，
     * 并把队友血量上限翻倍（20 → 40）以容纳。
     */
    private void transferHealthOnQuit(Player quitter, Team team) {
        if (teamSize <= 1 || quitter.isDead()) return;
        double quitHealth = Math.max(0, quitter.getHealth());
        if (quitHealth < 0.5) return;
        Player mate = null;
        for (Map.Entry<UUID, Team> entry : players.entrySet()) {
            if (entry.getValue() != team || entry.getKey().equals(quitter.getUniqueId())) continue;
            Player candidate = Bukkit.getPlayer(entry.getKey());
            if (candidate != null && candidate.isOnline() && !candidate.isDead()) {
                mate = candidate;
                break;
            }
        }
        if (mate == null) return;
        org.bukkit.attribute.AttributeInstance maxHealth =
                mate.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        double cap = 20.0 * teamSize; // 上限封顶：不随连续退出无限翻倍
        double newMax = Math.min(cap, maxHealth.getBaseValue() * 2);
        maxHealth.setBaseValue(newMax);
        mate.setHealth(Math.min(newMax, mate.getHealth() + quitHealth));
        broadcast("game.health-inherited", mate.getName(), quitter.getName(), String.format("%.0f", newMax));
    }

    /** 排队/倒计时中断：把还在场上的玩家送回大厅并发等待区物品 */
    private void sendBackToLobby() {
        Location lobby = plugin.lobby();
        for (UUID id : new ArrayList<>(players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (lobby != null && !LocUtil.sameBlock(p.getLocation(), lobby)) {
                p.setFallDistance(0f);
                p.teleport(lobby);
            }
            plugin.lobbyMenu().giveLobbyItems(p);
            plugin.updateVisibility(p);
        }
    }

    private Team nextTeam() {
        int red = 0;
        for (Team team : players.values()) {
            if (team == Team.RED) red++;
        }
        return red <= players.size() - red ? Team.RED : Team.BLUE;
    }

    // ------------------------------------------------------------------
    // 匹配 / 倒计时 / 开局
    // ------------------------------------------------------------------

    private void checkCountdown() {
        if (state != GameState.WAITING) return;
        if (!isFull()) return; // 满员（teamSize * 2）才开局
        state = GameState.STARTING;
        countdownSeconds = plugin.settings().countdownSeconds();
        countdownBar = Bukkit.createBossBar(
                Msg.text("match.count-bar").replace('&', '§'),
                org.bukkit.boss.BarColor.YELLOW,
                org.bukkit.boss.BarStyle.SEGMENTED_10);
        countdownBar.setProgress(1.0);
        updateBarViewers();
        broadcast("match.matched");
        // 匹配到才把所有人传送到各自出生点，等待期间禁止移动（监听器处理）
        for (Map.Entry<UUID, Team> entry : players.entrySet()) {
            Player pl = Bukkit.getPlayer(entry.getKey());
            if (pl == null || !pl.isOnline()) continue;
            pl.setGameMode(GameMode.SURVIVAL);
            clearInventory(pl);
            pl.setHealth(20.0);
            pl.setFoodLevel(20);
            pl.setFireTicks(0);
            Location spawn = pos().spawn(entry.getValue());
            if (spawn != null) pl.teleport(spawn);
            pl.setFallDistance(0f);
        }
        broadcast("match.countdown", countdownSeconds);
        // 匹配成功：图腾特效（粒子爆发 + 模式图标从头顶升起）
        for (UUID id : players.keySet()) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) playMatchTotem(pl);
        }
        startCountdownTask();
    }

    /** 倒计时任务（首次匹配与局间共用） */
    private void startCountdownTask() {
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (players.size() < maxPlayers()) {
                    cancel();
                    countdownTask = null;
                    if (matchCountdown) {
                        // 排队的人跑了：回到等待状态
                        state = GameState.WAITING;
                        countdownSeconds = 0;
                        removeBar();
                        broadcast("match.cancelled");
                        sendBackToLobby();
                    } else {
                        // 局间有人跑了但队友还在：少打多，直接开局
                        start();
                    }
                    return;
                }
                countdownSeconds--;
                if (countdownSeconds <= 0) {
                    cancel();
                    countdownTask = null;
                    removeBar();
                    start();
                    return;
                }
                updateBarViewers();
                int total = matchCountdown
                        ? Math.max(1, plugin.settings().countdownSeconds())
                        : Math.max(1, plugin.settings().roundCountdownSeconds());
                countdownBar.setProgress(Math.max(0.0, Math.min(1.0, countdownSeconds / (double) total)));
                if (countdownSeconds <= 5 || countdownSeconds % 5 == 0) {
                    for (Player p : onlinePlayers()) {
                        if (matchCountdown) {
                            Msg.title(p, "match.count-title", "match.count-sub",
                                    countdownSeconds, mode.display());
                        } else {
                            Msg.title(p, "match.count-title", "match.round-title",
                                    countdownSeconds, roundsCurrent);
                        }
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * 匹配成功特效：不死图腾弹出动画的复刻——
     * 图腾粒子爆发 + 模式图标以 GUI 平面样式（和菜单里一样的图标外观）
     * 从玩家胸口位置升起、缓慢旋转、放大后消失，全程约 2.5 秒。
     */
    private void playMatchTotem(Player p) {
        Material icon = mode.icon();
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                p.getLocation().clone().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.2);
        ItemStack stack = new ItemStack(icon);
        final float scale = (float) plugin.settings().totemScale();
        ItemDisplay display = p.getWorld().spawn(
                p.getLocation().clone().add(0, 0.9, 0), ItemDisplay.class, d -> {
                    d.setItemStack(stack);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI); // 平面图标观感
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setShadowRadius(0f);
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(15, 15)); // 全亮度，不受光照影响
                    org.bukkit.util.Transformation t = d.getTransformation();
                    d.setTransformation(new org.bukkit.util.Transformation(
                            t.getTranslation(),
                            t.getLeftRotation(),
                            new org.joml.Vector3f(scale, scale, scale), // 放大（totem-scale）
                            t.getRightRotation()));
                });
        final long durationTicks = Math.max(5L, (long) (plugin.settings().totemDurationSeconds() * 20));
        final float totalRise = 1.6f;
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 2;
                if (ticks >= durationTicks || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                float progress = (float) ticks / durationTicks;
                float rise = totalRise * progress;
                var t = display.getTransformation();
                t.getLeftRotation().rotationY(progress * (float) Math.PI); // 缓慢旋转一圈
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);
                display.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0, rise, 0),
                        t.getLeftRotation(),
                        new org.joml.Vector3f(scale, scale, scale),
                        t.getRightRotation()));
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void start() {
        state = GameState.RUNNING;
        removeBar();
        if (hasBeds()) restoreBeds();
        placeGuards();
        updateWorldAutoSave(false); // 对局期间关闭所在世界的自动保存，防中途关服写脏地图
        for (Map.Entry<UUID, Team> entry : players.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            alive.add(entry.getKey());
            if (p.isDead()) continue; // 局间切换时还在死亡界面的：重生事件里处理
            prepareSpawn(p, entry.getValue());
        }
        for (Player p : onlinePlayers()) {
            plugin.updateVisibility(p); // 场内玩家互相可见、与大厅互相不可见
            Msg.title(p, "match.start-title", "match.start-sub");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        tickTask = new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                voidCheck(); // 每 5 tick 检测虚空，降低死亡延迟
                runs++;
                if (runs % 4 == 0) { // 每秒
                    mode.onSecondTick(Game.this);
                    plugin.boards().updateGame(Game.this);
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    /** 重算本局参与者与全服的互见关系（匹配传送至出生点后调用，不等 start()） */
    public void refreshVisibility() {
        for (Player p : onlinePlayers()) {
            plugin.updateVisibility(p);
        }
    }

    /**
     * 对局所在世界的自动保存开关：
     * 开局关闭（防中途关服/崩溃把玩家放置/破坏的方块写进地图文件，重启后地图重置），
     * 最后一局结束回滚后再恢复自动保存并把干净地图落盘。
     */
    private void updateWorldAutoSave(boolean enable) {
        Location any = pos().spawn(Team.RED);
        org.bukkit.World world = any == null ? null : any.getWorld();
        if (world == null || arena.worldName() == null) return;
        if (enable) {
            boolean stillActive = plugin.games().active().stream()
                    .anyMatch(g -> g != this && arena.worldName().equals(g.arena().worldName()));
            if (!stillActive) {
                world.setAutoSave(true);
                world.save(); // 落盘回滚后的干净地图
            }
        } else {
            world.setAutoSave(false);
        }
    }

    /** 传送到出生点并发放装备（开局与重生共用） */
    public void prepareSpawn(Player p, Team team) {
        p.setGameMode(GameMode.SURVIVAL);
        clearInventory(p);
        giveKit(p, team);
        org.bukkit.attribute.AttributeInstance maxHealth =
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(20.0); // 重置继承的血量上限
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.clearActivePotionEffects();
        int protect = plugin.settings().spawnProtectionSeconds();
        if (protect > 0) {
            protectionUntil.put(p.getUniqueId(),
                    System.currentTimeMillis() + protect * 1000L); // 重生/开局保护
        }
        Location spawn = pos().spawn(team);
        if (spawn != null && !LocUtil.sameBlock(p.getLocation(), spawn)) {
            p.teleport(spawn); // 已在出生点附近就不重复传送，避免闪"正在加载地形"
        }
        p.setFallDistance(0f);
    }

    // ------------------------------------------------------------------
    // 死亡 / 结算
    // ------------------------------------------------------------------

    public void handleDeath(Player victim) {
        if (state != GameState.RUNNING) return;
        if (!players.containsKey(victim.getUniqueId())) return; // 死亡到结算的 1 tick 里已退出：leave() 已广播并 checkEnd 过
        alive.remove(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer == null) killer = recentAttacker(victim); // 虚空/环境死亡也归属击杀
        lastAttacker.remove(victim.getUniqueId());
        lastAttackTime.remove(victim.getUniqueId());
        boolean credited = false;
        if (killer != null && !killer.equals(victim) && isParticipant(killer.getUniqueId())
                && players.get(killer.getUniqueId()) != players.get(victim.getUniqueId())) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            credited = true;
        }
        if (credited) {
            broadcast("game.killed", nameOf(victim.getUniqueId()), killer.getName());
        } else {
            broadcast("game.died", nameOf(victim.getUniqueId()));
        }
        if (shouldRespawn(victim)) {
            Msg.title(victim, "game.death-title", "game.death-sub");
        } else {
            Msg.title(victim, "game.elim-title", "game.elim-sub");
        }
        checkEnd();
    }

    protected void checkEnd() {
        if (state != GameState.RUNNING) return;
        // 一队"无人存活且没有人会回来"才算被淘汰。
        // 会回来 = 模式允许重生且床还在，或有成员正在幽灵等待（他们进幽灵时床还在，到期会重生）
        boolean redCanComeBack = canTeamComeBack(Team.RED);
        boolean blueCanComeBack = canTeamComeBack(Team.BLUE);
        boolean redOut = aliveCount(Team.RED) == 0 && (!redCanComeBack || teamCount(Team.RED) == 0);
        boolean blueOut = aliveCount(Team.BLUE) == 0 && (!blueCanComeBack || teamCount(Team.BLUE) == 0);
        if (redOut && blueOut) end(null);
        else if (redOut) end(Team.BLUE);
        else if (blueOut) end(Team.RED);
    }

    /** 该队是否还有人会回到场上（幽灵等待中的重生也算） */
    private boolean canTeamComeBack(Team team) {
        if (mode.respawnOnDeath() && bedAlive(team)) return true;
        for (UUID id : ghosts) {
            if (teamOf(id) == team) return true; // 幽灵等待中的成员到期会重生
        }
        return false;
    }

    private int teamCount(Team team) {
        int n = 0;
        for (Team t : players.values()) {
            if (t == team) n++;
        }
        return n;
    }

    /** 一局结束：回合制下先记账，未分出胜负则开下一局 */
    public void end(Team winner) {
        if (state != GameState.RUNNING) return;
        stopTick();
        if (winner != null) {
            roundWins.merge(winner, 1, Integer::sum);
        }
        roundsPlayed++;
        int wins = winner == null ? 0 : roundWins.getOrDefault(winner, 0);
        int needed = roundsTotal / 2 + 1; // 过半即胜（3 局 = 先胜 2）
        int red = roundWins.getOrDefault(Team.RED, 0);
        int blue = roundWins.getOrDefault(Team.BLUE, 0);

        // 一方无人在线 → 直接判整场
        int redOnline = 0;
        int blueOnline = 0;
        for (Map.Entry<UUID, Team> entry : players.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                if (entry.getValue() == Team.RED) redOnline++;
                else blueOnline++;
            }
        }

        boolean seriesOver;
        Team matchWinner = null;
        if (roundsTotal <= 1) {
            seriesOver = true;
            matchWinner = winner;
        } else if (redOnline == 0 && blueOnline == 0) {
            seriesOver = true;
            matchWinner = null;
        } else if (redOnline == 0) {
            seriesOver = true;
            matchWinner = Team.BLUE;
        } else if (blueOnline == 0) {
            seriesOver = true;
            matchWinner = Team.RED;
        } else if (wins >= needed) {
            seriesOver = true;
            matchWinner = winner;
        } else if (roundsPlayed >= roundsTotal) {
            seriesOver = true;
            matchWinner = red == blue ? null : (red > blue ? Team.RED : Team.BLUE);
        } else {
            seriesOver = false;
        }

        if (!seriesOver) {
            if (winner == null) {
                broadcast("game.draw");
            } else {
                broadcast("game.round-won",
                        legacyName(winner), roundsCurrent, red, blue);
            }
            beginRound();
            return;
        }
        finishMatch(matchWinner);
    }

    /** 多局制：重置本局状态并进入短倒计时 */
    private void beginRound() {
        roundsCurrent++;
        state = GameState.STARTING;
        matchCountdown = false;
        countdownSeconds = plugin.settings().roundCountdownSeconds();
        cancelGhosts();
        for (Team team : Team.values()) {
            bedAlive.put(team, true);
        }
        tracker.rollback();
        if (hasBeds()) restoreBeds();
        placeGuards();
        for (UUID id : players.keySet()) {
            alive.add(id); // 存活玩家的装备由 start() 统一发放；死亡界面的由重生事件处理
        }
        countdownBar = Bukkit.createBossBar(
                Msg.text("match.round-bar", roundsCurrent).replace('&', '§'),
                org.bukkit.boss.BarColor.YELLOW, org.bukkit.boss.BarStyle.SEGMENTED_10);
        countdownBar.setProgress(1.0);
        updateBarViewers();
        startCountdownTask();
    }

    /** 队伍名带颜色的旧式字符串（供消息占位符使用） */
    private String legacyName(Team team) {
        return team.legacyName();
    }

    /** 终局结算（原 end 主体） */
    private void finishMatch(Team winner) {
        state = GameState.ENDING;
        stopCountdown();
        stopTick();
        removeBar();
        if (winner == null) {
            broadcast("game.draw");
        } else {
            broadcast("game.win", legacyName(winner));
        }
        if (roundsTotal > 1) {
            broadcast("game.score",
                    roundWins.getOrDefault(Team.RED, 0),
                    roundWins.getOrDefault(Team.BLUE, 0));
        }
        String mvp = mvpDescription();
        if (mvp != null) broadcast("game.mvp", mvp);
        for (Player p : onlinePlayers()) {
            Msg.title(p,
                    winner == null ? "game.end-title-draw" : "game.end-title",
                    "game.end-sub",
                    winner == null ? "" : legacyName(winner));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskLater(plugin, 100L);
    }

    private void cleanup() {
        cancelGhosts();
        dismissSpectators();
        // 先注销（清掉 byPlayer）再逐个回大厅：resetToLobby 里的 updateVisibility
        // 才会按"大厅玩家"重算，否则前对手之间会保持互相可见、打破大厅互隐
        plugin.games().unregister(this);
        for (UUID id : new ArrayList<>(players.keySet())) {
            players.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) resetToLobby(p);
        }
        alive.clear();
        kills.clear();
        arena.releasePosition(position);
        onEnd();
        plugin.games().unregister(this);
        updateWorldAutoSave(true); // 回滚完成后再恢复自动保存并把干净地图落盘
    }

    /** 空场解散（等待中没人了） */
    public void disband() {
        if (state == GameState.ENDING) return;
        state = GameState.ENDING;
        stopCountdown();
        stopTick();
        removeBar();
        cancelGhosts();
        dismissSpectators();
        players.clear();
        alive.clear();
        arena.releasePosition(position);
        onEnd();
        plugin.games().unregister(this);
        updateWorldAutoSave(true);
    }

    /** 关服时立即结束并还原 */
    public void shutdownNow() {
        state = GameState.ENDING;
        stopCountdown();
        stopTick();
        removeBar();
        cancelGhosts();
        dismissSpectators();
        plugin.games().unregister(this); // 与 cleanup 相同：先注销再按大厅身份重算可见性
        for (UUID id : new ArrayList<>(players.keySet())) {
            players.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) resetToLobby(p);
        }
        arena.releasePosition(position);
        onEnd();
        updateWorldAutoSave(true); // 关服前回滚并落盘干净地图
    }

    public void resetToLobby(Player p) {
        recordPersonalKit(p); // 对局结束/关服回大厅前保存调整过的背包（阵亡者无 kit 快照，自动跳过）
        clearInventory(p);
        p.setGameMode(GameMode.SURVIVAL);
        org.bukkit.attribute.AttributeInstance maxHealth =
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(20.0); // 别把继承的上限带进大厅
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setCollidable(true);
        p.clearActivePotionEffects();
        plugin.boards().showLobby(p);
        plugin.lobbyMenu().giveLobbyItems(p); // 游戏菜单 + 快速加入上次
        plugin.updateVisibility(p);           // 回到大厅：与大厅玩家互相隐藏
        Location lobby = plugin.lobby();
        if (lobby != null && !LocUtil.sameBlock(p.getLocation(), lobby)) {
            p.teleport(lobby);
        }
        Msg.send(p, "lobby.returned");
    }

    // ------------------------------------------------------------------
    // 虚空处死
    // ------------------------------------------------------------------

    /** 虚空处死检测（每 5 tick，低延迟；仅游戏进行中，且模式允许） */
    private void voidCheck() {
        int below = plugin.settings().voidBelowSpawn();
        if (below <= 0 || state != GameState.RUNNING || !mode.settings().isVoidKill()) return;
        ArenaPosition pos = pos(); // 点位整局不变，提出循环
        for (Map.Entry<UUID, Team> entry : players.entrySet()) {
            UUID id = entry.getKey();
            if (!alive.contains(id) || ghosts.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            double spawnY = pos.spawnY(entry.getValue());
            if (Double.isNaN(spawnY)) continue;
            if (p.getLocation().getY() < spawnY - below) {
                p.setHealth(0.0); // 走正常死亡流程（有床则进入重生等待）
            }
        }
    }

    // ------------------------------------------------------------------
    // 床（目标方块）
    // ------------------------------------------------------------------

    public boolean hasBeds() {
        return mode.needsBeds();
    }

    /** 模式设置快捷访问 */
    public ModeSettings modeSettings() {
        return mode.settings();
    }

    public boolean bedAlive(Team team) {
        if (!hasBeds()) return false;
        return bedAlive.getOrDefault(team, false);
    }

    /** 床在且模式允许重生则死亡重生，否则死亡即淘汰 */
    public boolean shouldRespawn(Player p) {
        Team team = teamOf(p.getUniqueId());
        return team != null && mode.respawnOnDeath() && bedAlive(team);
    }

    protected void restoreBeds() {
        for (Team team : Team.values()) {
            Location head = pos().bedHead(team);
            BlockFace facing = pos().bedFacing(team);
            if (head == null || facing == null) continue;
            placeBed(team, head.getBlock(), facing);
        }
    }

    private void placeBed(Team team, Block headBlock, BlockFace facing) {
        Material material = team == Team.RED ? Material.RED_BED : Material.BLUE_BED;
        Block footBlock = headBlock.getRelative(facing.getOppositeFace());
        Bed headData = (Bed) material.createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        headBlock.setBlockData(headData);
        Bed footData = (Bed) material.createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        footBlock.setBlockData(footData);
    }

    /** 玩家左键拆床（监听器已把破坏事件取消，实际处理在这里做） */
    public void handleBedBreak(Player breaker, Block block) {
        if (!(block.getBlockData() instanceof Bed bed)) return;
        Location headLoc = bed.getPart() == Bed.Part.HEAD
                ? block.getLocation()
                : block.getRelative(bed.getFacing()).getLocation();
        Team brokenTeam = null;
        for (Team team : Team.values()) {
            Location stored = pos().bedHead(team);
            if (stored != null && LocUtil.sameBlock(stored, headLoc)) {
                brokenTeam = team;
                break;
            }
        }
        if (brokenTeam == null) return; // 不是本游戏的床
        Team breakerTeam = teamOf(breaker.getUniqueId());
        if (breakerTeam == brokenTeam) {
            Msg.send(breaker, "bed.own");
            return;
        }
        if (!bedAlive(brokenTeam)) return;
        bedAlive.put(brokenTeam, false);
        block.setType(Material.AIR);
        Block other = bed.getPart() == Bed.Part.HEAD
                ? block.getRelative(bed.getFacing().getOppositeFace())
                : block.getRelative(bed.getFacing());
        other.setType(Material.AIR);
        broadcast("bed.destroyed", breaker.getName(), legacyName(brokenTeam));
        for (Player pl : onlinePlayers()) {
            Team team = teamOf(pl.getUniqueId());
            if (team == null) continue;
            if (team == brokenTeam) {
                Msg.title(pl, "bed.destroyed-title", "bed.destroyed-sub");
            } else {
                Msg.title(pl, "bed.enemy-title", "bed.enemy-sub", legacyName(brokenTeam));
            }
            pl.playSound(pl.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
    }

    // ------------------------------------------------------------------
    // 围床结构
    // ------------------------------------------------------------------

    public boolean isGuardBlock(Block block) {
        return guardBlocks.contains(block.getLocation());
    }

    /** 把记录的围床结构放到两张床周围（每局开始/结束调用；蓝床按朝向镜像），本局内这些方块可拆可炸 */
    private void placeGuards() {
        guardBlocks.clear();
        if (!mode.settings().isNeedsGuard() || arena.getGuardEntries().isEmpty()) return;
        for (Team team : Team.values()) {
            Location head = pos().bedHead(team);
            if (head == null) continue;
            for (Arena.GuardEntry entry : arena.getGuardEntries()) {
                Arena.GuardEntry e = team == Team.BLUE ? arena.entryForBlue(pos(), entry) : entry;
                Block block = head.clone().add(e.dx(), e.dy(), e.dz()).getBlock();
                block.setBlockData(Bukkit.createBlockData(e.data()));
                guardBlocks.add(block.getLocation());
            }
        }
    }

    // ------------------------------------------------------------------
    // 重生等待（幽灵状态）：隐身无粒子 + 无敌 + 可飞行 + 无碰撞 + 不可攻击/破坏
    // ------------------------------------------------------------------

    public boolean isGhost(UUID id) {
        return ghosts.contains(id);
    }

    /** 死亡后进入重生等待（秒数 settings.respawn-seconds），期间为幽灵状态 */
    public void beginGhostRespawn(Player p, Team team) {
        ghosts.add(p.getUniqueId());
        alive.remove(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);
        p.setFallDistance(0f);
        p.setFireTicks(0);
        // 无粒子隐身（图标也不显示），到时自动失效，prepareSpawn 也会清一次
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setCollidable(false); // 无碰撞箱：其他玩家碰不到
        plugin.updateVisibility(p); // 幽灵对所有玩家隐藏（可见性唯一出口，别的玩家刷新也不会暴露）
        int wait = plugin.settings().respawnSeconds();
        UUID id = p.getUniqueId();
        ghostTasks.put(id, new BukkitRunnable() {
            int left = wait;

            @Override
            public void run() {
                Player player = Bukkit.getPlayer(id);
                if (!players.containsKey(id) || state != GameState.RUNNING || player == null) {
                    cancel();
                    ghostTasks.remove(id);
                    if (player != null) endGhost(player);
                    return;
                }
                if (left <= 0) {
                    cancel();
                    ghostTasks.remove(id);
                    endGhost(player);
                    markAlive(id);
                    prepareSpawn(player, team);
                    Msg.title(player, "ghost.respawn-title", "ghost.respawn-sub");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                    return;
                }
                Msg.title(player, "ghost.wait-title", "ghost.wait-sub", left);
                left--;
            }
        }.runTaskTimer(plugin, 10L, 20L));
    }

    private void endGhost(Player p) {
        ghosts.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setCollidable(true);
        p.setFallDistance(0f);
        // 不再对所有人 showPlayer（会覆盖大厅互隐），按当前身份整体重算
        plugin.updateVisibility(p);
    }

    /** 立即清除某个玩家的幽灵状态（退出/掉线时用，不等重生倒计时任务） */
    private void cancelGhost(UUID id) {
        BukkitTask task = ghostTasks.remove(id);
        if (task != null) task.cancel();
        if (ghosts.remove(id)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.setFlying(false);
                p.setAllowFlight(false);
                p.setCollidable(true);
            }
        }
    }

    /** 结束/解散时清理所有幽灵状态与任务 */
    protected void cancelGhosts() {
        for (BukkitTask task : ghostTasks.values()) {
            task.cancel();
        }
        ghostTasks.clear();
        for (UUID id : new ArrayList<>(ghosts)) {
            ghosts.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.setFlying(false);
                p.setAllowFlight(false);
                p.setCollidable(true);
                plugin.updateVisibility(p);
            }
        }
    }

    // ------------------------------------------------------------------
    // 装备：个人 kit > 模式 kit（/pa kit 设置）> 模式默认，每次重生都是满配
    // ------------------------------------------------------------------

    /** 本局实际发给各玩家的 kit 快照：死亡/离场时对比背包，玩家自己调整过才记为个人 kit */
    private final Map<UUID, KitManager.Kit> givenKit = new HashMap<>();

    /** 发放装备：个人 kit > 模式 kit（/pa kit 设置）或模式默认，每次重生都是满配 */
    protected final void giveKit(Player p, Team team) {
        PlayerInventory inv = p.getInventory();
        boolean customKit = giveArenaKit(p, team, inv);
        // 守家羊毛只在默认 kit 时附加：个人/模式 kit 的围床方块由 kit 本身提供（teamColored 自动染队色），避免多给
        if (!customKit) {
            ItemStack guard = guardItem(team);
            if (guard != null && guard.getAmount() > 0) {
                inv.addItem(guard);
            }
        }
        givenKit.put(p.getUniqueId(), snapshotInventory(p));
    }

    /** @return 是否发放了自定义 kit（个人 kit 或模式 kit）；false = 模式默认 kit */
    private boolean giveArenaKit(Player p, Team team, PlayerInventory inv) {
        KitManager.Kit personal = plugin.playerKits().get(p.getUniqueId(), mode.id());
        KitManager.Kit kit = plugin.kits().get(mode.id());
        boolean custom = kit != null;
        if (kit != null) {
            // 模式级自定义 kit（/pa kit <模式> 设置，该模式所有竞技场共用）
            List<ItemStack> items = kit.storage();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.getType().isAir()) inv.setItem(i, teamColored(stack.clone(), team));
            }
            if (kit.helmet() != null) inv.setHelmet(teamColored(kit.helmet().clone(), team));
            if (kit.chestplate() != null) inv.setChestplate(teamColored(kit.chestplate().clone(), team));
            if (kit.leggings() != null) inv.setLeggings(teamColored(kit.leggings().clone(), team));
            if (kit.boots() != null) inv.setBoots(teamColored(kit.boots().clone(), team));
        } else {
            mode.giveDefaultKit(this, p, team);
            // 默认 kit 里的皮革也染成队伍颜色
            for (ItemStack piece : inv.getArmorContents()) {
                teamColored(piece, team);
            }
        }
        // 个人变化量叠加：AIR = 清空该槽位；item = 用玩家调整后的版本；没动的槽位跟随上面的原 kit
        if (personal != null) {
            List<ItemStack> items = personal.storage();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null) continue;
                if (stack.getType().isAir()) inv.setItem(i, null);
                else inv.setItem(i, teamColored(stack.clone(), team));
            }
            if (personal.helmet() != null) {
                if (personal.helmet().getType().isAir()) inv.setHelmet(null);
                else inv.setHelmet(teamColored(personal.helmet().clone(), team));
            }
            if (personal.chestplate() != null) {
                if (personal.chestplate().getType().isAir()) inv.setChestplate(null);
                else inv.setChestplate(teamColored(personal.chestplate().clone(), team));
            }
            if (personal.leggings() != null) {
                if (personal.leggings().getType().isAir()) inv.setLeggings(null);
                else inv.setLeggings(teamColored(personal.leggings().clone(), team));
            }
            if (personal.boots() != null) {
                if (personal.boots().getType().isAir()) inv.setBoots(null);
                else inv.setBoots(teamColored(personal.boots().clone(), team));
            }
        }
        return custom;
    }

    /** 死亡/离场前调用：背包和发下来的 kit 不一样（玩家自己调整过）→ 记为该玩家在该模式的个人 kit */
    public void recordPersonalKit(Player p) {
        recordPersonalKit(p, null);
    }

    /**
     * 带 deathDrops 的死亡路径：Paper 26.2 实测死亡事件触发时背包还在（下一 tick 才被清空），
     * 但部分版本在事件前就把背包移进了掉落列表——那时背包快照为空，用掉落物重建以免把空背包记成个人 kit。
     * 非死亡路径（退出/回大厅）传 null。
     */
    public void recordPersonalKit(Player p, List<ItemStack> deathDrops) {
        KitManager.Kit given = givenKit.remove(p.getUniqueId());
        if (given == null) return; // 没领过装备（排队中/幽灵等待/已阵亡）
        KitManager.Kit current = snapshotInventory(p);
        if (isEmptyKit(current) && deathDrops != null && !deathDrops.isEmpty()) {
            current = kitFromDrops(deathDrops);
        }
        if (kitEquals(current, given)) {
            // 玩家把背包恢复成发下来的原样：清掉个人变化量，继续跟随模式 kit（管理员改动能同步）
            if (plugin.playerKits().clear(p.getUniqueId(), mode.id())) {
                Msg.send(p, "kit.personal-cleared", "mode", mode.display());
            }
            return;
        }
        // 只记变化量：与发下来的 kit 相同的槽位不记（进游戏时跟随原 kit 运算）
        plugin.playerKits().record(p.getUniqueId(), mode.id(), kitDelta(given, current));
        Msg.send(p, "kit.personal-saved", mode.display());
    }

    /**
     * 变化量：与发下来的 kit 逐槽位对比——
     * 相同的槽位记 null（发放时跟随原 kit）、被清空的槽位记 AIR、其余记玩家调整后的内容。
     */
    private static KitManager.Kit kitDelta(KitManager.Kit given, KitManager.Kit current) {
        List<ItemStack> storage = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            ItemStack g = given.storage().get(i);
            ItemStack c = current.storage().get(i);
            storage.add(itemEquals(g, c) ? null
                    : (c == null ? new ItemStack(Material.AIR) : c));
        }
        return new KitManager.Kit(storage,
                armorDelta(given.helmet(), current.helmet()),
                armorDelta(given.chestplate(), current.chestplate()),
                armorDelta(given.leggings(), current.leggings()),
                armorDelta(given.boots(), current.boots()));
    }

    private static ItemStack armorDelta(ItemStack given, ItemStack current) {
        return itemEquals(given, current) ? null
                : (current == null ? new ItemStack(Material.AIR) : current);
    }

    /** 数量+类型+元数据都一致才算没变（AIR/空视为一致） */
    private static boolean itemEquals(ItemStack a, ItemStack b) {
        boolean aEmpty = a == null || a.getType().isAir();
        boolean bEmpty = b == null || b.getType().isAir();
        if (aEmpty && bEmpty) return true;
        if (aEmpty != bEmpty) return false;
        return b.isSimilar(a) && b.getAmount() == a.getAmount();
    }

    /** 快照是否完全没有物品（36 格 + 四件盔甲全空） */
    private static boolean isEmptyKit(KitManager.Kit kit) {
        for (ItemStack stack : kit.storage()) {
            if (stack != null && !stack.getType().isAir()) return false;
        }
        return kit.helmet() == null && kit.chestplate() == null
                && kit.leggings() == null && kit.boots() == null;
    }

    /** 从掉落物重建 kit 快照：盔甲按装备槽归类，其余按顺序填入背包格（掉落列表本身不带槽位信息） */
    private static KitManager.Kit kitFromDrops(List<ItemStack> drops) {
        List<ItemStack> storage = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) storage.add(null);
        ItemStack helmet = null, chestplate = null, leggings = null, boots = null;
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            switch (drop.getType().getEquipmentSlot()) {
                case HEAD -> { if (helmet == null) helmet = drop.clone(); }
                case CHEST -> { if (chestplate == null) chestplate = drop.clone(); }
                case LEGS -> { if (leggings == null) leggings = drop.clone(); }
                case FEET -> { if (boots == null) boots = drop.clone(); }
                default -> {
                    for (int i = 0; i < storage.size(); i++) {
                        if (storage.get(i) == null) {
                            storage.set(i, drop.clone());
                            break;
                        }
                    }
                }
            }
        }
        return new KitManager.Kit(storage, helmet, chestplate, leggings, boots);
    }

    /** 当前背包（36 格 + 四件盔甲）的完整快照 */
    private KitManager.Kit snapshotInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        List<ItemStack> storage = new ArrayList<>(36);
        for (ItemStack stack : inv.getStorageContents()) {
            storage.add(stack == null ? null : stack.clone());
        }
        return new KitManager.Kit(storage,
                cloneOrNull(inv.getHelmet()), cloneOrNull(inv.getChestplate()),
                cloneOrNull(inv.getLeggings()), cloneOrNull(inv.getBoots()));
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    /** 逐槽位比较（类型+数量+元数据）；同局同队，队色差异不参与 */
    private static boolean kitEquals(KitManager.Kit a, KitManager.Kit b) {
        List<ItemStack> as = a.storage();
        List<ItemStack> bs = b.storage();
        int n = Math.max(as.size(), bs.size());
        for (int i = 0; i < n; i++) {
            ItemStack x = i < as.size() ? as.get(i) : null;
            ItemStack y = i < bs.size() ? bs.get(i) : null;
            if (!java.util.Objects.equals(x, y)) return false;
        }
        return java.util.Objects.equals(a.helmet(), b.helmet())
                && java.util.Objects.equals(a.chestplate(), b.chestplate())
                && java.util.Objects.equals(a.leggings(), b.leggings())
                && java.util.Objects.equals(a.boots(), b.boots());
    }

    /** 默认 kit 的守家羊毛（自定义 kit 的围床方块由 kit 本身提供，会自动染队色） */
    protected ItemStack guardItem(Team team) {
        int amount = plugin.settings().kitBlocks();
        if (amount <= 0) return null;
        return new ItemStack(team == Team.RED ? Material.RED_WOOL : Material.BLUE_WOOL, amount);
    }

    /** 皮革防具自动染成队伍颜色，羊毛自动换成队伍颜色羊毛，其他物品原样返回 */
    private ItemStack teamColored(ItemStack stack, Team team) {
        if (stack == null) return null;
        if (stack.getType().name().endsWith("_WOOL")) {
            return new ItemStack(team == Team.RED ? Material.RED_WOOL : Material.BLUE_WOOL, stack.getAmount());
        }
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(team == Team.RED
                    ? org.bukkit.Color.fromRGB(0xB0, 0x2E, 0x26)
                    : org.bukkit.Color.fromRGB(0x3C, 0x44, 0xAA));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // 结束钩子
    // ------------------------------------------------------------------

    protected void onEnd() {
        if (hasBeds()) restoreBeds();
        tracker.rollback();
        placeGuards();
    }

    // ------------------------------------------------------------------
    // 观战
    // ------------------------------------------------------------------

    public boolean isSpectator(UUID id) {
        return spectators.contains(id);
    }

    /** 场外玩家进入观战（旁观模式，传送到观战点） */
    public void addSpectator(Player p) {
        spectators.add(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        Location point = arena.spectatorPoint(position);
        if (point != null) p.teleport(point);
        plugin.updateVisibility(p);
        broadcast("spectate.started", p.getName());
        plugin.boards().showGame(p, this);
        Msg.send(p, "spectate.hint", arena.getName());
    }

    /** 退出观战并回大厅 */
    public void removeSpectator(Player p) {
        if (!spectators.remove(p.getUniqueId())) return;
        returnSpectatorToLobby(p);
    }

    /** 玩家掉线时静默移除 */
    public void removeSpectatorQuiet(UUID id) {
        spectators.remove(id);
    }

    private void returnSpectatorToLobby(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setFlying(false);
        p.setAllowFlight(false);
        plugin.boards().showLobby(p);
        plugin.lobbyMenu().giveLobbyItems(p);
        plugin.updateVisibility(p);
        Location lobby = plugin.lobby();
        if (lobby != null && !LocUtil.sameBlock(p.getLocation(), lobby)) {
            p.teleport(lobby);
        }
        Msg.send(p, "spectate.ended");
    }

    private void dismissSpectators() {
        for (UUID id : new ArrayList<>(spectators)) {
            Player p = Bukkit.getPlayer(id);
            spectators.remove(id);
            if (p != null && p.isOnline()) returnSpectatorToLobby(p);
        }
    }

    // ------------------------------------------------------------------
    // 查询 / 工具
    // ------------------------------------------------------------------

    public boolean isFull() {
        return players.size() >= maxPlayers();
    }

    public int playersCount() {
        return players.size();
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public boolean isAlive(UUID id) {
        return alive.contains(id);
    }

    public void markAlive(UUID id) {
        alive.add(id);
    }

    public Team teamOf(UUID id) {
        return players.get(id);
    }

    public int aliveCount(Team team) {
        int n = 0;
        for (Map.Entry<UUID, Team> entry : players.entrySet()) {
            if (entry.getValue() == team && alive.contains(entry.getKey())) n++;
        }
        return n;
    }

    public BlockTracker tracker() {
        return tracker;
    }

    public List<Player> onlinePlayers() {
        List<Player> list = new ArrayList<>(players.size());
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    /** 记分板观众（参与者 + 观战者）：记分板每秒刷新只需遍历这里，不必全服扫描 */
    public List<UUID> scoreboardViewers() {
        List<UUID> out = new ArrayList<>(players.size() + spectators.size());
        out.addAll(players.keySet());
        out.addAll(spectators);
        return out;
    }

    /** 广播一条消息键（含占位符），参与者和观战者都会收到。
     *  Component 不可变：整条消息只解析一次（原实现按接收者各解析一遍），复用发给所有人 */
    public void broadcast(String key, Object... replacements) {
        Component line = Msg.prefix().append(Msg.component(key, replacements));
        for (Player p : onlinePlayers()) {
            p.sendMessage(line);
        }
        // 观战者也同步收听比赛消息
        for (UUID id : spectators) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(line);
        }
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(arena.getName())
                .append(" [").append(mode.display()).append("] ").append(state);
        sb.append(" 玩家:");
        for (UUID id : players.keySet()) sb.append(" ").append(nameOf(id));
        if (!spectators.isEmpty()) {
            sb.append(" 观战:");
            for (UUID id : spectators) sb.append(" ").append(nameOf(id));
        }
        return sb.toString();
    }

    /** 记分板行（模板在 messages.yml 的 scoreboard.* 键，支持 & 颜色码） */
    public List<String> scoreboardLinesFor(Player p) {
        List<String> lines = new ArrayList<>();
        lines.add(Msg.text("scoreboard.mode", mode.display()));
        lines.add(Msg.text("scoreboard.arena", arena.getName()));
        switch (state) {
            case WAITING -> lines.add(Msg.text("scoreboard.waiting", players.size(), maxPlayers()));
            case STARTING -> lines.add(Msg.text("scoreboard.countdown", countdownSeconds));
            case RUNNING -> lines.add(Msg.text("scoreboard.running"));
            case ENDING -> lines.add(Msg.text("scoreboard.ended"));
        }
        if (state == GameState.RUNNING || state == GameState.STARTING) {
            lines.add(Msg.text("scoreboard.alive", aliveCount(Team.RED), aliveCount(Team.BLUE)));
        }
        if (roundsTotal > 1) {
            lines.add(Msg.text("scoreboard.rounds",
                    roundWins.getOrDefault(Team.RED, 0),
                    roundWins.getOrDefault(Team.BLUE, 0),
                    roundsCurrent));
        }
        if (state == GameState.RUNNING && hasBeds()) {
            lines.add(Msg.text("scoreboard.beds",
                    Msg.text(bedAlive(Team.RED) ? "scoreboard.bed-yes" : "scoreboard.bed-no"),
                    Msg.text(bedAlive(Team.BLUE) ? "scoreboard.bed-yes" : "scoreboard.bed-no")));
        }
        List<String> extra = new ArrayList<>();
        mode.addScoreboardLines(this, extra);
        lines.addAll(extra);
        lines.add(Msg.text("scoreboard.kills", kills.getOrDefault(p.getUniqueId(), 0)));
        return lines;
    }

    protected String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString().substring(0, 8) : name;
    }

    private String mvpDescription() {
        UUID best = null;
        int bestKills = 0;
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            if (entry.getValue() > bestKills) {
                bestKills = entry.getValue();
                best = entry.getKey();
            }
        }
        return best == null ? null : nameOf(best) + " (" + bestKills + "杀)";
    }

    protected void clearInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
    }

    /** 重生/开局保护期内 */
    public boolean isProtected(UUID id) {
        Long until = protectionUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** 进攻会打破自己的保护 */
    public void breakProtection(UUID id) {
        protectionUntil.remove(id);
    }

    private void updateBarViewers() {
        if (countdownBar == null) return;
        // 只遍历本局玩家（原来扫全服在线玩家）；再清掉已退局的残留观察者
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) countdownBar.addPlayer(p);
        }
        for (Player p : countdownBar.getPlayers()) {
            if (!players.containsKey(p.getUniqueId())) countdownBar.removePlayer(p);
        }
    }

    private void removeBar() {
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar = null;
        }
    }

    protected void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    protected void stopTick() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }
}
