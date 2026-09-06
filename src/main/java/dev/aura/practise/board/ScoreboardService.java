package dev.aura.practise.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.game.Game;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** 简单侧边栏记分板（大厅 + 游戏内） */
public class ScoreboardService {

    /** 每行用一个不可见的颜色代码作为条目，保证行内容可重复且可更新 */
    private static final String[] ENTRIES = {
            "\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74", "\u00a75", "\u00a76", "\u00a77",
            "\u00a78", "\u00a79", "\u00a7a", "\u00a7b", "\u00a7c", "\u00a7d", "\u00a7e"
    };

    private final PractiseAuraPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** 上次渲染的行内容，内容没变就跳过重绘 */
    private final Map<UUID, List<String>> lastLines = new HashMap<>();

    public ScoreboardService(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    public void showLobby(Player p) {
        render(p, lobbyLines());
    }

    public void showGame(Player p, Game game) {
        render(p, game.scoreboardLinesFor(p));
    }

    public void updateGame(Game game) {
        // 只遍历本局观众（参与者+观战者），不去全服扫描；行内容按查看者构建（含个人击杀数）
        for (UUID id : game.scoreboardViewers()) {
            if (!boards.containsKey(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) render(p, game.scoreboardLinesFor(p));
        }
    }

    /** 每 2 秒刷新一次大厅记分板；大厅行内容与查看者无关，整个扫描轮只构建一次 */
    public void refreshLobbyBoards() {
        List<String> lines = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.games().gameOf(p.getUniqueId()) != null || !boards.containsKey(p.getUniqueId())) {
                continue;
            }
            if (lines == null) lines = lobbyLines();
            render(p, lines);
        }
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        lastLines.remove(p.getUniqueId());
    }

    /** 大厅行内容与查看者无关；模板在 messages.yml 的 scoreboard.* 键 */
    private List<String> lobbyLines() {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(Msg.text("scoreboard.lobby-online", Bukkit.getOnlinePlayers().size()));
        lines.add("");
        for (ModeHandler mode : ModeRegistry.all()) {
            lines.add(Msg.text("scoreboard.lobby-join", mode.id()));
        }
        return lines;
    }

    private void render(Player p, List<String> lines) {
        // 内容没变化就不重绘（每秒 tick 调用，省掉无谓的组件构建）
        List<String> previous = lastLines.get(p.getUniqueId());
        if (previous != null && previous.equals(lines)) return;
        lastLines.put(p.getUniqueId(), new ArrayList<>(lines));
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(p.getUniqueId(), board);
        }
        Objective obj = board.getObjective("practise");
        if (obj == null) {
            obj = board.registerNewObjective("practise", "dummy",
                    Msg.legacy(Msg.text("scoreboard.title")));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        int used = Math.min(lines.size(), ENTRIES.length);
        for (int i = 0; i < ENTRIES.length; i++) {
            String entry = ENTRIES[i];
            if (i < used) {
                org.bukkit.scoreboard.Team lineTeam = board.getTeam("line" + i);
                if (lineTeam == null) {
                    lineTeam = board.registerNewTeam("line" + i);
                    lineTeam.addEntry(entry);
                }
                // Msg.legacy 解析 & 颜色码：scoreboard.* 消息键可以带颜色
                lineTeam.prefix(Msg.legacy(lines.get(i)));
                obj.getScore(entry).setScore(ENTRIES.length - i);
            } else {
                board.resetScores(entry);
            }
        }
        p.setScoreboard(board);
    }
}
