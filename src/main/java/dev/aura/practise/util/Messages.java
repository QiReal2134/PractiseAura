package dev.aura.practise.util;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.aura.practise.PractiseAuraPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 消息管理器：所有玩家可见消息集中在 messages.yml，用户可自行修改。
 * - 文本支持 & 颜色码（§ 也兼容）
 * - 支持 {placeholder} 占位符
 * - 插件升级新增的消息键会自动合并进现有文件，不覆盖用户已改的内容
 */
public class Messages {

    private final PractiseAuraPlugin plugin;
    private final Map<String, String> cache = new LinkedHashMap<>();
    private File file;

    public Messages(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
    }

    /** 默认消息（键 → 文本）。修改默认文案改这里。 */
    private static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("prefix", "&8[&bAura&8] ");
        d.put("help.header", "&b--- PractiseAura ---");
        d.put("help.entry", "&f/pa {name}{params} &8- &7{desc}");
        d.put("world.create-usage", "&7用法: /world create <名> [void|flat|normal]");
        d.put("duel.none", "&7你没有待处理的约战邀请");
        d.put("guard.bad-action", "&c参数只能填 ready 或 clear");
        d.put("setting.input-expired", "&c输入已超时，请重新点击设置项");
        d.put("place.mode-denied", "&c该模式不允许放置方块");
        // 通用
        d.put("error.player-only", "&c该命令仅玩家可用");
        d.put("error.no-permission", "&c你没有权限");
        d.put("error.arena-missing", "&c竞技场 {arena} 不存在");
        d.put("error.unknown-mode", "&c未知模式: {input}（可选 {modes}）");
        d.put("error.modes", "&7可用模式: {modes}");
        d.put("setup.world-teleported", "&7已自动传送到地图所在世界 {world}，请就位后重新执行命令");
        // 加入 / 排队
        d.put("join.welcome", "&7欢迎来到 &bPractiseAura&7！");
        d.put("join.hint", "&7手持游戏菜单物品右键选择模式");
        d.put("join.usage", "&7用法: /pa join <{modes}>");
        d.put("join.already", "&c你已经在游戏中了，先用 /pa leave 退出");
        d.put("join.no-arena", "&c暂时没有可用的 {mode} 竞技场，请联系管理员");
        d.put("join.failed", "&c加入失败，游戏已满或已开始");
        d.put("join.success", "&7已加入 &f{mode} &7[&f{arena}&7]");
        d.put("join.not-in-game", "&7你当前不在任何游戏中");
        d.put("game.joined", "&f{name} &7加入了排队 (&f{count}&7/&f{max}&7)");
        d.put("game.left", "&f{name} &7离开了游戏");
        d.put("game.quit", "&f{name} &7退出了游戏");
        // 匹配 / 倒计时 / 回合
        d.put("match.matched", "&a匹配成功！正在传送至竞技场...");
        d.put("match.countdown", "&7游戏将在 &f{seconds} &7秒后开始！");
        d.put("match.cancelled", "&c人数不足，倒计时已取消");
        d.put("match.count-title", "&e{seconds}");
        d.put("match.count-sub", "&7{mode} 即将开始");
        d.put("match.round-title", "&e第 {round} 局");
        d.put("match.round-bar", "&e第 {round} 局倒计时");
        d.put("match.count-bar", "&e游戏开始倒计时");
        d.put("match.start-title", "&a开始!");
        d.put("match.start-sub", "&e{mode}");
        d.put("game.round-start", "&a第 {round} / {total} 局开始！");
        d.put("game.round-won", "{team} &a拿下第 {round} 局！（比分 &f{red}&7:&f{blue}&7）");
        // 战斗 / 死亡
        d.put("game.killed", "&f{victim} &7被 &f{killer} &7击杀了！");
        d.put("game.died", "&f{victim} &7死了");
        d.put("game.death-message", "&7{player} 死了");
        d.put("game.death-title", "&c你死了");
        d.put("game.death-sub", "&7即将重生");
        d.put("game.elim-title", "&c你被淘汰了");
        d.put("game.elim-sub", "&7等待游戏结束");
        d.put("game.draw", "&e平局！");
        d.put("game.win", "{team} &e获胜！");
        d.put("game.score", "&7总比分 红 {red} : {blue} 蓝");
        d.put("game.mvp", "&6MVP: &f{mvp}");
        d.put("game.end-title", "{team} &e获胜!");
        d.put("game.end-title-draw", "&e平局");
        d.put("game.end-sub", "&7游戏结束");
        d.put("bed.destroyed", "&f{player} &7摧毁了 {team}&7的床！");
        d.put("bed.destroyed-title", "&c床被摧毁!");
        d.put("bed.destroyed-sub", "&7死亡后无法重生");
        d.put("bed.enemy-title", "&a摧毁敌方床!");
        d.put("bed.enemy-sub", "&7{team} 无法重生");
        d.put("bed.own", "&c不能破坏自己队伍的床！");
        d.put("game.health-inherited", "&f{mate} &7继承了 &f{quitter} &7的血量（上限 &f{max}&7）");
        d.put("ghost.wait-title", "&b重生 {seconds}s");
        d.put("ghost.wait-sub", "&7等待复活（幽灵状态）");
        d.put("ghost.respawn-title", "&a重生!");
        d.put("ghost.respawn-sub", "&7保护你的床");
        d.put("lobby.returned", "&7已返回大厅");
        d.put("lobby.missing", "&c大厅未设置，请管理员先 /pa setlobby");
        d.put("hub.returned", "&7已回到大厅");
        // 放置 / 火球
        d.put("place.denied", "&c虚空线下不能放置方块");
        d.put("fireball.cooldown", "&7火球冷却中（{seconds} 秒）");
        // 观战
        d.put("spectate.started", "&f{player} &7开始观战");
        d.put("spectate.hint", "&7正在观战 {arena} &7，输入 /pa spectate leave 退出");
        d.put("spectate.ended", "&7观战结束，已返回大厅");
        d.put("spectate.usage", "&7用法: /pa spectate <场名|leave>");
        d.put("spectate.not-spectating", "&7你当前没有在观战");
        d.put("spectate.in-game", "&c你在游戏中，不能观战");
        d.put("spectate.no-running", "&c该竞技场当前没有进行中的游戏");
        d.put("spectate.already", "&e你已经在观战这场游戏了");
        // 约战
        d.put("duel.usage", "&7用法: /pa duel <玩家> [模式] [回合数] 或 /pa duel accept|deny");
        d.put("duel.not-online", "&c玩家 {target} 不在线");
        d.put("duel.self", "&c不能约战自己");
        d.put("duel.in-game", "&c你或对方正在游戏中");
        d.put("duel.target-pending", "&c{target} 已有一个待处理的约战邀请，请稍后再试");
        d.put("duel.cooldown", "&7发送太频繁，{seconds} 秒后再试");
        d.put("duel.sent", "&7已向 &f{target} &7发起 &b{mode} &7约战（{info}30 秒内有效）");
        d.put("duel.rounds-info", "{rounds} 局制，");
        d.put("duel.invite", "{player} 邀请你进行 {mode}{rounds-info} 对决！ ");
        d.put("duel.accept-btn", "&a[接受]");
        d.put("duel.deny-btn", "&c[拒绝]");
        d.put("duel.accept-hover", "&a接受约战");
        d.put("duel.deny-hover", "&c拒绝约战");
        d.put("duel.expired", "&c该约战邀请已过期");
        d.put("duel.expired-notice", "&7{player} 的约战邀请已过期");
        d.put("duel.inviter-offline", "&c邀请你的玩家已下线");
        d.put("duel.denied-inviter", "&7{player} 拒绝了你的约战");
        d.put("duel.denied", "&7已拒绝 {player} 的约战");
        d.put("duel.accepted-inviter", "&a约战已接受，即将开始 {mode}！");
        d.put("duel.accepted", "&7已接受 {player} 的约战！");
        d.put("duel.no-arena", "&c开始失败：没有空闲竞技场");
        d.put("duel.accept-no-arena", "&c{player} 接受了约战，但没有空闲竞技场");
        // 管理：竞技场
        d.put("setup.usage", "&7用法: /pa setup <名字>");
        d.put("setup.in-use-hint", "&7提示: 该竞技场有进行中的对局，本次修改对下一局生效");
        d.put("setup.lobby-set", "&7已把当前位置设为大厅出生点");
        d.put("setup.kit-hint", "&7kit 为按模式设置: /pa kit <模式> [clear]（对该模式所有竞技场生效）");
        d.put("setup.positions", "&7点位组数: {count}（同图可并发 {count} 场；setspawn/setbed 可带组号）");
        d.put("setup.position-line", "&7  点位 {index}: 出生点 {spawns} 床 {beds}{incomplete}");
        d.put("create.usage", "&7用法: /pa create <名字> <{modes}>");
        d.put("create.bad-name", "&c名字只能包含字母、数字、下划线、短横线（1-24 位）");
        d.put("create.exists", "&c竞技场 {name} 已存在");
        d.put("create.success", "&7已创建竞技场 &f{name} &7[&f{mode}&7]");
        d.put("create.hint", "&7下一步: 输入 /pa setup {name} 打开可视化配置菜单（点击即可执行）");
        d.put("delete.usage", "&7用法: /pa delete <名字>");
        d.put("delete.success", "&7已删除竞技场 {name}");
        d.put("delete.in-use", "&c竞技场 {arena} 有未结束的对局，等比赛结束再删除");
        d.put("setspawn.usage", "&7用法: /pa setspawn <名字> <red|blue> [组号]（站在出生点执行）");
        d.put("setspawn.bad-team", "&c队伍只能填 red 或 blue");
        d.put("setspawn.success", "&7已把当前位置设为 {arena} 第 {group} 组 {team} 出生点");
        d.put("setbed.usage", "&7用法: /pa setbed <名字> <red|blue> [组号]，然后左键点击那张床");
        d.put("setbed.pending", "&7请在 60 秒内左键点击要作为第 {group} 组 {team} 的床");
        d.put("setbed.done", "&7已设置 {arena} 第 {group} 组 {team} 的床");
        d.put("setbuild.usage", "&7用法: /pa setbuild <名字> <pos1|pos2>（站在对角点执行）");
        d.put("setbuild.bad-pos", "&c参数只能填 pos1 或 pos2");
        d.put("setbuild.success", "&7已把当前位置设为 {arena} 的可建设区域 pos{index}");
        d.put("setbuild.need-other", "&7还需要再设置另一个对角点");
        d.put("setbuild.done", "&a可建设区域已生效（矩形框）");
        d.put("guard.usage", "&7用法: /pa guard <名字> ready|clear");
        d.put("guard.beds-missing", "&c先设置好第 1 组两队的床（/pa setbed）再记录围床");
        d.put("guard.disabled-hint", "&7提示: 模式 {mode} 未启用围床（/pa mode {id} needs-guard true 可开启），记录仍会保存");
        d.put("guard.empty", "&c红队的床周围 {radius} 格内没有可记录的方块，先放好防护方块");
        d.put("guard.recorded", "&7已记录围床结构 {count} 个方块");
        d.put("guard.sync", "&7蓝床同步: 放置 {placed} 个{skipped}");
        d.put("guard.sync-skipped", "，跳过被占用的 {skipped} 个");
        d.put("guard.replay-hint", "&7每局开始会自动在两张床周围重新放置，游戏中可拆可炸");
        d.put("guard.cleared", "&7已清除 {arena} 的围床结构");
        d.put("genvoid.usage", "&7用法: /pa genvoid <名字> [世界名]");
        d.put("genvoid.world-missing", "&c世界不存在");
        d.put("genvoid.exists", "&c竞技场 {name} 已存在");
        d.put("genvoid.success", "&7虚空地图已生成: {name}（两岛位于 {world} 的 X=-{distance}/+{distance}，Y={height}）");
        d.put("genvoid.hint", "&7出生点与床已自动配置，/pa list 确认就绪即可开玩；记得设置 /pa setlobby");
        d.put("genvoid.teleported", "&7已传送到地图");
        d.put("kit.usage", "&7用法: /pa kit <模式> [clear]");
        d.put("kit.saved", "&7已把当前背包+盔甲存为 {mode} 的 kit");
        d.put("kit.shared", "&7该模式所有竞技场共用此 kit；羊毛/皮革发放时会自动变成队伍颜色");
        d.put("kit.cleared", "&7已清除 {mode} 的 kit，恢复默认");
        // 设置 / 模式开关
        d.put("setting.keys", "&7可设置项: {keys}");
        d.put("setting.usage", "&7用法: /pa setting <key> [值|next|input]");
        d.put("setting.unknown-key", "&c未知设置项: {key}");
        d.put("setting.bad-number", "&c值必须是数字: {value}");
        d.put("setting.negative", "&c数值不能为负数");
        d.put("setting.applied", "&7已设置 {key} = {value}（立即生效）");
        d.put("setting.cancelled", "&7已取消设置输入");
        d.put("setting.input-prompt", "&e请在聊天栏输入 {key} 的新数值（30 秒内有效）");
        d.put("setting.cancel-btn", "&c[点击取消]");
        d.put("setting.cancel-hover", "&7取消本次输入");
        d.put("setting.console", "&7控制台请用 /pa setting <key> <值>");
        d.put("duel.bad-rounds", "&c回合数必须是数字（1-5）");
        d.put("mode.usage", "&7用法: /pa mode <模式> [开关] [true|false]");
        d.put("mode.modes", "&7可用模式: {modes}");
        d.put("mode.unknown-mode", "&c未知模式: {input}（可选 {modes}）");
        d.put("mode.unknown-flag", "&c未知开关: {flag}（可选 {flags}）");
        d.put("mode.bad-value", "&c值只能是 true 或 false");
        d.put("mode.applied", "&7已设置 {mode} 的 {flag} = {value}（立即生效，影响之后开局的对局）");
        // /world
        d.put("world.help-header", "&b--- 世界管理 ---");
        d.put("world.help-list", "&7/world list 查看所有世界");
        d.put("world.help-create", "&7/world create <名> [void|flat|normal] 创建世界（管理员）");
        d.put("world.help-tp", "&7/world tp <名> 传送到世界");
        d.put("world.help-delete", "&7/world delete <名> 删除世界（管理员）");
        d.put("world.list-header", "&b=== 世界 ===");
        d.put("world.list-entry", "&7- {name} &8({type}, 玩家 {players})");
        d.put("world.exists", "&c世界 {name} 已存在");
        d.put("world.created", "&7世界 {name}（{type}）已创建，原版命令可直接使用");
        d.put("world.teleported-to-you", "&7已传送过去");
        d.put("world.create-fail", "&c世界创建失败");
        d.put("world.bad-type", "&c类型只能是 void / flat / normal");
        d.put("world.tp-usage", "&7用法: /world tp <名>");
        d.put("world.missing", "&c世界 {name} 不存在");
        d.put("world.teleported", "&7已传送到世界 {name}");
        d.put("world.delete-usage", "&7用法: /world delete <名>");
        d.put("world.delete-protected", "&c不能删除主世界");
        d.put("world.delete-in-use", "&c世界 {world} 有未结束的对局，无法删除");
        d.put("world.delete-fail", "&c世界卸载失败（可能有插件占用）");
        d.put("world.deleted", "&7世界 {name} 已卸载并删除");
        d.put("world.delete-folder-fail", "&c世界已卸载，但文件夹删除失败: {error}");
        // 列表
        d.put("list.arenas-header", "&b=== 竞技场 ===");
        d.put("list.arenas-empty", "&7（空，用 /pa create 创建）");
        d.put("list.games-header", "&b=== 游戏 ===");
        d.put("list.games-empty", "&7（空）");
        d.put("menu.title", "&8选择模式");
        d.put("menu.item-title", "&b游戏菜单");
        d.put("menu.item-lore-1", "&7右键打开模式选择");
        d.put("menu.mode-lore-1", "&7点击加入排队");
        d.put("menu.mode-lore-2", "&82 人即可开局");
        d.put("queue.dye-title", "&c退出排队");
        d.put("queue.dye-lore", "&7右键退出当前排队");
        d.put("rejoin.title", "&a快速加入: {mode}");
        d.put("rejoin.lore", "&7右键加入上次的模式");
        d.put("list.arena-entry", "&f{name} &7[&f{mode}&7] {status}{hint}");
        d.put("list.game-entry", "&7{desc}");
        return d;
    }

    public synchronized void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            writeDefaults();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cache.clear();
        String prefix = cfg.getString("prefix", null);
        if (prefix != null) cache.put("prefix", prefix);
        var sec = cfg.getConfigurationSection("messages");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String value = sec.getString(key);
                if (value != null) cache.put(key, value);
            }
        }
        // 合并：插件升级新增的键补进文件（不覆盖用户已改内容）
        boolean changed = false;
        for (Map.Entry<String, String> entry : defaults().entrySet()) {
            if (!cache.containsKey(entry.getKey())) {
                cache.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) save();
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("prefix", cache.getOrDefault("prefix", defaults().get("prefix")));
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (entry.getKey().equals("prefix")) continue;
            cfg.set("messages." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存 messages.yml 失败: " + ex.getMessage());
        }
    }

    private void writeDefaults() {
        cache.putAll(defaults());
        save();
    }

    /** 取消息文本（缺失时回退内置默认，再缺失返回键名） */
    public String get(String key) {
        String value = cache.get(key);
        if (value != null) return value;
        return defaults().getOrDefault(key, key);
    }

    /** 取原始文本（不套占位符），供 BossBar 等旧式 API 使用 */
    public String raw(String key) {
        return get(key);
    }
}
