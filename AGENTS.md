# AGENTS.md — The Truth

Minecraft **1.21.1 / NeoForge 21.1.248** 模组，前置 **Applied Energistics 2**，新增维度「确界 / Certus」（modid `thetruth`，维度 `thetruth:certus`）。

## 当前状态（2026-09-26）

创意与计划阶段已完成，**代码未开始**；下一批次 M0（工程骨架与 CI）。权威文档在 `docs/`：

- `00-世界观创意方案.md` — 需求权威来源（机制、内容、范围收敛）
- `01-设计红线与技术闸门.md` — 四条设计红线的实现路径与已核对结论
- `02-开发计划.md` — M0–M8 批次、验收标准、交付纪律

改设计先改文档，再动代码。

## 环境陷阱（必读）

- 本机 `~/test/Steamcommunity_302/` 反代用**自签证书**劫持域名：**需要 Gradle 下载依赖时必须先提醒用户手动关闭它**，下载完成后再提醒重新开启（保证 GitHub 访问稳定）。Java 构建出现 `PKIX path building failed` 几乎都是它在作怪，特征是 Java 失败而 curl 正常。
- 系统无 `java` 命令；JDK 21 在 `~/.gradle/jdks/jdk-21.0.12.1+1`。Gradle 8.8 wrapper 发行版已缓存。

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
- 版本号唯一来源是 `gradle.properties` 的 `mod_version`；发布 checklist 必须含版本递增，且与 GitHub Release 同批次提交。
- 验证分层：机制正确性进 GameTest（M0 引入基建），观感与手感游戏内人工验证。
- **不使用子代理**（用户偏好）：所有调查与核对在本会话内自己完成。
