# DeathChest

[![Build](https://github.com/NPUcraft/DeathChest/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/NPUcraft/DeathChest/actions/workflows/build.yml)

面向 Paper 1.21.8 的安全死亡箱插件。玩家死亡后，插件以 `PlayerDeathEvent#getDrops()` 为唯一物品来源，根据配置计算费用并保存实际掉落物。死亡箱默认先私人保护 12 小时，再公开 3 天，最后按配置掉落或删除剩余物品。

## 主要功能

- 以物品安全和防复制为优先的死亡处理顺序。
- 单箱、双箱及额外箱子容量规划，容量计算考虑物品堆叠。
- Vault、CoinsEngine / ExcellentEconomy、无经济三种模式。
- 基础价格、等级价格、背包价格、上下限和取整方式。
- `NORMAL_DROP`、`PUBLIC_CHEST`、`TAKE_ALL` 三种余额不足策略。
- 私人保护 → 公开 → 到期处理的三阶段生命周期。
- `REALTIME` 和 `PAUSE_OFFLINE` 两种死亡箱计时方式。
- Residence 可选区域权限检查。
- TextDisplay 动态全息和 PlaceholderAPI 可选变量。
- 潜行右键快速取回、空装备槽自动穿戴、背包不足时保留剩余物品。
- 完整死亡快照、可点击只读物品 GUI、管理员增量 / Force Restore。
- 玩家可用 `/dc on`、`/dc off` 持久化控制个人死亡箱，默认开启。
- 可读实体箱 ID：`DC-玩家名-yyyyMMdd-HHmmss-SSS`；同次死亡的额外箱追加 `-P2`、`-P3`。
- 可读死亡记录 ID：`DR-玩家名-yyyyMMdd-HHmmss-SSS`；同一毫秒发生冲突时追加 `-N2`。
- SQLite 和 MySQL 存储、恢复仓库、审计数据库及控制台行为日志。
- 致命伤害时自动使用背包内的不死图腾。

## 运行环境

| 项目 | 要求 |
| --- | --- |
| 服务端 | Paper 1.21.8 |
| Java | Java 21 |
| 构建工具 | Maven |
| 主类 | `com.npucraft.deathchest.DeathChestPlugin` |
| Folia | 不支持 |

可选依赖均通过 `softdepend` 接入，没有安装时不会阻止 DeathChest 启动：

- Vault：还需要 EssentialsX Economy、CMI Economy 等 Vault Economy Provider。
- CoinsEngine / ExcellentEconomy：原生按配置的货币 ID 操作，不要求桥接 Vault。
- PlaceholderAPI：未安装时只跳过外部变量和 Expansion，核心功能正常。
- Residence：未安装时跳过领地检查。

如果配置启用了经济收费，但指定的经济插件、Provider 或货币不可用，DeathChest 不会创建免费保护箱，本次物品按原版掉落。

## 构建与安装

```bash
mvn clean test
mvn clean package
```

需要跳过测试时可使用：

```bash
mvn -DskipTests clean package
```

构建产物为 `target/DeathChest-1.0.2.jar`，其中已经包含 SQLite 和 MySQL JDBC 驱动。将 JAR 放入服务器的 `plugins/` 目录并启动，默认生成：

- `plugins/DeathChest/config.yml`
- `plugins/DeathChest/message_zh.yml`
- `plugins/DeathChest/deathchest.db`（使用 SQLite 时）
- `plugins/DeathChest/uptime.yml`（暂停关服计时所需，请勿手动删除）

首次部署建议流程：

1. 关闭服务器。
2. 放入插件 JAR。
3. 启动一次，让插件生成配置。
4. 关闭服务器并修改 `config.yml`。
5. 再次启动，检查启动横幅、Storage 和 Economy Hook 日志。

## 默认行为

| 功能 | 默认值 |
| --- | --- |
| 基础价格 | 200 |
| 等级价格 | 每级 2 |
| 背包价格 | 每个实际掉落物品栈 20 |
| 最高价格 | 1200 |
| 经验保留 | 70% |
| 箱子尺寸 | AUTO：优先单箱，不够则双箱 |
| 容量溢出 | EXTRA_CHEST |
| 位置搜索 | 水平 32 格、垂直 128 格；高空、虚空和岩浆死亡优先寻找最近安全陆地 |
| 找不到位置 | VIRTUAL_STORAGE |
| 私人保护 | 12 小时 |
| 公开阶段 | 3 天 |
| 计时方式 | REALTIME，关服期间继续计时 |
| 到期处理 | DROP_ITEMS |
| 单玩家死亡记录 | 最多 30 条安全可清理记录 |
| 记录清理周期 | 12 小时 |
| 普通恢复仓库保留 | 30 天 |
| 审计日志保留 | 365 天 |
| 时区 | Asia/Shanghai（北京时间） |

价格示例：基础价 200，玩家 30 级，本次实际掉落占用 20 个物品栈，则价格为 `200 + 30×2 + 20×20 = 660`。

## 死亡处理流程

1. `general.enabled=false` 或玩家没有 `deathchest.use` 时，死亡箱流程不介入本次死亡；独立的背包图腾功能仍可生效。
2. 玩家执行 `/dc off` 后不创建死亡箱，但会记录一次未创建记录；没有个人设置时使用 `player-settings.default-enabled`。
3. `KeepInventory=true` 时始终不生成死亡箱，也不会复制玩家背包。
4. 插件只读取事件最终的 `getDrops()`，兼容此前已修改掉落列表的其他插件。
5. 应用诅咒规则：消失诅咒物品不保存；绑定诅咒物品允许从玩家身上掉落并进入后续流程。
6. 深拷贝实际掉落并先创建 `PREPARED` DeathRecord。
7. 查询经济、计算价格、规划容量并搜索合法位置。
8. 创建实体箱、写入 PDC、存入物品并持久化 DeathChest。
9. 成功保存死亡记录并完成扣费后，处理经验并清理对应原版掉落。
10. 任一步骤失败时尽量撤销已创建方块；在原版掉落尚未清除的阶段直接回退原版掉落。

找不到安全位置且 `location.failure-mode=VIRTUAL_STORAGE` 时，本次物品进入恢复仓库、不收取费用、不会创建实体箱；经验仍按 `experience` 配置处理。玩家上线后插件自动尝试将恢复物品放入背包。

## 经济与余额不足

`economy.provider` 支持：

- `VAULT`
- `COINSENGINE`
- `NONE`

`economy.enabled=false` 或 `provider=NONE` 时不扣费，死亡箱仍正常创建。

余额不足策略：

- `NORMAL_DROP`：不扣费、不创建死亡箱，全部按原版掉落。
- `PUBLIC_CHEST`：不扣费，但创建立即公开且无私人保护的死亡箱。
- `TAKE_ALL`：扣除玩家当前全部余额，然后创建正常受保护的死亡箱。

余额查询异常和扣款失败不会按“余额为 0”处理，而是中止创建并保留原版掉落。DeathRecord 会记录计算价格、实际扣款、收费前后余额、经济 Provider 和货币 ID；管理员回滚不会退款。

## 箱子容量与位置

`chest.sizing-mode`：

- `AUTO`：先模拟 27 格库存，不够再模拟 54 格库存。
- `SINGLE`：每个箱子使用 27 格。
- `DOUBLE`：每个箱子使用 54 格。

`chest.overflow-mode`：

- `EXTRA_CHEST`：继续创建额外死亡箱。
- `DROP_OVERFLOW`：可保存部分进入箱子，剩余部分保留为原版掉落。
- `NORMAL_DROP_ALL`：全部放弃创建并使用原版掉落。

位置搜索会检查世界高度、虚空、岩浆、方块可替换性、箱子连接、已有 DeathChest、双箱两侧空间及 Residence 权限。`location.max-block-checks` 限制单次死亡最多检查的候选方块数量，避免极端位置搜索长期占用主线程。

Residence 检查可分别启用 `build`、`place`、`container`。双箱两个方块都会独立检查，不能跨入玩家无权限的领地。

## 保护、公开与到期

默认生命周期：

1. 创建后私人保护 12 小时。
2. 到达 `unlockAt` 后自动公开。
3. 公开 3 天后到达 `expireAt`，按 `cleanup.expire-mode` 处理。

保护期内会阻止非主人打开和破坏，并按配置防止爆炸、活塞、漏斗及容器自动搬运。锁定中的箱子始终禁止交互。管理员 bypass 权限可绕过指定限制。

`cleanup.expire-mode`：

- `DROP_ITEMS`：删除死亡箱，把剩余物品掉落在原位置。
- `DELETE_ITEMS`：直接删除剩余物品。该选项具有永久物品删除风险。

`protection.public-time=0` 表示公开后永不自动到期。玩家或管理员执行 `/deathchest unlock <id>` 时，会立即公开箱子，并从执行时刻重新计算完整的公开时长。

保护权限直接根据真实时间戳判断；到期删除任务每 30 秒检查一次，因此箱子移除最多可能比 `expireAt` 晚约 30 秒。全息状态按 `hologram.update-interval` 刷新。世界或区块未加载时不会强制扫描其中的方块，重新加载后继续处理。

## 关服计时

`protection.timer-mode` 支持：

- `REALTIME`：按真实时间计算，服务器关闭期间倒计时继续。
- `PAUSE_OFFLINE`：服务器关闭期间暂停，启动时将确认过的停机时长补回 `unlockAt` 和 `expireAt`。

`PAUSE_OFFLINE` 使用 `uptime.yml` 和每个箱子的持久化补时标记，避免一次停机被重复补偿。切换计时模式后建议完整重启；`/deathchest reload` 不会回溯修正此前已经经过的时间。

恢复仓库保留期和审计日志保留期始终按真实时间计算，不受死亡箱计时模式影响。

## 快速取回与破坏箱子

默认潜行右键死亡箱执行 Quick Retrieve，没有 `/deathchest retrieve` 命令。

取回时先计算新的箱子和玩家库存状态，再提交：

1. 按配置尝试自动装备头盔、胸甲、护腿、靴子和副手物品。
2. 合并玩家背包中的现有物品栈。
3. 使用空背包格。
4. 无法容纳的物品继续留在死亡箱中。

`EMPTY_SLOT_ONLY` 只装备空槽；`ALWAYS_REPLACE` 仅在旧装备能够安全放回背包时替换。默认只有死亡箱主人可以快速取回；管理员是否可绕过由权限和 `quick-retrieve.allow-admin-bypass` 共同决定。

允许破坏死亡箱时，非空箱的剩余物品会先写入箱子主人的恢复仓库，然后才删除实体箱。若数据库或恢复仓库不可用，破坏会被取消，不会改为不安全的直接掉落。公开死亡箱的破坏者不会获得 Quick Retrieve 权限。

## 不死图腾与诅咒

`totem.inventory-trigger=true` 时，如果致命伤害发生且主手、副手均没有图腾，插件会在玩家存储背包中寻找图腾并换入副手，使原版图腾机制能够触发。创造和旁观模式不会执行；当前主手格不会被重复扫描。该功能与 `general.enabled` 独立。

消失诅咒物品会从死亡掉落处理中移除，不进入死亡箱或 DeathRecord 物品快照。绑定诅咒物品可以正常掉落并保存。

## 玩家命令

主命令别名为 `/dc`。

`/deathchest help` 对普通玩家仅显示下列基础命令；只有拥有 `deathchest.admin` 的管理员才会看到管理员命令区块。

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/deathchest help` | 无专用权限 | 查看帮助 |
| `/deathchest on`、`/deathchest off` | `deathchest.toggle` | 开启或关闭个人死亡箱 |
| `/deathchest status` | `deathchest.status` | 查看功能状态、活动箱数量和最近位置 |
| `/deathchest info` | `deathchest.info` | 预览自己最近一次死亡箱快照 |
| `/deathchest info all` | `deathchest.info` | 列出自己的全部死亡箱记录 |
| `/deathchest info activate` | `deathchest.info` | 列出自己的活动死亡箱 |
| `/deathchest info inactive` | `deathchest.info` | 列出自己的已提取/已恢复死亡箱 |
| `/deathchest unlock <id>` | `deathchest.unlock` | 立即公开自己的死亡箱 |

带筛选参数的 `/info` 会输出可点击列表；点击条目打开只读物品 GUI。普通玩家只能查看自己的记录，不能借助隐藏的点击命令查看其他玩家快照。

项目没有 `/public`、`/retrieve`、`/recover`、`/remove`、`/record` 命令。玩家首次状态由 `player-settings.default-enabled` 决定；允许切换时，`/dc on|off` 的选择会持久化到 SQLite/MySQL。

## 管理员命令

管理员可以从 `/dc info <玩家> [all|activate|inactive]` 的可点击列表进入预览 GUI，并通过 GUI 底部按钮执行增量恢复或 Force 覆盖。

| 命令 | 主要权限 | 说明 |
| --- | --- | --- |
| `/deathchest reload` | `deathchest.reload` | 重载配置、消息、经济和集成，不重连数据库 |
| `/deathchest info <玩家> [all\|activate\|inactive]` | `deathchest.info.others` | 查看并预览指定玩家的死亡箱记录 |
| `/deathchest tp <id>` | `deathchest.teleport` | 传送到活动死亡箱 |
| `/deathchest unlock <id>` | `deathchest.unlock`、`unlock.others` | 立即公开任意死亡箱 |
| `/deathchest restore <玩家> <id> [all\|item\|exp] [--force]` | `deathchest.restore` | 恢复指定玩家的死亡快照，默认 `all` |
| `/deathchest records` | `deathchest.record` | 查看记录、活动箱和恢复仓库数量 |

恢复经验（包括 `restore ... all`）额外需要 `deathchest.restore.exp`。`--force` 还需要配置允许并拥有 `deathchest.restore.force`。

## 增量 Restore 与 Force Restore

DeathChest 和 DeathRecord 相互独立。玩家正常领取物品不会修改原始死亡快照。

未指定 `--force` 时采用增量恢复：

- 仅允许关联实体箱仍存在、箱内物品与原始快照完全一致且没有待领取恢复条目的记录。
- 箱子已被部分领取、已经消失、当时走原版掉落或存在待恢复条目时会拒绝，以免重复发放。
- 先复制玩家当前存储背包，在内存中模拟堆叠和放入空格。
- 只有完整死亡快照能够一次性装入时才提交。
- 空间不足时不会添加任何物品，并提示玩家清理背包。
- 不覆盖当前装备、副手或已有背包物品。

恢复 `item` 或 `all` 并指定 `--force` 时采用覆盖恢复；`exp --force` 只允许重复恢复经验，不会修改背包：

- 清空目标玩家的存储背包、装备栏和副手。
- 将死亡快照写入空背包；可穿戴物优先进入对应空装备槽。
- 即使清空后仍无法完整容纳时拒绝操作，不产生部分恢复。
- Force 可以再次覆盖已经恢复过的记录，并输出控制台 WARN 和审计记录。

恢复物品前会锁定关联实体箱，并先把完整恢复快照持久化到不自动过期的 Recovery Storage 事务条目，再删除实体箱和修改玩家背包。正常完成后事务条目才会删除。若服务器在恢复过程中断电，启动时会移除仍存在的关联箱并隔离事务快照，不会自动发放；管理员检查玩家背包后使用 `--force` 明确完成处理。这样可以避免箱内物品与恢复物同时存在，也避免在删箱与写入背包之间断电造成永久丢失。

经验恢复为“设置到死亡前总经验”，不是增加相同经验值。所有恢复模式都要求目标玩家在线。

## 权限

`deathchest.admin` 默认授予 OP，并包含全部管理员子权限。

| 权限 | 默认 | 用途 |
| --- | --- | --- |
| `deathchest.use` | true | 允许插件处理该玩家死亡 |
| `deathchest.status` | true | 查看个人状态 |
| `deathchest.toggle` | true | 开启或关闭个人死亡箱 |
| `deathchest.info` | true | 查看自己的箱子和只读快照 |
| `deathchest.info.others` | OP | 查看其他玩家的死亡箱记录和快照 |
| `deathchest.unlock` | true | 公开自己的箱子 |
| `deathchest.retrieve` | true | 使用快速取回 |
| `deathchest.retrieve.bypass` | OP | 快速取回他人箱子 |
| `deathchest.bypass` | OP | 绕过私人打开限制 |
| `deathchest.break.bypass` | OP | 绕过破坏权限限制 |
| `deathchest.unlock.others` | OP | 公开他人箱子 |
| `deathchest.record` | OP | 查看记录统计 |
| `deathchest.teleport` | OP | 传送到死亡箱 |
| `deathchest.reload` | OP | 重载插件配置 |
| `deathchest.restore` | OP | 恢复物品 |
| `deathchest.restore.exp` | OP | 恢复经验 |
| `deathchest.restore.force` | OP | 使用 Force Restore |
| `deathchest.admin` | OP | 全部管理员权限 |

## PlaceholderAPI

Expansion identifier 为 `deathchest`，仅为在线玩家返回数据：

- `%deathchest_enabled%`
- `%deathchest_estimated_price%`（别名 `%deathchest_estimated_cost%`）
- `%deathchest_estimated_currency%`
- `%deathchest_count%`
- `%deathchest_last_id%`
- `%deathchest_last_world%`
- `%deathchest_last_x%`、`%deathchest_last_y%`、`%deathchest_last_z%`
- `%deathchest_last_price%`
- `%deathchest_last_protection_remaining%`
- `%deathchest_last_expire_remaining%`

消息和全息内部还支持：

- `%deathchest_id%`、`%deathchest_owner%`、`%deathchest_owner_uuid%`
- `%deathchest_world%`、`%deathchest_x%`、`%deathchest_y%`、`%deathchest_z%`
- `%deathchest_price%`、`%deathchest_currency%`
- `%deathchest_estimated_price%`（别名 `%deathchest_estimated_cost%`）、`%deathchest_estimated_currency%`
- `%deathchest_created_time%`、`%deathchest_unlock_time%`、`%deathchest_expire_time%`
- `%deathchest_protection_remaining%`、`%deathchest_expire_remaining%`
- `%deathchest_state%`、`%deathchest_item_count%`、`%deathchest_slot_count%`
- `%deathchest_player_level%`、`%player_name%`

插件先替换内部变量，再交给 PlaceholderAPI 解析其他插件变量。倒计时会按实际长度显示“天、小时、分钟、秒”，例如 `2天3小时4分钟5秒`；全息和信息消息同时标注绝对的公开或掉落时间，默认格式为 `yyyy-MM-dd HH:mm:ss`，时区为 `Asia/Shanghai`。目标时间为 0、已经到期或当前不受保护时，剩余时间显示 `-`。

预计费用按玩家当前等级、当前背包中排除消失诅咒后的物品和当前余额计算，并遵循 KeepInventory、个人开关和余额不足策略。功能关闭、没有可掉落物、无需收费、经济查询失败，以及 `NORMAL_DROP` / `PUBLIC_CHEST` 余额不足时返回 `0`；`TAKE_ALL` 余额不足时返回当前可扣余额。其他插件在实际死亡事件中临时修改掉落物时，最终扣费仍以死亡事件中的实际掉落为准。

## 存储

### SQLite

默认使用 `storage.type=SQLITE`，数据保存在 `plugins/DeathChest/deathchest.db`。SQLite 会启用 WAL、外键、busy timeout，并在正常关闭时执行 checkpoint。

### MySQL

设置 `storage.type=MYSQL` 后填写 host、port、database、username、password。也可以填写完整的 `storage.mysql.jdbc-url`，此时其他地址参数会被忽略。

默认 JDBC 参数启用：

```text
sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false
```

远程 MySQL 应配置可信 CA 和与主机名匹配的服务端证书。只有明确了解风险的本机开发环境才应手动使用 `sslMode=DISABLE`。MySQL 连接失败时插件不会静默回退 SQLite，避免把数据写入错误的存储后端。

修改数据库类型、连接地址、凭据或 TLS 参数后必须重启服务器，`/deathchest reload` 不会重新建立 Storage 连接。

## 数据保留与审计

- DeathRecord 按玩家限制数量，默认每人 30 条。
- 清理时跳过正在回滚、关联活动箱或关联待领取恢复物品的记录。
- 如果没有记录可以安全删除，允许暂时超过限制并在控制台警告。
- 普通 Recovery Storage 默认 30 天后删除未领取物品；管理员恢复事务快照不自动过期或发放，必须在中断后由管理员检查并用 `--force` 处理。
- Audit Log 默认保留 365 天。
- 玩家命令、箱子创建/打开/破坏/取回/公开/过期、经济扣款、恢复仓库和管理员恢复等默认输出到控制台。

`audit.enabled` 控制数据库审计，`audit.log-to-console` 独立控制控制台输出。关闭控制台输出不会关闭数据库审计。

## 配置和语言文件

所有功能配置集中在 `config.yml`，每个配置项均附有中文注释。聊天、帮助和全息文案位于 `message_zh.yml`，使用 MiniMessage 格式，例如 `<red>`、`<gray>`。`language: zh` 对应 `message_zh.yml`；指定文件不存在时回退中文文件。

`/deathchest reload` 会重载配置、消息文件、经济 Hook、Residence、全息、清理任务和 PlaceholderAPI Expansion，但不会重连数据库，也不适合切换计时模式或存储后端。

## 安全设计说明

- 原版掉落只在死亡箱或恢复仓库确认持久化后清理。
- 实体箱通过 PDC ID、Owner、Record ID、创建时间、世界坐标和双箱两侧共同校验。
- 孤立、复制或位置不匹配的 PDC 不会被自动信任或重新登记。
- 死亡箱转入恢复仓库使用确定性转移 ID，启动时会协调中断操作。
- 数据库写入失败时，自动领取会回滚本次玩家背包变更。
- DeathRecord 原始 ItemStack 快照不会因正常领取而修改。
- 全息属于非关键功能，全息失败不会撤销已经安全创建的死亡箱。

## 已知限制与运维建议

- 仅支持 Paper 1.21.8 和 Java 21，不支持 Folia。
- Bukkit 世界、实体和库存操作必须在服务器主线程执行；极端磁盘延迟可能拖慢主线程，应把数据库部署在低延迟存储上。
- 不要在服务器运行时直接修改数据库表或箱子 PDC。
- 使用 MySQL 时应监控连接延迟、TLS 证书有效期和数据库备份。
- `cleanup.expire-mode=DELETE_ITEMS` 会永久删除到期箱内物品，启用前请明确告知玩家。
- 定期备份数据库和 `uptime.yml`；恢复备份时应保持世界存档、数据库和插件数据来自同一时间点。
- 正式上线前建议在测试服覆盖：断电、数据库断连、低 TPS、区块卸载、背包满、连续死亡、余额不足、双箱、额外箱、Residence 边界和管理员恢复。

## 项目结构

```text
src/main/java/com/npucraft/deathchest/
├─ command/    命令、Tab 补全、只读 GUI
├─ config/     配置与语言管理
├─ hook/       经济、PlaceholderAPI、Residence 等可选集成
├─ listener/   死亡、保护、世界、玩家和图腾事件
├─ manager/    事务、箱子、记录、恢复、全息和清理
├─ model/      持久化模型与枚举
├─ storage/    SQLite / MySQL 抽象和 JDBC 实现
└─ util/       物品、经验、时间、ID、PDC 等工具
```
