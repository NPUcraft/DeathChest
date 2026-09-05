# Velocitab 死亡箱 footer

将 `velocitab-footer.yml` 中的 `footers` 放入现有 `default` 分组，缩进与原来的 `headers`、`format` 相同。保留现有分组和服务器列表，因此不会改变玩家列表的跨服可见范围。

显示条件精确匹配三个服务器：Survival-Main、Survival-Industry、Survival-Resource。Velocitab 会应用服务器名替换，所以条件也包含当前配置对应的三个带颜色中文名称；以后修改这些 replacement 时需要同步修改条件。

新增 `%deathchest_tab_footer%` 返回 MiniMessage 文本，自带开头换行：

- 始终显示后端服 `general.enabled` 的全局开关状态。
- 全局开启时显示当前玩家的死亡预估费用，沿用 `%deathchest_estimated_price%` 的计算方式。个人关闭时预估费用可能为 0；全局状态不会因此变为关闭。
- 存在活动死亡箱时，按创建时间倒序选择最新的一个箱子，显示实际箱子坐标。部分领取后箱子仍存在时继续显示。
- 保护中显示到解锁的“保护剩余”；公开后显示到到期的“掉落剩余”。固定使用累计小时、分钟、秒，例如 `72时0分0秒`。
- 若配置到期删除物品，则显示“清理剩余”；永不到期显示“永久”。到期但清理任务尚未移除箱子时显示 `0时0分0秒`。
- 全局关闭后仍显示已经存在的活动箱。没有活动箱时不输出箱子行。

另新增 `%deathchest_global_enabled%`，仅返回全局开关 `true` / `false`。现有 `%deathchest_enabled%` 仍表示全局与个人开关的合并结果。

数据范围为玩家当前连接的后端服；插件没有将三个后端的活动死亡箱聚合为一个跨服列表。这里的“全局开关”也指当前后端的插件全局开关。

## 安装与刷新

1. 将重新构建的 `target/DeathChest-1.0.2.jar` 安装到三个生存后端服并重启后端。
2. 后端启用 PlaceholderAPI 和 DeathChest 的 PlaceholderAPI 集成。Velocity 及相关后端安装 PAPIProxyBridge，并开启 Velocitab 的 PAPI hook。
3. 修改 Velocitab `config.yml` 中的 `papi_cache_time: 1000`；分组原有的 `placeholder_update_rate: 1000` 和 `header_footer_update_rate: 500` 可保留。默认 PAPI 缓存为 30 秒，仅修改 footer 刷新率无法实现秒级倒计时。
4. 替换分组的 footer 后执行 `/velocitab reload`。
5. 分别验证三个生存服显示、Lobby 等其他服不显示，以及无箱、保护期、公开期、领取后和全局关闭状态。

显示示例：

```text
在线玩家：8/20人 | 余额：1200
DeathChest：开 ｜ 预消耗：660🍉
死亡点：Overworld(123, 64, -456) ｜ 保护剩余：11时59分58秒
```

参考：[条件占位符](https://william278.net/docs/velocitab/conditional-placeholders)、[PAPI 桥接与缓存](https://william278.net/docs/velocitab/placeholders)。
