# HuskSync 跨服死亡防护（DeathChest 1.0.4）

适用场景：资源服死亡保留背包，主服死亡掉落，使用 HuskSync 同步背包和血量。死亡与切服重叠时，目标服可能在同步锁定期间应用零血量并再次死亡；HuskSync 此时清空掉落，DeathChest 收到空列表。

## 本次防护

- 通过 HuskSync v3 的 `BukkitPreSyncEvent.editData`，在快照应用前将非正数或非有限血量调整为 1（半颗心）。正常正数血量保持不变，不需要关闭血量同步。
- 只编辑本次传入的快照血量，不修改其中的背包、经验或末影箱，不发放补偿物品。该事件也可能用于登录或管理员数据更新，保护同样适用。
- 源服死亡已发生；本防护不会撤销源服掉落或死亡箱。已死亡玩家同步到目标服后会以半颗心存活。这是明确的行为变化，不等同于在目标服继续显示死亡画面。
- 保留背包的死亡记录改为深拷贝当前背包、装备和副手。即使玩家关闭个人死亡箱，也会保存；仍受 `general.enabled`、`deathchest.use` 权限和 `death-records.enabled` 控制。
- 这些保留物品快照不会创建死亡箱或自动放入恢复仓库。管理员核实丢失后才能通过强制恢复覆盖背包，避免重复发放。已有空记录无法补回历史物品。
- 防护执行失败会取消本次同步并记录错误，玩家需在管理员排除错误后重新连接；启动时 API 不兼容会明确输出防护未启用。

默认启用，旧配置未添加此项也会使用 `true`。如需显式配置，合并到 DeathChest 的 `config.yml`：

```yaml
integrations:
  husksync:
    prevent-sync-death: true
```

此开关独立于死亡箱总开关和玩家个人开关。没有安装 HuskSync 的服务器不会注册该集成。不要通过在线热卸载/重装 HuskSync 更新集成；完整重启服务器。

## 部署

1. 备份两服原 DeathChest JAR 和插件数据。停止服务器，将两服的旧 DeathChest JAR 替换为 `DeathChest-1.0.4.jar`；目录内仅保留一个 DeathChest JAR。
2. 两服 HuskSync 保持 `synchronization.features.health: true` 以及 `sync_dead_players_changing_server: true`。
3. 主服 HuskSync 的 `synchronization.save_on_death.items_to_save` 改为 `DROPS`，资源服保持 `ITEMS_TO_KEEP`。这一步修正备份内容，不能单独防止同步死亡。事件优先级保持原来的 `NORMAL`。
4. 启动两服，确认出现 `HuskSync 同步死亡防护已注册，启用=true`。若出现“防护未启用”或其他错误，应先排除错误再放开切服。

## 上线前验证

代码依据 HuskSync `3.8.8-0772f09` 的事件与数据接口编写。自动化测试使用接口契约夹具和 Bukkit 模拟对象；尚未替代 Leaves + HuskSync + Redis + 数据库的双服实测。

先在测试服用可丢弃物品验证：

1. 正常存活切服：背包数量、装备、副手和正常血量不变。
2. 资源服死亡后立即切服：目标服不因同步零血量再次死亡，物品只保留一份；触发时控制台有调整血量日志及 snapshot ID。
3. 主服正常死亡：原有死亡箱和扣费规则正常；源服已入箱的物品不应在目标服重复出现。
4. 资源服普通保留背包死亡：控制台记录 `快照物品栈`；用 `/deathchest info view <record-id>` 检查快照含背包、装备和副手。

如果测试仍丢物品，保留两服同一时段日志及 HuskSync 同步快照再继续定位；本次针对的是应用零血量造成的二次死亡，不保证覆盖其他同步插件、数据覆盖或服务器崩溃问题。

## 依据

- [HuskSync PreSyncEvent 编辑接口（0772f09）](https://github.com/WiIIiam278/HuskSync/blob/0772f09/common/src/main/java/net/william278/husksync/event/PreSyncEvent.java)
- [HuskSync 同步锁定期间死亡处理（0772f09）](https://github.com/WiIIiam278/HuskSync/blob/0772f09/bukkit/src/main/java/net/william278/husksync/listener/PaperEventListener.java)
- [HuskSync 血量应用（0772f09）](https://github.com/WiIIiam278/HuskSync/blob/0772f09/bukkit/src/main/java/net/william278/husksync/data/BukkitData.java)
