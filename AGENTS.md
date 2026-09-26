# AGENTS.md — The Truth

Minecraft **1.21.1 / NeoForge 21.1.248** 模组，前置 **Applied Energistics 2**，新增维度「确界 / Certus」（modid `thetruth`，维度 `thetruth:certus`）。

## 当前状态（2026-09-26）

创意与计划阶段完成；**M0（工程骨架与 CI）、M1（维度骨架）已完成**：M1 交付确界维度 JSON + noise_settings 碎片岛地形 + `certus_stone` 方块 + `/thetruthdebug` 调试命令 + GameTest 4 项全绿（传送落点与平板地形两处首验缺陷已修复）。下一批次 **M2（地形与观测周期）**。权威文档在 `docs/`：

- `00-世界观创意方案.md` — 需求权威来源（机制、内容、范围收敛）
- `01-设计红线与技术闸门.md` — 四条设计红线的实现路径与已核对结论
- `02-开发计划.md` — M0–M8 批次、验收标准、交付纪律

改设计先改文档，再动代码。

## 环境陷阱（必读）

- **WSL2 MTU 黑洞（2026-09-26 实锤，首个大坑）**：镜像网络模式下 eth 网卡 MTU 1500 时，超过 ~1400 字节的包被静默丢弃且 ICMP 分片通知时通时断——表现为**大文件下载随机永久卡死**（小文件正常、连接堆积 CLOSE-WAIT、进度零推进、NRT 挂死）。诊断：`ping -4 -M do -s 1472 <host>` 全丢即实锤。**已持久化修复**：`/usr/local/sbin/wsl-fix-mtu.sh`（遍历所有 `eth*` 设 MTU 1400，幂等）经 `/etc/wsl.conf` `[boot] command` 随 WSL 启动自动执行，WSL 重启后依然生效，无需手动重跑；手动补设：`sudo /usr/local/sbin/wsl-fix-mtu.sh`。
- **反代与 maven 下载无关**：`S302_rules.ini` 只含 `github=1` 等域，**不含任何 maven 域名**。所以 Gradle 拉依赖卡住时先查 MTU（上条），不是反代。反代的真实危害是 MITM github 域名用自签证书——Java 报 `PKIX path building failed` 才轮到它：需要访问 github 的构建步骤前提醒用户**关闭**反代，完成后提醒重新开启（保证 gh/git 稳定）。
- 系统无 `java` 命令；JDK 21 在 `~/.gradle/jdks/jdk-21.0.12.1+1`（构建命令须 `export JAVA_HOME` 指向它）。Gradle 8.8 wrapper 发行版已缓存。
- **GameTest 结构模板必须二进制 `.nbt`**：数据包路线只读 `data/<ns>/structure/*.nbt`（gzip NBT），`.snbt` 仅在 IDE 的 `gameteststructures/` 平铺目录生效且命名空间被丢弃；`tryLoad` 静默吞异常，解析失败与缺失都报 "Missing test structure"。空模板用 `python3 tools/make_empty_structure.py` 生成。

## 依赖与构建（已核对，勿凭记忆改）

- 构建插件 `net.neoforged.moddev` **2.0.147**（兼容 Gradle 8.8）。
- AE2：`org.appliedenergistics:appliedenergistics2:19.2.17`，**Maven Central** 即有（主 jar + `-api` 分类器 + sources）；POM 唯一传递依赖 `org.appliedenergistics:guideme:21.1.1`。不需要 modmaven / curse.maven。
- **只允许 import `appeng.api.**`**；必须触碰前置内部类时，先在 `docs/02` 登记风险并说明理由。

## AE2 集成硬事实（证据见 `docs/01`）

- 确界地层方块**不要设 `hardness = -1`**：破坏面板 `canHandleBlock` 要求 `hardness >= 0`，会连正解路径一起被拒。
- 破坏面板传入 loot context 的是**原版钻石工具**（按 `MINEABLE_WITH_*` 标签选择），不能靠工具识别 AE2；判别"自动化开采"只能看 `LootContextParams.THIS_ENTITY` 是否为假玩家。
- `DimensionTransition` 在 **1.21.1 就存在**（`net.minecraft.world.level.portal`），配 `ServerPlayer#teleportTo(ServerLevel, x, y, z, yRot, xRot)`；AE2 `SpatialStorageHelper` 是现成参考。
- 无线覆盖判定用公开的 `IWirelessAccessPoint`（`getRange()` / `isActive()` / `getGrid()`）；自定义存储元件实现 `StorageCell` + `IBasicCellItem`，均为公开 API，无需 mixin。

