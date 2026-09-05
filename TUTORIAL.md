# PractiseAura 使用与开发教程

> Paper 26.2 practice 核心插件：BedFight / FireBallFight（1v1/2v2，可扩展更多模式）
> Java 25 · 无 NMS · 支持 1.8.9 客户端（经 Via）
> 配套文档：[README.md](README.md)（命令表/配置表/结构速查）

---

## 第 1 章 环境准备与构建

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| JDK | **25**（编译和运行都要） | 项目 `tools\jdk-25.0.4.1` 已自带 |
| Maven | 3.9+ | 项目 `tools\apache-maven-3.9.9` 已自带 |
| Paper 服务器 | 26.2 | `run\paper.jar` 已下载好 |

> ⚠️ Paper 26.2 是 2026 新版本号方案，**必须 Java 25**，系统默认 JAVA_HOME（JDK 17）不可用。

**构建**：双击根目录 `build.cmd`，看到 `BUILD SUCCESS` 即成功，产物自动同步到 `run\plugins\`。

**开服**：双击 `run\start-server.cmd`，看到 `[PractiseAura] PractiseAura 已启用！` 即成功。

**连接**：本机 `localhost`；局域网 `你的IP:25565`（连不上就开防火墙 25575/25565）。

**1.8.9 客户端**：`plugins/` 放 ViaVersion + ViaBackwards + ViaRewind 三个插件，服务端保持 26.2。

---

## 第 2 章 第一次配置（管理员，10 分钟）

进服先拿 OP（控制台 `op 名字`，或 `run\rcon.ps1 "op 名字"`）。

### 2.1 世界与大厅

```
/world create game_void void   ← 建一个虚空世界（可选，也可用主世界）
/pa setlobby                   ← 站在大厅位置执行
```

### 2.2 创建地图（三种方式）

```
/pa genvoid void1 game_void    ← 方式一：一键生成虚空地图（自动配好床和出生点）
/pa create bf1 bedfight        ← 方式二：手动建场
/pa setup bf1                  ← 方式三：菜单里也能创建流程引导
```

### 2.3 可视化配置菜单

```
/pa setup bf1
```

聊天里出现**可点击菜单**，逐项点击完成配置：

```
=== bf1 [BedFight] 配置 ===
状态: 就绪 ✔
点位组数: 1（同图可并发 1 场）
  点位 1: 出生点✔ 床✔
[大厅] [红队出生点] [蓝队出生点] [红队的床] [蓝队的床]   ← 站到位点击执行
[挖掘区 pos1/pos2]                                      ← 可选
[记录围床并同步两床] / [清除围床]                          ← 可选
=== 全局设置 ===
[虚空处死] [重生等待] [重生保护] [火球×4] [回合数] [队伍规模] ...
   ↑ 点击后按提示在聊天栏输入数字（30 秒内，[点击取消] 可撤）
```

- ✔ 绿 = 已设置；悬停有提示，**点击即执行**
- 命令也可以手打，`Tab` 补全全程可用

### 2.4 多组点位（同图多场并发）

```
/pa setspawn bf1 red 2    ← 组号默认 1；带 2 即编辑第二组
/pa setspawn bf1 blue 2
/pa setbed bf1 red 2      ← 左键点第二组的红床
/pa setbed bf1 blue 2
```

配 N 组 = 这张图同时跑 N 场。菜单会逐组显示 `点位 N: 出生点✔ 床✔`。

### 2.5 挖掘区（可选）

默认地图方块全保护。开放部分地图（如中央岛）：

```
/pa setbuild bf1 pos1    # 站矩形一角
/pa setbuild bf1 pos2    # 站对角
```

区域内地图方块可拆/放/炸，局末自动回滚。也可以用模式开关 `allow-break-map` 全图开放。

### 2.6 Kit（按模式）

1. 穿好装备、放好物品
2. `/pa kit bedfight`
3. `/pa kit bedfight clear` 恢复默认

kit 挂在**模式**上（所有该模式竞技场共用）；羊毛→自动队色，皮革→自动染色。守家羊毛默认自动发（`kit-blocks`）。

### 2.7 围床结构（可选）

1. 在第 1 组红床周围摆防护方块（半径默认 4 格）
2. `/pa guard bf1 ready` → 自动镜像到蓝床；每局重放、局中可拆炸

### 2.8 模式开关

```
/pa mode bedfight                 ← 查看全部开关
/pa mode bedfight needs-beds false
/pa mode fireballfight damage false
```

| 开关 | 作用 |
| --- | --- |
| needs-beds | 有床（重生机制）；false=一条命 |
| needs-guard | 启用围床重放 |
| damage | false = 拳击式（有击退无伤害，靠虚空淘汰） |
| pvp | 允许攻击玩家 |
| allow-break-map | 允许破坏地图方块（无需挖掘区） |
| allow-break-placed | 允许拆玩家方块 |
| allow-place | 允许放方块 |
| void-kill | 虚空处死 |

组合示例：**boxing** = `needs-beds=false, damage=false, needs-guard=false, allow-place=false`；**2v2 保床战** = `team-size 2` + bedfight 默认开关。

---

## 第 3 章 玩家玩法

### 加入与匹配

大厅手持铁剑右键 → 菜单点模式图标 → 排队（第 9 格红染料退出）。
满员（`team-size`×2）→ **图腾特效**（模式图标从头顶升起旋转 2.5 秒）→ 传送 + 锁位 → BossBar 倒计时 → 开局。

### 约战与观战

```
/duel 玩家 fireballfight 3    ← 3 局制约战；对方聊天点 [接受]/[拒绝]
/spectate bf1                 ← 观战；/pa spectate leave 退出
```

### 死亡与重生

- 床在：幽灵状态（隐身无粒子/无敌/飞行/无碰撞/对他人隐藏）等待 `respawn-seconds` → 满状态重生 + 2 秒保护（攻击即失效）
- 床毁：淘汰旁观
- 死亡瞬间快照背包 → 重生还原你调整后的样子
- 2v2 队友退出：血量继承（上限翻倍封顶 40）

### 结束

先胜过半回合（或单局）决出胜负 → 播报总比分 + MVP → 5 秒后回大厅 → 快速加入纸。

### 回大厅

`/hub`（`/lobby`）：观战自动退、对局自动退（按判负规则）、纯回大厅传送。

---

## 第 4 章 配置

所有配置三种改法：`/pa setup` 菜单点击输入 / `/pa setting <key> <值>` / 手改 config.yml（需重启）。
全部 key 与默认值见 [README.md](README.md) 配置表；模式级开关用 `/pa mode`。

---

## 第 5 章 开发者：添加新模式

### 5.1 实现 ModeHandler

```java
package dev.aura.practise.mode;

