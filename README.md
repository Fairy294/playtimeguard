# Playtime Guard（防沉迷）

Minecraft **NeoForge 26.1.2** 服务端的防沉迷模组。按星期限制进入、限制每日游玩时长、用 BossBar 实时显示剩余时间，到点自动踢出。

**所有参数都在配置文件里改，模组不提供任何游戏内指令。**

## 特性

- **豁免名单** —— 名单内的玩家完全不受限制
- **禁玩日** —— 指定星期禁止进入，登录即踢出
- **每日时长上限** —— 只统计在线时间，下线就停止累计
- **BossBar 倒计时** —— 每个玩家一条独立的条，显示自己的剩余时间
- **到点踢出** —— 时长用完自动踢出，消息可自定义
- **提前提醒** —— 剩余 N 分钟时发一次提醒
- **持久化** —— 时长写入世界目录的 JSON，服务器重启不清零
- **每日重置** —— 可配置重置时刻（默认凌晨 4 点）

## 安装

1. 把 `playtimeguard-*.jar` 放进服务端的 `mods/` 目录
2. 启动服务器，首次运行会生成配置文件
3. 编辑 `config/playtimeguard-server.toml`
4. 重启服务器（或重载配置）

## 配置

配置文件位置：**`config/playtimeguard-server.toml`**

```toml
[general]
    # 完全不受禁玩日、时长和 BossBar 限制的玩家名列表，不区分大小写。
    exemptPlayers = ["CMC4624"]

    # 禁止游玩的星期列表，使用 MONDAY 到 SUNDAY；空列表表示不禁止。
    blockedDays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"]

    # 每个非豁免玩家每天最多游玩的分钟数，范围 1 到 1440。
    dailyLimitMinutes = 120

    # 剩余多少分钟时提醒一次，0 表示不提醒，范围 0 到 120。
    warnBeforeMinutes = 10

    # 服务器本地时间每天几点重置时长，范围 0 到 23。
    resetHour = 4

[display]
    # 是否给非豁免玩家显示剩余游玩时长 BossBar。
    bossBarEnabled = true

    # BossBar 文本模板。
    bossBarText = "今日剩余：%remaining%"

    # BossBar 颜色：PINK、BLUE、RED、GREEN、YELLOW、PURPLE 或 WHITE。
    bossBarColor = "RED"

    # 是否让 BossBar 进度随剩余时间递减；关闭后显示满格静态条。
    bossBarShowProgress = true

[messages]
    # 今日时长用完时的踢出消息。
    kickTimeUpMessage = "今日游玩时间已用完，明天再来吧。"

    # 命中禁玩日时的踢出消息。
    kickBlockedDayMessage = "今天（%day%）不允许游玩，明天见。"

    # 剩余时长提醒消息。
    warnMessage = "注意：今日剩余游玩时间仅剩 %remaining%。"

[storage]
    # 世界目录中保存游玩时长的 JSON 文件名。
    dataFileName = "playtimeguard.json"
```

### 占位符

消息和 BossBar 文本都支持这些占位符：

| 占位符 | 含义 | 示例 |
|---|---|---|
| `%remaining%` | 今日剩余时长 | `1小时23分` |
| `%used%` | 今日已用时长 | `37分` |
| `%player%` | 玩家名 | `Steve` |
| `%day%` | 今天的星期（中文） | `星期一` |
| `%limit%` | 每日上限 | `2小时0分` |

### 时长格式

- 超过 1 小时 → `1小时23分`
- 不足 1 小时 → `23分`
- 不足 1 分钟 → `45秒`

## 数据存储

游玩时长保存在世界目录下的 `playtimeguard.json`：

```json
{
  "entries": {
    "<玩家UUID>": { "date": "2026-09-26", "ticks": 72000 }
  }
}
```

- 玩家下线时保存
- 服务器停服时保存
- 运行中每分钟额外落盘一次
- 读取时如果记录日期不是今天，视为 0

**跨天判定用 `resetHour`**：比如设为 4，那么凌晨 3 点还算"昨天"，4 点之后才算新的一天。

## 构建

```bash
./gradlew build
```

产物在 `build/libs/`。

CI（GitHub Actions）会在每次推送到 `main` 时自动构建并上传 jar 产物；
推送 `v*` 格式的 tag 时会自动创建 GitHub Release。

## 行为说明

### 登录时
- 豁免玩家 → 不做任何检查
- 命中禁玩日 → 立刻踢出，发送 `kickBlockedDayMessage`
- 今日时长已用完 → 立刻踢出，发送 `kickTimeUpMessage`
- 否则 → 注册 BossBar，开始计时

### 游戏中
- 每 tick 累计在线时间（按 20 tick = 1 秒折算）
- 每秒更新一次 BossBar 显示
- 剩余时长跨过 `warnBeforeMinutes` 阈值时发一次提醒（只提醒一次，不会反复刷）
- 剩余归零 → 踢出

### 下线时
- 结算未凑满一秒的零头，避免反复上下线刷时长
- 保存到磁盘
- 移除该玩家的 BossBar

## 兼容性

- Minecraft 26.1.2
- NeoForge 26.1.2.84 及以上
- 需要 Java 25
- **纯服务端模组**，客户端不需要安装

## 许可

MIT
