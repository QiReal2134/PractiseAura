package dev.aura.practise.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.Arena;
import dev.aura.practise.game.Game;
import dev.aura.practise.game.GameState;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class GameManager {

    private final PractiseAuraPlugin plugin;
    private final List<Game> games = new ArrayList<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();

    public GameManager(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    public void join(Player p, ModeHandler mode) {
        Game spectating = spectatorGameOf(p.getUniqueId());
        if (spectating != null) {
            spectating.removeSpectator(p); // 排队前先退出观战
        }
        if (byPlayer.containsKey(p.getUniqueId())) {
            Msg.send(p, "join.already");
            return;
        }
        // 优先拼进等待中的同模式游戏
        for (Game game : games) {
            if (game.mode() == mode && game.state() == GameState.WAITING && !game.isFull()) {
                addTo(game, p);
                return;
            }
        }
        // 找一个有空闲点位的竞技场
        Arena arena = plugin.arenas().findFree(mode);
        if (arena == null) {
            Msg.send(p, "join.no-arena", mode.display());
            return;
        }
        Game game = new Game(plugin, arena, mode, arena.freePosition());
        game.setRounds(plugin.settings().rounds());
        games.add(game);
        addTo(game, p);
    }

    private void addTo(Game game, Player p) {
        if (!game.addPlayer(p)) {
            Msg.send(p, "join.failed");
            if (game.playersCount() == 0) game.disband();
            return;
        }
        byPlayer.put(p.getUniqueId(), game);
        plugin.lobbyMenu().rememberLastGame(p.getUniqueId(), game.mode()); // 用于快速加入
        plugin.boards().showGame(p, game);
        Msg.send(p, "join.success", game.mode().display(), game.arena().getName());
    }

    public void leave(Player p) {
        Game game = byPlayer.remove(p.getUniqueId());
        if (game == null) {
            Msg.send(p, "join.not-in-game");
            return;
        }
        game.leave(p, "game.left");
        if (game.state() == GameState.WAITING && game.playersCount() == 0) {
            game.disband();
        }
        game.resetToLobby(p);
    }

    public void handleQuit(Player p) {
        Game game = byPlayer.remove(p.getUniqueId());
        if (game == null) {
            // 不是参与者则可能是观战者
            for (Game g : games) {
                g.removeSpectatorQuiet(p.getUniqueId());
            }
            return;
        }
        game.leave(p, "game.quit");
        if (game.state() == GameState.WAITING && game.playersCount() == 0) {
            game.disband();
        }
    }

    public Game gameOf(UUID id) {
        return byPlayer.get(id);
    }

    /** 热路径快速判断：当前是否有任何游戏（避免每帧事件白做查表） */
    public boolean hasActive() {
        return !games.isEmpty();
    }

    /** 该玩家是否正在观战某场游戏 */
    public Game spectatorGameOf(UUID id) {
        for (Game game : games) {
            if (game.isSpectator(id)) return game;
        }
        return null;
    }

    /** 某竞技场当前进行中的游戏 */
    public Game runningGameAt(Arena arena) {
        for (Game game : games) {
            if (game.arena() == arena && game.state() == GameState.RUNNING) return game;
        }
        return null;
    }

    /** 该竞技场是否有未结束的对局（/pa delete 前守卫） */
    public boolean arenaInUse(Arena arena) {
        for (Game game : games) {
            if (game.arena() == arena && game.state() != GameState.ENDING) return true;
        }
        return false;
    }

    /** 该世界是否有未结束的对局（/world delete 前守卫） */
    public boolean worldInUse(String worldName) {
        if (worldName == null) return false;
        for (Game game : games) {
            if (game.state() == GameState.ENDING) continue;
            Location red = game.spawn(dev.aura.practise.game.Team.RED);
            Location blue = game.spawn(dev.aura.practise.game.Team.BLUE);
            if (red != null && worldName.equals(red.getWorld() == null ? null : red.getWorld().getName())) {
                return true;
            }
            if (blue != null && worldName.equals(blue.getWorld() == null ? null : blue.getWorld().getName())) {
                return true;
            }
        }
        return false;
    }

    /** /pa duel 接受后：直接把两人放进一场新游戏（邀请者红队，受邀者蓝队） */
    public boolean startDuel(Player a, Player b, ModeHandler mode, int rounds) {
        if (byPlayer.containsKey(a.getUniqueId()) || byPlayer.containsKey(b.getUniqueId())) {
            return false;
        }
        // 观战中的先退出观战，避免以旁观模式进不了战斗
        Game spectatingA = spectatorGameOf(a.getUniqueId());
        if (spectatingA != null) spectatingA.removeSpectator(a);
        Game spectatingB = spectatorGameOf(b.getUniqueId());
        if (spectatingB != null) spectatingB.removeSpectator(b);
        Arena arena = plugin.arenas().findFree(mode);
        if (arena == null) return false;
        Game game = new Game(plugin, arena, mode, arena.freePosition());
        game.setRounds(rounds);
        game.setTeamSize(1); // duel 固定 1v1
        games.add(game);
        if (!game.addPlayer(a)) {
            game.disband();
            return false;
        }
        if (!game.addPlayer(b)) {
            game.disband();
            return false;
        }
        byPlayer.put(a.getUniqueId(), game);
        byPlayer.put(b.getUniqueId(), game);
        plugin.lobbyMenu().rememberLastGame(a.getUniqueId(), mode);
        plugin.lobbyMenu().rememberLastGame(b.getUniqueId(), mode);
        plugin.boards().showGame(a, game);
        plugin.boards().showGame(b, game);
        return true;
    }

    public List<Game> active() {
        return games;
    }

    public void unregister(Game game) {
        games.remove(game);
        byPlayer.values().removeIf(g -> g == game);
    }

    /** 关服时立即结束所有游戏 */
    public void shutdown() {
        for (Game game : new ArrayList<>(games)) {
            game.shutdownNow();
        }
        games.clear();
        byPlayer.clear();
    }
}
