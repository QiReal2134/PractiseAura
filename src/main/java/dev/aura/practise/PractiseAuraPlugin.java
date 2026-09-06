package dev.aura.practise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.aura.practise.board.ScoreboardService;
import dev.aura.practise.command.CommandDispatcher;
import dev.aura.practise.command.QuickJoinCommand;
import dev.aura.practise.command.SubCommandAdapter;
import dev.aura.practise.command.WorldCommand;
import dev.aura.practise.command.sub.CreateSub;
import dev.aura.practise.command.sub.DeleteSub;
import dev.aura.practise.command.sub.DuelSub;
import dev.aura.practise.command.sub.GenVoidSub;
import dev.aura.practise.command.sub.GuardSub;
import dev.aura.practise.command.sub.HubSub;
import dev.aura.practise.command.sub.JoinSub;
import dev.aura.practise.command.sub.KitSub;
import dev.aura.practise.command.sub.LeaveSub;
import dev.aura.practise.command.sub.ListSub;
import dev.aura.practise.command.sub.ModeSub;
import dev.aura.practise.command.sub.SetBedSub;
import dev.aura.practise.command.sub.SetBuildSub;
import dev.aura.practise.command.sub.SetLobbySub;
import dev.aura.practise.command.sub.SetSpawnSub;
import dev.aura.practise.command.sub.SettingSub;
import dev.aura.practise.command.sub.SpectateSub;
import dev.aura.practise.command.sub.SetupSub;
import dev.aura.practise.game.PendingBed;
import dev.aura.practise.game.PendingDuel;
import dev.aura.practise.game.PendingSetting;
import dev.aura.practise.listener.GameListener;
import dev.aura.practise.manager.ArenaManager;
import dev.aura.practise.manager.GameManager;
import dev.aura.practise.manager.Settings;
import dev.aura.practise.menu.LobbyMenu;
import dev.aura.practise.util.LocUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class PractiseAuraPlugin extends JavaPlugin {

    private Settings settings;
    private ArenaManager arenaManager;
    private GameManager gameManager;
    private dev.aura.practise.manager.KitManager kitManager;
    private dev.aura.practise.manager.PlayerKitManager playerKitManager;
    private dev.aura.practise.manager.LastModeManager lastModeManager;
    private ScoreboardService boards;
    private LobbyMenu lobbyMenu;
    private Location lobby;
    // ConcurrentHashMap：pendingSettings 会被 AsyncChatEvent 的异步聊天线程读写
    private final Map<UUID, PendingBed> pendingBeds = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSetting> pendingSettings = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDuel> duelInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> duelCooldowns = new ConcurrentHashMap<>();
    /** 管理员配置模式：UUID → 正在配置的竞技场名（/pa setup 进入，/pa setup leave 退出） */
    private final Map<UUID, String> setuping = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        dev.aura.practise.util.Messages messages = new dev.aura.practise.util.Messages(this);
        messages.load();
        dev.aura.practise.util.Msg.init(messages); // 消息系统最先就绪
        dev.aura.practise.mode.ModeRegistry.refresh(this); // 读取各模式的模式级开关
        WorldCommand.loadRegistered(this); // 加载 worlds.yml 登记的自定义世界（先于竞技场）
        kitManager = new dev.aura.practise.manager.KitManager(this);
        kitManager.load(); // 先加载 kit（竞技场加载时可能做旧版 kit 迁移）
        playerKitManager = new dev.aura.practise.manager.PlayerKitManager(this);
        playerKitManager.load(); // 玩家个人 kit（对局中自动记录）
        // 延迟落盘：记录只标脏，每 30 秒批量写一次（对局内死亡路径无磁盘 I/O），关服时 onDisable 兜底
        getServer().getScheduler().runTaskTimer(this, playerKitManager::flushIfDirty, 600L, 600L);
        arenaManager = new ArenaManager(this);
        arenaManager.load();
        lastModeManager = new dev.aura.practise.manager.LastModeManager();
        lobbyMenu = new LobbyMenu(this);
        boards = new ScoreboardService(this);
        gameManager = new GameManager(this);
        lobby = LocUtil.read(getConfig(), "lobby");

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        CommandDispatcher dispatcher = new CommandDispatcher(this);
        dispatcher.register(new JoinSub());
        dispatcher.register(new LeaveSub());
        dispatcher.register(new ListSub());
        dispatcher.register(new DuelSub());
        dispatcher.register(new SpectateSub());
        dispatcher.register(new HubSub());
        dispatcher.register(new SetupSub());
        dispatcher.register(new SetLobbySub());
        dispatcher.register(new CreateSub());
        dispatcher.register(new DeleteSub());
        dispatcher.register(new SetSpawnSub());
        dispatcher.register(new SetBedSub());
        dispatcher.register(new SetBuildSub());
        dispatcher.register(new KitSub());
        dispatcher.register(new GuardSub());
        dispatcher.register(new GenVoidSub());
        dispatcher.register(new SettingSub());
        dispatcher.register(new ModeSub());
        PluginCommand cmd = getCommand("practise");
        if (cmd != null) {
            cmd.setExecutor(dispatcher);
            cmd.setTabCompleter(dispatcher);
        }
        PluginCommand bedFight = getCommand("bedfight");
        if (bedFight != null) bedFight.setExecutor(new QuickJoinCommand(this, "bedfight"));
        PluginCommand fireballFight = getCommand("fireballfight");
        if (fireballFight != null) fireballFight.setExecutor(new QuickJoinCommand(this, "fireballfight"));

        // /duel 独立顶层命令（复用 DuelSub，含 Tab 补全）
        PluginCommand duel = getCommand("duel");
        if (duel != null) {
            SubCommandAdapter duelAdapter = new SubCommandAdapter(this, new DuelSub());
            duel.setExecutor(duelAdapter);
            duel.setTabCompleter(duelAdapter);
        }
        // /world 世界管理
        PluginCommand world = getCommand("world");
        if (world != null) {
            dev.aura.practise.command.WorldCommand worldCommand =
                    new dev.aura.practise.command.WorldCommand(this);
            world.setExecutor(worldCommand);
            world.setTabCompleter(worldCommand);
        }
        // /hub 回大厅
        PluginCommand hub = getCommand("hub");
        if (hub != null) {
            SubCommandAdapter hubAdapter = new SubCommandAdapter(this, new HubSub());
            hub.setExecutor(hubAdapter);
            hub.setTabCompleter(hubAdapter);
        }

        // 每 2 秒刷新大厅记分板
        new BukkitRunnable() {
            @Override
            public void run() {
                boards.refreshLobbyBoards();
            }
        }.runTaskTimer(this, 40L, 40L);

        getLogger().info("PractiseAura 已启用！");
        if (arenaManager.all().isEmpty()) {
            getLogger().info("还没有竞技场：进服后用 /pa create <名字> <模式> 创建");
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.shutdown();
        if (playerKitManager != null) playerKitManager.flushIfDirty();
        if (arenaManager != null) arenaManager.saveAll();
        getLogger().info("PractiseAura 已关闭");
    }

    public Settings settings() {
        return settings;
    }

    public ArenaManager arenas() {
        return arenaManager;
    }

    public GameManager games() {
        return gameManager;
    }

    public ScoreboardService boards() {
        return boards;
    }

    public LobbyMenu lobbyMenu() {
        return lobbyMenu;
    }

    public dev.aura.practise.manager.LastModeManager lastModes() {
        return lastModeManager;
    }

    public dev.aura.practise.manager.KitManager kits() {
        return kitManager;
    }

    public dev.aura.practise.manager.PlayerKitManager playerKits() {
        return playerKitManager;
    }

    public Location lobby() {
        return lobby == null ? null : lobby.clone();
    }

    public Map<UUID, PendingBed> pendingBeds() {
        return pendingBeds;
    }

    public Map<UUID, PendingSetting> pendingSettings() {
        return pendingSettings;
    }

    public Map<UUID, PendingDuel> duelInvites() {
        return duelInvites;
    }

    public Map<UUID, Long> duelCooldowns() {
        return duelCooldowns;
    }

    public Map<UUID, String> setuping() {
        return setuping;
    }

    public void setLobby(Location loc) {
        this.lobby = loc.clone();
        LocUtil.write(getConfig(), "lobby", loc);
        saveConfig();
    }

    // ------------------------------------------------------------------
    // 可见性：大厅玩家互相不可见；场内（参与者/幽灵/观战）互相可见且与大厅互不可见
    // ------------------------------------------------------------------

    private boolean isGamePlayer(Player p) {
        dev.aura.practise.game.Game g = gameManager.gameOf(p.getUniqueId());
        // 排队等待中仍算大厅（人还站在大厅里）；开局后才算场内
        if (g != null && g.state() != dev.aura.practise.game.GameState.WAITING) return true;
        return gameManager.spectatorGameOf(p.getUniqueId()) != null;
    }

    private boolean isGhostPlayer(Player p) {
        dev.aura.practise.game.Game g = gameManager.gameOf(p.getUniqueId());
        return g != null && g.isGhost(p.getUniqueId());
    }

    /**
     * 玩家进出大厅/场内时调用，重算与所有人的互见关系。
     * 这是全插件唯一的 show/hide 出口：幽灵状态在这里单独成档，
     * 避免任意玩家的可见性刷新把重生等待中的幽灵重新暴露给别人。
     */
    public void updateVisibility(Player p) {
        boolean pInGame = isGamePlayer(p);
        boolean pGhost = isGhostPlayer(p);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            boolean oInGame = isGamePlayer(other);
            boolean oGhost = isGhostPlayer(other);
            if (pGhost && oGhost) {
                // 幽灵互相也看不见
                p.hidePlayer(this, other);
                other.hidePlayer(this, p);
            } else if (pGhost) {
                // 幽灵看得见别人，别人看不见幽灵
                p.showPlayer(this, other);
                other.hidePlayer(this, p);
            } else if (oGhost) {
                p.hidePlayer(this, other);
                other.showPlayer(this, p);
            } else if (pInGame || oInGame) {
                // 至少一方在场内：互相可见（大厅玩家本来也不在场边）
                p.showPlayer(this, other);
                other.showPlayer(this, p);
            } else {
                // 双方都在大厅：互相隐藏
                p.hidePlayer(this, other);
                other.hidePlayer(this, p);
            }
        }
    }
}