## 核对 MC API 的本地技巧

免下载核对任意类的真实签名：读本机 `~/.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_*_output.jar`，然后执行
`~/.gradle/jdks/jdk-21.0.12.1+1/bin/javap -cp <jar> <全限定类名>`。

## M1 核对的 1.21.1 硬事实（2026-09-26）

- **vanilla `GameTestServer.create` 硬编码 `WorldPresets.FLAT` 并丢弃 datapack 的 LevelStem**——GameTest 环境永远无法实例化 datapack 维度（`server.getLevel` 返回 null）。维度机制测试改走：worldgen registry 加载断言 + `NoiseRouter.finalDensity().compute(SinglePointContext)` 纯函数采样。**`interpolated`/`flat_cache` 密度节点在 NoiseChunk 之外单点采样时退化为 0**——单点采样只测得到密度树算术壳；验证绑定节点用固定种子直接采样 `NormalNoise`。
- `noise_settings` JSON 的 `spawn_target` 与 `surface_rule` 是必填键；`minecraft:noise` 密度函数需显式 `xz_scale`/`y_scale`。
- mappings：`ChunkStatus` 在 `net.minecraft.world.level.chunk.status`；`Level` 最低 y 用 `getMinBuildHeight()`（`minY()` 属于 `DimensionType`）；NeoForge 事件订阅注解是独立类 `net.neoforged.fml.common.EventBusSubscriber`（`Mod.EventBusSubscriber` 不存在），`bus` 属性已废弃（默认 GAME）。
- 1.21.1 目录名：掉落表 `loot_table/`（单数）、tag `tags/block/`（单数）。
- **岩浆海陷阱**：`NoiseBasedChunkGenerator` 的全局流体 picker 与 `aquifers_enabled` 无关——`y < min(-54, sea_level)` 的空腔无条件填岩浆。自定义维度若 `min_y < -54`，必须把 `sea_level` 设为 ≤`min_y` 才能避免虚空底部变岩浆海（原版 end 因 min_y=0 侥幸无事）。
- 1.21.1 的 `ChunkStatus` 已无 `HEIGHTMAP`；`SURFACE` 是首个地形高度可信的状态。
- NeoForge FML 类不在 `neoforge-21.1.248-merged.jar`，在 `~/.gradle/caches/modules-2/files-2.1/net.neoforged.fancymodloader/loader/4.0.43/.../loader-4.0.43.jar`。

## 设计红线（违反即打回）

1. 入口门槛位于 AE2 后期（空间 IO + 成对纠缠奇点），不提供绕过路径。
2. 维度内物质不稳定，必须建立确定性覆盖才能作业。
3. 地层必须经 ME 手段开采（自动化是正解）。
4. 维度产物反向增强主世界 AE2 能力。

**永不静默删除玩家物品**：一切损失必须有前摇预警与可挽回窗口。

## 工作约定

- 对话与文档用简体中文；**源代码标识符一律英文，禁止拼音**。
- 文档除 `AGENTS.md` / `README.md` 外一律放 `docs/`；`README.md` 待开发完成后再建。
- 每完成一个批次（实现 + 验证）立即 `git push origin main`，随后 `gh run watch <runId> --repo illagerCPR/the-truth --exit-status` 等 CI 变绿再收尾。
- **每完成一个批次后必须停下，等候用户明确指令再进入下一批次**，不要自动连跑。
- 版本号唯一来源是 `gradle.properties` 的 `mod_version`；发布 checklist 必须含版本递增，且与 GitHub Release 同批次提交。
- 验证分层：机制正确性进 GameTest（M0 引入基建），观感与手感游戏内人工验证。
- **不使用子代理**（用户偏好）：所有调查与核对在本会话内自己完成。
