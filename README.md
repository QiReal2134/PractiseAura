# PractiseAura

Paper 26.2 上的 Practice 核心插件：**BedFight / FireBallFight**（1v1 / 2v2，架构可扩展更多模式），机制参考 MinemenClub 等经典 practice 服务器。

- Java 25 · 纯 Paper/Bukkit API + Adventure，无 NMS
- ViaVersion / ViaBackwards / ViaRewind 配合下支持 **1.8.9 客户端**
- 教程见 [TUTORIAL.md](TUTORIAL.md)

## 构建

```cmd
build.cmd     # 自动用项目内 JDK25 + Maven，产物同步到 run\plugins\
```

## 玩家玩法

```
大厅 → 手持铁剑右键菜单选模式 → 排队（第9格红染料退出）
→ 满员（team-size×2）→ 图腾特效 + 传送 + 锁位倒计时（BossBar）
→ 开局发 kit → 战斗 → 死亡幽灵等待重生 → 决出胜负 → /hub 或快速加入再来一局
```

- **床机制**：床在 → 死亡重生；床毁 → 死亡淘汰；全灭对方获胜
- **守家**：开局自动发队色羊毛；`/pa guard` 记录的围床结构每局自动重放，可拆可炸
- **火球**（FireBallFight）：右键发射，威力/伤害/冷却可配，自己只吃击退不掉血，不毁方块
- **死亡**：幽灵状态（隐身无粒子+无敌+飞行+无碰撞+对他人完全隐藏）→ 重生保护 → 个人 kit 快照还原
- **血量继承**：2v2+ 队友中途退出，剩余血量转移给队友，上限翻倍（封顶 `20×队伍人数`）
- **退出判负**：对局中退出/掉线（含死亡界面）直接判对方获胜

## 命令

### 玩家

| 命令 | 说明 |
| --- | --- |
| `/pa join <模式>` | 加入排队（快捷：`/bedfight`、`/fireballfight`） |
| `/pa duel <玩家> [模式] [回合数]`（独立命令 `/duel`） | 约战，对方聊天里点接受/拒绝 |
| `/pa spectate <场名\|leave>` | 观战 / 退出 |
| `/hub`（`/lobby`） | 回到大厅（自动退观战/退对局） |
| `/pa leave`、`/pa list` | 离开 / 查询 |
| `/world list\|tp\|create\|delete` | 世界管理（create/delete 管理员） |

### 管理员（默认隐藏于 help/Tab，输入完整命令可用）

| 命令 | 说明 |
| --- | --- |
| `/pa setup <名>` | **可视化配置菜单**（点击执行，推荐入口） |
| `/pa create\|delete <名> [模式]` | 创建 / 删除竞技场 |
| `/pa setlobby` | 设置大厅 |
| `/pa setspawn <名> <red\|blue> [组号]` | 出生点（可带组号配多套点位） |
| `/pa setbed <名> <red\|blue> [组号]` | 床（左键点床） |
| `/pa setbuild <名> <pos1\|pos2>` | 可挖掘区域 |
| `/pa kit <模式> [clear]` | 按模式设置 kit（所有该模式竞技场共用） |
| `/pa guard <名> ready\|clear` | 围床结构记录/清除（自动同步两床） |
| `/pa genvoid <名> [世界名]` | 一键生成虚空地图 |
| `/pa setting <key> [值\|next\|input]` | 全局参数 |
| `/pa mode <模式> [开关] [true\|false]` | 模式开关 |

## 配置（config.yml，全部即时生效于新对局）

```yaml
settings:
  team-size: 1               # 每队人数 1-4（1v1/2v2/…），满员开局
  rounds: 1                  # 默认回合数（过半即胜；duel 可单独指定）
  countdown-seconds: 5       # 匹配后锁位倒计时
  respawn-seconds: 3         # 幽灵等待，0=立即
  spawn-protection-seconds: 2 # 重生保护，攻击即失效，0=关闭
  void-below-spawn: 12       # 虚空处死线（低于出生点Y），0=关闭
  place-limit-below-spawn: 12 # 虚空线下禁放，0=不限
  kit-blocks: 24             # 默认 kit 守家羊毛
  guard-scan-radius: 4       # 围床扫描半径
  fireball-power-x/y: 1.6/0.8 # 火球击退
  fireball-damage: 4.0       # 火球伤害（0=纯击退）
  fireball-radius: 2.5       # 火球半径
  fireball-cooldown-seconds: 1.5
  show-admin-commands: false # help/Tab 是否显示管理命令
  lobby-item: IRON_SWORD
  rejoin-item: PAPER

modes:                       # 模式级开关（/pa mode 修改）
  bedfight:
    needs-beds: true         # 有床（重生机制）
    needs-guard: true        # 启用围床
    damage: true             # false = 拳击式（有击退无伤害）
    pvp: true
    allow-break-map: false   # 允许破坏地图方块
    allow-break-placed: true # 允许拆玩家方块
    allow-place: true
    void-kill: true          # 虚空处死
```

## 添加新模式（开发者）

1. 新建类实现 `mode/ModeHandler`：`id / display / icon / needsBeds(默认读设置) / giveDefaultKit`，可选钩子 `onSecondTick / addScoreboardLines / onRightClick`
2. `ModeRegistry` 里 `register(new XxxMode());` 一行
3. 完成——菜单、匹配、图腾特效、约战、模式开关全部自动支持（示例见 `BedFightMode`，`SwordPvP` 教程示例见 TUTORIAL.md）

## 结构

```
dev.aura.practise
├── PractiseAuraPlugin        # 主类（服务装配、/hub /duel /world 注册）
├── mode/                     # ★ 模式注册表 + 模式开关（加新模式看这里）
├── game/                     # Game 主逻辑 / Arena(多点位) / ArenaPosition / BlockTracker
├── manager/                  # ArenaManager / GameManager / KitManager / Settings(缓存)
├── command/
│   ├── SubCommand            # 子命令接口
│   ├── CommandDispatcher     # 分发器（help/权限/Tab 统一处理）
│   ├── SubCommandAdapter     # 把子命令包装成独立顶层命令（/duel /hub）
│   ├── CmdUtil               # 公共工具（权限/传送/解析）
│   ├── sub/                  # 每个命令一个文件
│   ├── WorldCommand          # /world 世界管理
│   └── QuickJoinCommand      # /bedfight /fireballfight 快捷加入
├── listener/                 # 事件监听
├── menu/                     # 等待区物品 + 模式选择菜单
├── board/                    # 记分板（差异渲染）
└── util/                     # Msg / LocUtil
```
