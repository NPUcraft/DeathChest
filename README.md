# DeathChest

Paper 1.21.8 死亡箱插件。玩家死亡后按配置扣费，把本次掉落放进死亡箱：先私人保护，到期公开，再超时则箱子消失、剩余物品掉在地上。

项目：[npucraft.com](https://npucraft.com)

## 环境

| 项目 | 要求 |
| --- | --- |
| 服务端 | Paper 1.21.8 |
| Java | 21 |
| 构建 | Maven |
| 主类 | `com.npucraft.deathchest.DeathChestPlugin` |

可选依赖（softdepend，未安装则跳过对应功能）：Vault + 经济实现、CoinsEngine / ExcellentEconomy、PlaceholderAPI、Residence。已启用经济但 Vault/CoinsEngine 不可用时，不会创建免费保护箱，物品按原版掉落。

```bash
mvn test
mvn package
```

需要跳过测试时再用 `mvn -DskipTests package`。

产物：`target/DeathChest-1.0.0.jar`（已包含 SQLite 与 MySQL 驱动）。放入 `plugins/` 后启动，会生成：

- `plugins/DeathChest/config.yml`
- `plugins/DeathChest/message_zh.yml`
- `plugins/DeathChest/deathchest.db`（默认 SQLite）
- `plugins/DeathChest/uptime.yml`（`PAUSE_OFFLINE` 计时用，不要手动删）

配置项说明写在 `config.yml` 注释里。聊天和全息文案在 `message_zh.yml`，由 `language` 指定；指定的语言文件不存在时使用 `zh`。

尚未正式发布。测试时请删除旧的 `deathchest.db`（或对应 MySQL 表）再启动，不要沿用旧库。

## 死亡时会发生什么

1. KeepInventory 为 true、或没有 `deathchest.use`、或 `general.enabled` / `player-settings.default-enabled` 为 false：不创建死亡箱。
2. 只处理 `PlayerDeathEvent.getDrops()`，不会再复制一份整包。消失诅咒不进箱子；绑定诅咒可以掉落。
3. 费用 = 基础价 + 等级价 + 背包价，再取整并限制在上下限。默认例子：基础 `300` + 30 级 × `10` + 掉落占用 20 栈 × `10` = **800**，上限 `3000`。经验默认保留 70%。
4. 余额不足时按 `NORMAL_DROP` / `PUBLIC_CHEST` / `TAKE_ALL` 处理，见配置注释。查询余额失败或经济插件缺失时，走原版掉落。
5. 优先单箱（27 格），不够则双箱；仍不够可再开额外箱子。
6. 在死亡点附近找可放位置（默认水平 32、垂直 64），避开岩浆、虚空、已有箱子和无权限 Residence。找不到时默认写入恢复仓库并清空原版掉落，同时仍按配置处理经验。
7. 放置成功、写入记录、扣费、处理经验之后，才清空原版掉落。中途失败则尽量还原方块，物品仍走原版掉落。
8. 默认私人保护 12 小时，之后公开；再过 3 天箱子消失。`public-time: 0` 表示公开后一直保留。倒计时默认按现实时间走（关服也会过）；若不想关服期间继续计时，把 `protection.timer-mode` 设为 `PAUSE_OFFLINE` 后重启。开机后会立刻处理已到期箱子。恢复仓库过期仍按现实时间。
9. `totem.inventory-trigger` 可在致命伤害时把背包里的图腾换到副手，与死亡箱总开关无关。
10. 全息失败不影响箱子。敲碎公开死亡箱时，剩余物品进入**主人**的恢复仓库，而不是敲碎者背包。恢复仓库关闭时改为掉在地上。

世界卸载时不会删除该世界的死亡箱记录；世界重新加载后继续保护。

玩家命令、打开/取回/敲碎死亡箱、创建/公开/过期/恢复仓库等行为默认会打印到控制台。不想看时把 `audit.log-to-console` 设为 `false`。

## 玩家

别名：`/dc`。`list` / `info` / `unlock` 使用**活动死亡箱 ID**。箱子过期后玩家不能再用 `/info`。

| 命令 | 说明 |
| --- | --- |
| `/deathchest help` | 帮助 |
| `/deathchest status` | 功能是否开启、活动箱子数量、最近位置 |
| `/deathchest list` | 自己的活动死亡箱 |
| `/deathchest info <id>` | 箱子信息，并以只读 GUI 预览当时掉落快照 |
| `/deathchest unlock <id>` | 立即公开自己的箱子 |

默认潜行右键快速取回，可自动穿装备。是否创建死亡箱由 `player-settings.default-enabled` 控制，没有个人开关。

## 管理员

`restore` 使用 **DeathRecord ID**，从 `/deathchest history` 取得。回滚不退款。

| 命令 | 说明 |
| --- | --- |
| `/deathchest reload` | 重载配置和语言文件，不重连数据库 |
| `/deathchest list <玩家>` | 查看他人活动死亡箱 |
| `/deathchest history <玩家>` | 死亡记录 |
| `/deathchest tp <id>` | 传送到活动死亡箱 |
| `/deathchest unlock <id>` | 公开任意死亡箱 |
| `/deathchest restore <id> [all\|items\|exp] [--force]` | 回滚物品和/或经验 |
| `/deathchest records stats` | 记录、活动箱子、待恢复数量 |

SAFE 回滚在以下情况会拒绝，避免复制物品：箱子里的东西和快照不一致、箱子已经没了、物品还在恢复仓库、记录没有物品快照、当时没有创建死亡箱。`--force` 需要 `rollback.allow-force` 和 `deathchest.restore.force`；箱子还在时只发还箱子里剩下的物品，不会再发一整份快照。玩家离线时 `restore all` 会跳过经验并提示。

## 权限

玩家默认拥有：`deathchest.use`、`status`、`list`、`info`、`unlock`、`retrieve`。

管理员常用：`deathchest.admin`、`bypass`（打开别人的保护箱）、`break.bypass`、`unlock.others`、`restore.force`。完整列表见 `plugin.yml`。

## PlaceholderAPI

identifier：`deathchest`

- `%deathchest_enabled%`（`true` / `false`）
- `%deathchest_count%`
- `%deathchest_last_id%`
- `%deathchest_last_world%` / `%deathchest_last_x%` / `%deathchest_last_y%` / `%deathchest_last_z%`
- `%deathchest_last_price%`
- `%deathchest_last_protection_remaining%`
- `%deathchest_last_expire_remaining%`

全息和消息里还会替换 `%deathchest_owner%`、`%deathchest_protection_remaining%` 等。到期时间为 0、已到期、或箱子不受保护时显示 `-`。

## 存储

`storage.type` 为 `SQLITE` 或 `MYSQL`。MySQL 连不上不会改回 SQLite。改存储类型后必须重启。