import dev.aura.practise.game.Game;
import dev.aura.practise.game.Team;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SwordMode implements ModeHandler {

    private final ModeSettings settings = new ModeSettings()
            .setNeedsBeds(false)      // 无床：死一次淘汰
            .setNeedsGuard(false)
            .setDamageEnabled(true)
            .setPvp(true)
            .setAllowBreakMap(false)
            .setAllowBreakPlaced(false)
            .setAllowPlace(false)
            .setVoidKill(true);

    @Override public ModeSettings settings() { return settings; }
    @Override public String id() { return "sword"; }
    @Override public String display() { return "SwordPvP"; }
    @Override public Material icon() { return Material.IRON_SWORD; }

    @Override
    public void giveDefaultKit(Game game, Player p, Team team) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, new ItemStack(Material.IRON_SWORD));
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.IRON_BOOTS));
    }
}
```

### 5.2 注册

`ModeRegistry` 的 static 块：`register(new SwordMode());`

完成。菜单/匹配/图腾特效/约战/模式开关全部自动支持。

### 5.3 进阶钩子

| 钩子 | 用途 |
| --- | --- |
| `onSecondTick(Game)` | 每秒逻辑（补给/计时） |
| `addScoreboardLines(Game, List)` | 侧边栏自定义行 |
| `onRightClick(Game, Player)` | 右键机制（返回 true 拦截事件），参考 `FireballFightMode` |

### 5.4 快捷命令（可选）

`plugin.yml` 加命令声明 + `PractiseAuraPlugin` 注册 `QuickJoinCommand(this, "sword")`。

### 5.5 加子命令（可选）

实现 `SubCommand` 接口（name/description/execute/tab），`PractiseAuraPlugin` 里 `dispatcher.register(new XxxSub())`——help/权限/Tab 自动集成。

---

## 第 6 章 FAQ

| 问题 | 答案 |
| --- | --- |
| 死亡/回大厅闪"正在加载" | 已做同位置免传送；仍出现则是必要位移（幽灵拉回/观战传送） |
| 火球炸不死人 | `fireball-damage` 为 0（纯击退），`/pa setting` 调整 |
| 床拆不掉 | 须敌方玩家游戏内手拆；自己床会拒绝 |
| 改了 config 不生效 | 用 `/pa setting`（即时生效）或重启 |
| Tab 补全没反应 | 重连服务器刷新命令树；确认插件启用成功 |
| 管理命令 Tab 看不见 | `show-admin-commands: false`，输全命令仍可用 |
| 朋友连不上 | 防火墙 25565；外网需端口映射 |
| 血条变长了 | 2v2 队友退出的血量继承，重生后恢复 |

---

## 附：目录速查

```
PractiseAura/
├── build.cmd / run\start-server.cmd / run\rcon.ps1   # 构建 / 开服 / 控制台
├── tools/                                             # 自带 JDK25 + Maven
├── run/                                               # 测试服
├── TUTORIAL.md / README.md
└── src/main/java/dev/aura/practise/
    ├── PractiseAuraPlugin        # 主类装配
    ├── mode/                     # ★ 模式注册表（加新模式）
    ├── game/                     # Game / Arena(多点位) / ArenaPosition / BlockTracker
    ├── manager/                  # Arena / Game / Kit / Settings(缓存)
    ├── command/                  # SubCommand 框架 + CmdUtil + sub/ 每命令一文件
    ├── listener/                 # 事件监听
    ├── menu/  board/  util/
```
