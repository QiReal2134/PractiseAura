package dev.aura.practise.listener;

import java.util.ArrayList;
import java.util.List;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.command.sub.SettingSub;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.Game;
import dev.aura.practise.game.GameState;
import dev.aura.practise.game.PendingBed;
import dev.aura.practise.game.PendingSetting;
import dev.aura.practise.game.Team;
import dev.aura.practise.menu.LobbyMenu;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GameListener implements Listener {

    private final PractiseAuraPlugin plugin;

    public GameListener(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // 进出服务器
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.boards().showLobby(p);
        plugin.lobbyMenu().giveLobbyItems(p);
        plugin.updateVisibility(p);
        // 兜底：中途崩溃/掉线可能把继承的血量上限写进玩家数据，进服时重置
        org.bukkit.attribute.AttributeInstance maxHealth =
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(20.0);
        Location lobby = plugin.lobby();
        if (lobby != null && !p.hasPermission("practiseaura.admin")) {
            p.teleport(lobby);
        }
        Msg.send(p, "join.welcome");
        Msg.send(p, "join.hint");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.games().handleQuit(p);
        plugin.boards().remove(p);
        plugin.pendingBeds().remove(p.getUniqueId());
        plugin.pendingSettings().remove(p.getUniqueId());
        plugin.lastModes().forget(p.getUniqueId());
        plugin.duelInvites().remove(p.getUniqueId()); // 以该玩家为目标的约战邀请
        plugin.duelCooldowns().remove(p.getUniqueId());
    }

    /** 聊天输入模式：玩家点击设置项后，下一条聊天消息作为数值（本事件在异步聊天线程触发） */
    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        // remove 是原子操作：返回 null 说明没有待输入项（或已被主线程取消/过期清理），聊天放行
        PendingSetting pending = plugin.pendingSettings().remove(p.getUniqueId());
        if (pending == null) return;
        e.setCancelled(true);
        if (pending.expired()) {
            Msg.send(p, "setting.input-expired");
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                SettingSub.apply(plugin, p, pending.key(), text));
    }

    // ------------------------------------------------------------------
    // 死亡 / 重生
    // ------------------------------------------------------------------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Game game = plugin.games().gameOf(victim.getUniqueId());
        // 清空掉落前先留一份快照：万一所在版本在事件触发前就把背包移进了掉落列表，可据此重建死亡 kit
        List<ItemStack> deathDrops = new ArrayList<>(e.getDrops().size());
        for (ItemStack drop : e.getDrops()) {
            deathDrops.add(drop == null ? null : drop.clone());
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (game == null || game.state() != GameState.RUNNING || !game.isAlive(victim.getUniqueId())) {
            return;
        }
        e.deathMessage(Msg.component("game.death-message", victim.getName()));
        // 死亡瞬间背包还在（Paper 26.2 下一 tick 才被原版清空）；若版本提前清空则由 recordPersonalKit 用掉落物兜底
        game.recordPersonalKit(victim, deathDrops);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isOnline()) game.handleDeath(victim);
            }
        }.runTask(plugin);
        // 跳过死亡界面：自动触发重生，走正常 PlayerRespawnEvent 流程（观战/幽灵），丝滑无加载
        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isOnline() && victim.isDead()) victim.spigot().respawn();
            }
        }.runTaskLater(plugin, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game == null || !game.isParticipant(p.getUniqueId())) return;
        if (game.state() == GameState.RUNNING && game.shouldRespawn(p)) {
            Team team = game.teamOf(p.getUniqueId());
            Location spawn = game.spawn(team);
            if (spawn != null) e.setRespawnLocation(spawn);
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 2 tick 内可能已退局/掉线，重新校验
                    if (!p.isOnline() || !game.isParticipant(p.getUniqueId())) return;
                    if (plugin.settings().respawnSeconds() > 0) {
                        game.beginGhostRespawn(p, team); // 幽灵等待，倒计时结束自动重生
                    } else {
                        game.markAlive(p.getUniqueId());
                        game.prepareSpawn(p, team);
                        Msg.title(p, "ghost.respawn-title", "ghost.respawn-sub");
                    }
                }
            }.runTaskLater(plugin, 2L);
        } else if (game.state() == GameState.STARTING && !game.isMatchCountdown()) {
            // 多局制局间切换时还在死亡界面的：直接满状态重生进新的一局
            Team team = game.teamOf(p.getUniqueId());
            Location spawn = game.spawn(team);
            if (spawn != null) e.setRespawnLocation(spawn);
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 2 tick 内可能已退局，重新校验
                    if (!p.isOnline() || !game.isParticipant(p.getUniqueId())) return;
                    game.markAlive(p.getUniqueId());
                    game.prepareSpawn(p, team);
                }
            }.runTaskLater(plugin, 2L);
        } else {
            Location spec = game.arena().spectatorPoint(game.position());
            Location lobby = plugin.lobby();
            Location target = spec != null ? spec : (lobby != null ? lobby : p.getWorld().getSpawnLocation());
            e.setRespawnLocation(target);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) return;
                    p.setGameMode(GameMode.SPECTATOR);
                    Msg.title(p, "game.elim-title", "game.elim-sub");
                }
            }.runTaskLater(plugin, 2L);
        }
    }

    // ------------------------------------------------------------------
    // 等待开始冻结（仅匹配成功传送后、倒计时期间）
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // 传送（含匹配传送）不受冻结影响
        if (e instanceof PlayerTeleportEvent) return;
        if (!plugin.games().hasActive()) return; // 热路径：无游戏直接跳过
        Player p = e.getPlayer();
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game == null || game.state() != GameState.STARTING) return;
        // 允许转头（坐标没变就不拦）
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Location from = e.getFrom().clone(); // 不直接改事件返回的 Location，clone 后再设朝向
        from.setDirection(e.getTo().getDirection());
        e.setTo(from);
    }

    // ------------------------------------------------------------------
    // 伤害 / 饥饿
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game == null) {
            // 大厅保护：对所有玩家免伤（含 PVP），掉虚空则传回大厅（未设置大厅时回主世界出生点）
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location target = plugin.lobby();
                if (target == null) target = Bukkit.getWorlds().get(0).getSpawnLocation();
                p.setFallDistance(0f);
                p.teleport(target);
            }
            return;
        }
        if (game.state() != GameState.RUNNING) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Team team = game.teamOf(p.getUniqueId());
                Location spawn = team == null ? null : game.spawn(team);
                if (spawn != null) {
                    p.setFallDistance(0f);
                    p.teleport(spawn);
                }
            }
            return;
        }
        if (game.isProtected(p.getUniqueId())) {
            e.setCancelled(true); // 重生/开局保护期内免伤
            return;
        }
        if (game.isGhost(p.getUniqueId())) {
            e.setCancelled(true); // 重生等待中无敌
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Team team = game.teamOf(p.getUniqueId());
                Location spawn = team == null ? null : game.spawn(team);
                if (spawn != null) {
                    p.setFallDistance(0f);
                    p.teleport(spawn);
                    p.setFlying(true);
                }
            }
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true); // 无摔落伤害
        }
        // VOID 等其余伤害放行 → 走正常死亡流程
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        // 火球的直接命中伤害：游戏内火球的伤害完全由爆炸逻辑接管，这里一律取消
        // （否则火球伤害设为 0 也会有原版直击伤害）
        if (e.getDamager() instanceof Fireball fireball
                && fireball.getShooter() instanceof Player shooter
                && plugin.games().gameOf(shooter.getUniqueId()) != null) {
            e.setCancelled(true);
            return;
        }
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        Game attackerGame = plugin.games().gameOf(attacker.getUniqueId()); // 查一次，后面共用
        // 攻击到人 → 立即结束自己的无敌（保护）时间（无论打的是谁）
        if (attackerGame != null && attackerGame.isProtected(attacker.getUniqueId())) {
            attackerGame.breakProtection(attacker.getUniqueId());
        }
        Game victimGame = plugin.games().gameOf(victim.getUniqueId());
        if (victimGame == null) {
            e.setCancelled(true); // 大厅/场外一律禁止 PVP（含对管理员）
            return;
        }
        if (victimGame.state() != GameState.RUNNING) {
            e.setCancelled(true);
            return;
        }
        // 重生等待中的幽灵：打不了人也不会被打
        if (victimGame.isGhost(victim.getUniqueId()) || victimGame.isGhost(attacker.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (victimGame.isProtected(victim.getUniqueId())) {
            e.setCancelled(true); // 保护期内被打无效
            return;
        }
        if (victimGame.isProtected(attacker.getUniqueId())) {
            victimGame.breakProtection(attacker.getUniqueId()); // 主动进攻打破自己的保护
        }
        if (attacker.equals(victim)) return; // 自己火球的自伤（火球跳）
        if (attackerGame != victimGame) {
            e.setCancelled(true); // 场外玩家打不到场内
            return;
        }
        if (victimGame.teamOf(attacker.getUniqueId()) == victimGame.teamOf(victim.getUniqueId())) {
            e.setCancelled(true); // 同队免伤
            return;
        }
        // 模式级 PVP / 伤害开关
        if (!victimGame.modeSettings().isPvp()) {
            e.setCancelled(true); // 禁止攻击玩家（连击退也没有）
            return;
        }
        if (!victimGame.modeSettings().isDamageEnabled()) {
            e.setDamage(0.0); // 拳击式：有击退无伤害（打空血条不掉，靠虚空/淘汰机制）
        }
        victimGame.setLastAttacker(victim, attacker); // 记录最近攻击者（虚空死亡归属击杀）
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        e.setCancelled(true); // 全服不掉饥饿
    }

    // ------------------------------------------------------------------
    // 方块
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game == null) {
            if (!p.hasPermission("practiseaura.admin")) e.setCancelled(true);
            return;
        }
        Block block = e.getBlock();
        // 床：永远不走原版破坏，交由游戏逻辑判定（敌方可拆、己方拒绝）
        if (Tag.BEDS.isTagged(block.getType())) {
            e.setCancelled(true);
            if (game.hasBeds() && game.state() == GameState.RUNNING && game.isAlive(p.getUniqueId())) {
                game.handleBedBreak(p, block);
            }
            return;
        }
        if (game.state() != GameState.RUNNING || !game.isAlive(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        // 围床结构：可拆（每局开始会重新放置），但不掉落物品——防止每局重复刷取围床材料
        if (game.isGuardBlock(block)) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            return;
        }
        // 玩家自己放的方块：按模式设置决定可否拆
        if (game.tracker().isPlaced(block)) {
            if (!game.modeSettings().isAllowBreakPlaced()) {
                e.setCancelled(true);
                return;
            }
            game.tracker().onBreak(block);
            return; // 放行原版破坏（含掉落）
        }
        // 地图方块：模式允许破坏全图，或配置了可建设区域且在区域内
        if (game.modeSettings().isAllowBreakMap() || game.arena().inBuildRegion(block.getLocation())) {
            game.tracker().onBreak(block);
            return;
        }
        e.setCancelled(true); // 其余地图方块受保护
    }

    /** 兜底：床永远不掉落物（防止半格床/爆炸边缘情况刷出床物品，床方块本身不会被移除） */
    @EventHandler(ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent e) {
        if (!Tag.BEDS.isTagged(e.getBlock().getType())) return;
        if (plugin.games().gameOf(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game == null) {
            if (!p.hasPermission("practiseaura.admin")) e.setCancelled(true);
            return;
        }
        if (game.state() != GameState.RUNNING || !game.isAlive(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        // 模式禁止放置方块
        if (!game.modeSettings().isAllowPlace()) {
            e.setCancelled(true);
            Msg.send(p, "place.mode-denied");
            return;
        }
        // 配置了可建设区域时限制在区域内；未配置则整张图可放（结束会回滚）
        if (game.arena().hasBuildRegion() && !game.arena().inBuildRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
            return;
        }
        // 虚空线下禁止放置（防止掉落后搭路自救）
        int limit = plugin.settings().placeLimitBelowSpawn();
        if (limit > 0) {
            Team team = game.teamOf(p.getUniqueId());
            double spawnY = team == null ? Double.NaN : game.spawnY(team);
            if (!Double.isNaN(spawnY) && e.getBlock().getY() < spawnY - limit) {
                e.setCancelled(true);
                Msg.send(p, "place.denied");
                return;
            }
        }
        game.tracker().onPlace(e.getBlock(), e.getBlockReplacedState().getBlockData());
    }

    // ------------------------------------------------------------------
    // 交互（设床 / 模式右键钩子 / 防睡觉）
    // ------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 管理员设置床：左键点击目标床
        PendingBed pending = plugin.pendingBeds().get(p.getUniqueId());
        if (pending != null) {
            if (pending.expired()) {
                plugin.pendingBeds().remove(p.getUniqueId());
            } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().name().endsWith("_BED")) {
                e.setCancelled(true);
                consumePendingBed(p, pending, e.getClickedBlock());
                return;
            }
        }

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        // 等待区菜单物品 / 退出排队染料
        String tag = plugin.lobbyMenu().tagOf(e.getItem());
        if (tag != null) {
            e.setCancelled(true);
            switch (tag) {
                case "menu" -> {
                    if (plugin.games().gameOf(p.getUniqueId()) == null) plugin.lobbyMenu().open(p);
                }
                case "rejoin" -> {
                    ModeHandler last = plugin.lastModes().lastOf(p.getUniqueId());
                    if (last != null && plugin.games().gameOf(p.getUniqueId()) == null) {
                        plugin.games().join(p, last);
                    }
                }
                case "leave" -> plugin.games().leave(p);
                default -> {
                }
            }
            return;
        }

        Game game = plugin.games().gameOf(p.getUniqueId());

        // 模式右键钩子（FireBallFight 发射火球等）：仅游戏进行中且玩家存活时交给模式处理
        if (game != null && game.state() == GameState.RUNNING && game.isAlive(p.getUniqueId())
                && game.mode().onRightClick(game, p)) {
            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);
            return;
        }

        // 防止睡觉 / 炸床
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().name().endsWith("_BED")
                && (game != null || !p.hasPermission("practiseaura.admin"))) {
            e.setCancelled(true);
        }
    }

    private void consumePendingBed(Player p, PendingBed pending, Block block) {
        Arena arena = plugin.arenas().get(pending.arenaName());
        if (arena == null) {
            Msg.send(p, "error.arena-missing", pending.arenaName());
            return;
        }
        if (!(block.getBlockData() instanceof Bed bed)) {
            Msg.send(p, "setup.not-bed");
            return;
        }
        Block head = bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing());
        BlockFace facing = bed.getFacing();
        arena.position(pending.position()).setBed(pending.team(), head.getLocation(), facing);
        plugin.arenas().saveAll();
        plugin.pendingBeds().remove(p.getUniqueId());
        Msg.send(p, "setbed.done", arena.getName(), pending.position(), pending.team().display());
    }

    // ------------------------------------------------------------------
    // 火球爆炸（完全接管：不破坏方块，纯特效 + 击退/伤害）
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof Fireball fireball)) return;
        if (!(fireball.getShooter() instanceof Player shooter)) return;
        Game game = plugin.games().gameOf(shooter.getUniqueId());
        if (game == null) return; // 非游戏内火球不干预

        e.setCancelled(true); // 阻止原版伤害/击退/破坏，全部手动控制
        Location center = fireball.getLocation();
        org.bukkit.World world = center.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

        // 实体：可配置的水平/垂直击退 + 伤害
        double radius = plugin.settings().fireballRadius();
        double powerX = plugin.settings().fireballPowerX();
        double powerY = plugin.settings().fireballPowerY();
        double damage = plugin.settings().fireballDamage();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player victim)) continue;
            if (!game.isParticipant(victim.getUniqueId()) || game.isGhost(victim.getUniqueId())) continue;
            Vector delta = victim.getLocation().toVector().subtract(center.toVector());
            if (delta.length() > radius) continue;
            Vector horiz = new Vector(delta.getX(), 0, delta.getZ());
            Vector kb = horiz.lengthSquared() < 0.001
                    ? new Vector(0, 0, 0)
                    : horiz.normalize().multiply(powerX);
            kb.setY(powerY);
            victim.setVelocity(victim.getVelocity().add(kb));
            if (victim.equals(shooter)) continue; // 自己：只吃击退（火球跳），不掉血
            if (game.teamOf(victim.getUniqueId()) == game.teamOf(shooter.getUniqueId())) continue; // 队友免伤害（仍吃击退）
            if (damage > 0 && game.modeSettings().isDamageEnabled()) victim.damage(damage, shooter);
        }
    }

    // ------------------------------------------------------------------
    // 模式选择菜单（箱子 GUI）
    // ------------------------------------------------------------------

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof LobbyMenu.Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        ModeHandler mode = plugin.lobbyMenu().modeFromSlot(e.getSlot());
        if (mode == null) return;
        p.closeInventory();
        plugin.games().join(p, mode);
    }

    /** 防止把背包物品拖拽进模式菜单 */
    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof LobbyMenu.Holder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // 丢弃物品
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game != null) {
            if (game.state() != GameState.RUNNING) e.setCancelled(true);
            return;
        }
        if (!p.hasPermission("practiseaura.admin")) e.setCancelled(true);
    }
}
