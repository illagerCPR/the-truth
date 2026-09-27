# AGENTS.md — The Truth

Minecraft **1.21.1 / NeoForge 21.1.248** 模组，前置 **Applied Energistics 2**，新增维度「确界 / Certus」（modid `thetruth`，维度 `thetruth:certus`）。

## 当前状态（2026-09-27）

创意与计划阶段完成；**M0（工程骨架与 CI）、M1（维度骨架）、M2（三层地形与观测周期）、M3（确定性覆盖与不稳定曲线）、M4（入口与回程）、M5（确界存储层）已完成**：M5 交付资源链（中层残差物质簇 `residual_matter`（生成纯函数 `CertusTerrainShape.isResidualClusterColumn`，≈0.5%/列）→ 挖掘掉未定形质 `unformed_matter`（`unformed_content` 组件记忆原物品，恒定 glint）→ 确界固化器 `certus_solidifier`（无 GUI 机器：`ItemHandler` capability + 手持右键；还原带内容未定形质 / 游离未定形质固化为确界结晶 `certus_matrix`；每次 200 AE + idle 2 AE/t）+ 确界元件 `certus_cell`（ME 存储：`StorageCells.addCellHandler` 公开注册；1,048,576 bytes / 256 types；内容存 `certus_cell_content` 组件永不丢，离网未定形不可读、复网恢复；`isFoil` glint + 禁嵌套）+ 确界核心 `certus_core`（M6 Boss 掉落）+ 脐带锚点 `umbilical_anchor`（手持已绑定钥匙右键配对，同 `pairId` 两锚点分属两维成对；确界端投影确定性覆盖（`UmbilicalNetwork` + `CertaintyCoverage` 接入）+ 对主世界端 FORCED chunk ticket 强加载；全存档 ≤2 对）+ M3 曲线接入（中层 randomize 时 10% 物品转未定形质（钥匙/元件豁免）；深层窗口耗尽 = 携带物溶解为未定形质实体（`lifespan=MAX`），永不静默删除）。GameTest 33 项全绿。**M6（生物与 Boss）已完成**：残余/测量者/未观测者三生物（激怒态、扫描可被阴影/网格缺口规避、直视石化）+「最后的测量者」三阶段 Boss（扫描固化 / 召唤与删列 / 数据端口过载——AE2 import 抽 16 份测量数据即过载死亡，掉确界核心+最后的记录）+ 深层观测台结构（纯函数布局 + 测量核心唤醒）+ 观测台覆盖豁免（M3 登记项 5 关闭）+ 掉落数据化（覆盖入网 / 覆盖无网保留 / 未覆盖消散为带记忆未定形质）+ 经验数据化（数据残片右键返还）。GameTest 42 项全绿（含创造标签页单堆回归守卫；验收期修复「按 E 开物品栏崩溃」——measurer_core/data_port 漏注册 BlockItem，见 M6 硬事实）。**M7（终局与世界状态标记）已完成**：不可逆终局标记 `EndgameState`（主世界 SavedData 布尔单向置位，无清除路径；触发 = 主世界手持「最后的记录」右键解读——确界内拒绝，物品不消耗，重复解读幂等；全服广播 + payload 单向镜像 `EndgameSync` 同步客户端）+ 脐带锚点主世界绑定门槛（终局前非确界维度拒绝绑定，门槛在交互层 Block、BE `bindPair` 无门槛）+ ME 存储元件确界印记（终局后 `RegisterItemDecorationsEvent` 装饰器在全部 ME 存储元件图标右上角渲染 3×3 石英青几何印记；判定真值源 `StorageCells.isCellHandled`，spatial cell 不算 ME 存储）。GameTest 49 项全绿（M7 新增 7）。下一批次 **M8（打磨与发布）**。权威文档在 `docs/`：

- `00-世界观创意方案.md` — 需求权威来源（机制、内容、范围收敛）
- `01-设计红线与技术闸门.md` — 四条设计红线的实现路径与已核对结论
- `02-开发计划.md` — M0–M8 批次、验收标准、交付纪律

改设计先改文档，再动代码。

## 环境陷阱（必读）

- **WSL2 MTU 黑洞（2026-09-26 实锤，首个大坑）**：镜像网络模式下 eth 网卡 MTU 1500 时，超过 ~1400 字节的包被静默丢弃且 ICMP 分片通知时通时断——表现为**大文件下载随机永久卡死**（小文件正常、连接堆积 CLOSE-WAIT、进度零推进、NRT 挂死）。诊断：`ping -4 -M do -s 1472 <host>` 全丢即实锤。**已持久化修复**：`/usr/local/sbin/wsl-fix-mtu.sh`（遍历所有 `eth*` 设 MTU 1400，幂等）经 `/etc/wsl.conf` `[boot] command` 随 WSL 启动自动执行，WSL 重启后依然生效，无需手动重跑；手动补设：`sudo /usr/local/sbin/wsl-fix-mtu.sh`。
- **反代与 maven 下载无关**：`S302_rules.ini` 只含 `github=1` 等域，**不含任何 maven 域名**。所以 Gradle 拉依赖卡住时先查 MTU（上条），不是反代。反代的真实危害是 MITM github 域名用自签证书——Java 报 `PKIX path building failed` 才轮到它：需要访问 github 的构建步骤前提醒用户**关闭**反代，完成后提醒重新开启（保证 gh/git 稳定）。
- **WSLg 鼠标捕获（2026-09-26）**：`runClient` 在 WSLg（本质 RDP 通道）下视角恒速疯转——MC 用 raw relative mouse input + `XWarpPointer` 每帧回中累计增量，WSLg 两者都不支持（microsoft/wslg#1242 / #1361；Mojang MC-126875 无官方修复）。**已落地解法**：`devmods/rdpmouse-neoforge-1.21.1-1.0.0.jar`（Modrinth RDPMouse 1.0.0，SHA1 `860a7403f28c01a5`）经 build.gradle 的 `runtimeOnly files(...)` 接入 dev 运行——不参与编译、不进发布产物、目录已 gitignore。游戏内 **F8** 切换 RDP 模式，光标触边按住 **Alt** 回中，方向键可转视角，需调高游戏内鼠标灵敏度。安全性已核对：其 mixin 仅在 config 的 `client` 数组、common 入口 `init()` 为空实现，gameTestServer 加载无害（22 项测试全绿实证）。
- **git 提交邮箱**（2026-09-26 起）：全局与仓库 `user.email` 已改为 `63698328+illagerCPR@users.noreply.github.com`（GitHub noreply，防 commit `.patch` 视图泄露私人邮箱）；此前 M0–M4 的 10 个本地提交仍带旧邮箱（用户已知悉，暂不重写历史）。SSH 签名 key 与 email 无关，签名链路不受影响。
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

## M2 核对的 1.21.1 硬事实（2026-09-26）

- **自写 ChunkGenerator 的写块契约**（照抄 vanilla `NoiseBasedChunkGenerator#doFill`）：`chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, y & 15, lz, state, false)` + `getOrCreateHeightmapUnprimed(OCEAN_FLOOR_WG / WORLD_SURFACE_WG).update(lx, y, lz, state)`——必须手动维护两个 `*_WG` heightmap，否则 SURFACE 状态的 heightmap 查询（传送落点预筛）全空。
- **自写生成器的 biome 填充**：override `createBiomes`，调 `chunk.fillBiomesFromNoise(resolver, sampler)`；resolver 收到 **quart 坐标**（1 quart = 4 blocks，blockY = quartY << 2）；`StructureManager#registryAccess()` 可拿 registry。
- `RandomState.create(HolderGetter.Provider, ...)` 第一参数是 **`HolderGetter.Provider`**（不是 `HolderLookup.Provider`，`RegistryAccess` 不实现前者）；按世界种子创建噪声的正统入口是 `RandomState#getOrCreateNoise(ResourceKey<NoiseParameters>)`（RandomState 为 per-level 单例，地形形状按其实例身份缓存）。
- **GameTest 的 `LEVEL_STEM` registry 同样不含 datapack 维度 JSON**（与 FLAT preset 同根因）——generator codec 验证改走 DFU：`LevelStem.CODEC.parse(access.createSerializationContext(JsonOps.INSTANCE), 打包 JSON)`；`RegistryOps` 在 `net.minecraft.resources`。
- **观测周期驱动**：`ServerLevel#setDayTime` 只覆盖 dayTime 不动 gameTime；以 `gameTime % 周期` 为状态源每 tick（`LevelTickEvent.Post`，`net.neoforged.neoforge.event.tick`）重写 dayTime，天空/天光/雾亮度等原版系统自动跟随——无状态可重入。
- **NeoForge 自定义维度效果**：`RegisterDimensionSpecialEffectsEvent`（client mod bus）按 dimension_type 的 effects id 注册 `DimensionSpecialEffects`；订阅走主类构造器 `dist.isClient()` 守卫 + `modEventBus.addListener(EventClass.class, clientClass::method)`，避免 dedicated server 加载 `@OnlyIn(CLIENT)` 类。
- 注册 `Registries.CHUNK_GENERATOR`（Registry<MapCodec<? extends ChunkGenerator>>）用 `DeferredRegister.create(Registries.CHUNK_GENERATOR, modid)`，元素为 codec。
- 「观测期缺口收窄 / 未观测期扩张」的几何演化未实现（已生成区块不会随函数重算）：M2 交付光/雾/天氛围层周期差异，几何演化推迟到 M3+ 与方块变更基建一起评估。

## M3 核对的硬事实（2026-09-26，AE2 节点接入三连坑都在这）

- **附属自写 ME 设备（不碰 AE2 内部基类）的正确姿势**：BE 实现 `IActionHost + IInWorldGridNodeHost`，节点用 `GridHelper.createManagedNode(host, listener)` 链式 `setInWorldNode(true)` + `setIdlePowerUsage(n)`；`IActionHost#getActionableNode`、`IInWorldGridNodeHost#getGridNode` 都返回 `mainNode.getNode()`。
- **坑 1：`ManagedGridNode` 默认 `inWorldNode = false`**——不显式 `setInWorldNode(true)` 时节点是纯逻辑 GridNode：没有邻接发现、邻居也发现不了它（`getExposedNode` 里 `instanceof InWorldGridNode` 直接失败），表现为"节点存在但永远 0 连接"。
- **坑 2：AE2 邻接发现走 NeoForge capability 而非 instanceof**：`GridHelper.getNodeHost` = `level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null)`。只实现接口不注册 capability = 邻居永远扫不到你。注册：mod bus `RegisterCapabilitiesEvent.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BE_TYPE, (be, side) -> be)`。
- **坑 3：`GridHelper.onFirstTick` 在注册时就解引用 `getLevel()`**（`TickHandler.addInit` 内 `getLevel().isClientSide()`）——BE 构造函数里调用必 NPE。正确做法：`onLoad()`（`ServerLevel` 守卫）里直接 `mainNode.create(level, pos)`；create 幂等（node 非 null 即 return）。
- AE2 BE 自身节点创建时机：`clearRemoved() → scheduleInit()` → AE2 init 队列 → **下一 `LevelTickEvent.Post`** 的 `readyBlockEntities` 消费 `onReady`（前置条件 `ServerChunkCache.isPositionTicking(chunk)`，vanilla GameTest 区域满足）；节点连接建立于 `markReady → updateState → findInWorldConnections`。
- vanilla `BlockEntity#onLoad()` 不在放置时同步调用——在下一 tick 的 `tickBlockEntities` 里对 `freshBlockEntities` 队列调用。
- **NeoForge Data Attachment 自动同步**：`AttachmentType.builder(sup).serialize(Codec, Predicate).sync(StreamCodec)`——sync 目标含玩家本人（`AttachmentSync.syncEntityUpdate` 对 ServerPlayer 特判追加自己）+ 登录时 `syncInitialPlayerAttachments` 初始同步；仅 `setData/removeData` 触发发送。玩家死亡默认不拷贝 attachment（自然清零）。
- `DimensionalBlockPos` 无 dimension 访问器，判同维度用 `isInWorld(LevelAccessor)`；`ChunkSource#getChunkNow(cx, cz)` 取已加载 chunk（null 不强载）。
- 音效常量：`SoundEvents.AMETHYST_BLOCK_CHIME`（不存在 `AMETHYST_CLUSTER_CHIME`；`AMETHYST_CLUSTER_BREAK`、`WARDEN_HEARTBEAT`、`WARDEN_SONIC_BOOM` 存在）。mappings：`Util` 在 `net.minecraft` 顶层；`RandomSource` 在 `net.minecraft.util`。
- **GameTest 隔离陷阱**：per-level 静态注册表跨测试 plot 共享（GameTest 世界各 plot 相距不远），世界级"负向"断言（如"此处不得被覆盖"）会被相邻 plot 的场污染——负向验证改为对被测 BE 自身断言；正向断言不受影响。
- AE2 19.2.17 的 `IWirelessAccessPoint` 判定复用：接入点 BE 无法从 API 侧枚举，扫玩家周边 chunk 的 `LevelChunk#getBlockEntities()` 过滤接口即可（`DimensionalBlockPos.isInWorld` 判维度）。

## M4 核对的硬事实（2026-09-26）

- **NeoForge `ItemEntity.setUnlimitedLifetime()` 重置的是 `age = -32768`**（约 32 分钟延寿），**不是** despawn 阈值——"永不消失"必须另设 public 字段 `lifespan = Integer.MAX_VALUE`（`age` 是 private 无 getter）。两者叠加才是真正的永不 despawn。
- **AE2 创造 ME 存储元件是物品**（`ae2:creative_storage_cell`，`CreativeCellItem`，装进 ME 驱动器用），**没有对应方块**——GameTest 里给网络注入存储用纯 API 替身：自实现 `IStorageProvider`（`mountInventories(mounts)` → `mounts.mount(MEStorage, IStorageMounts.DEFAULT_PRIORITY)`）+ `IStorageService.addGlobalStorageProvider(...)`，**同步挂载**（ProviderState.mount() 立即调 mountInventories），add 后同 tick 即可 insert/getAvailableStacks。
- AE2 网络 API 三件套（19.2.17 公开 API 实测）：`IStorageService.getInventory()` → `MEStorage`（`insert/extract(AEKey, long, Actionable, IActionSource)`、`getAvailableStacks()` → `KeyCounter`，`Iterable<Object2LongMap.Entry<AEKey>>`）；能量 `IGrid.getEnergyService().extractAEPower(cost, Actionable, PowerMultiplier.ONE)`——先 `SIMULATE` 校验再 `MODULATE` 扣除。
- 空间元件物品 id 是 `ae2:spatial_storage_cell_{2,16,128}`（**不是** `spatial_cell2`；lang 键 `item.ae2.spatial_storage_cell_2` 可证）。
- 1.21.1 配方 JSON 的 ingredient 仍是**对象格式** `{"item": ...}` / `{"tag": ...}`（字符串形式是 1.21.2+）；目录名 `recipe/`（单数）。原版样例可从 merged jar 的 `data/minecraft/recipe/*.json` 直接核对。
- 过场界面：`RegisterDimensionTransitionScreenEvent`（client mod bus）提供 `registerIncomingEffect/registerOutgoingEffect/registerConditionalEffect(ResourceKey<Level>, ReceivingLevelScreenFactory)`；工厂签名 `(BooleanSupplier, ReceivingLevelScreen$Reason) → ReceivingLevelScreen`，`Reason` 枚举只有 `NETHER_PORTAL / END_PORTAL / OTHER`；`ReceivingLevelScreen(BooleanSupplier, Reason)` 可继承并整屏重绘（`shouldCloseOnEsc` 可关）。
- mappings：发光是 `Entity.setGlowingTag(boolean)`（无 `setGlowing`）；`Player.pick(...)` 返回 `HitResult`，须 `instanceof BlockHitResult` 收窄；`GameTestHelper.makeMockServerPlayerInLevel()` 已标记过时（仍可用，无替代）。
- **GameTest plot 布局会随测试数量变化整体重排**：M4 新增 5 个 plot 后 M3 的 radius 测试（全局负向断言 dist 17 不覆盖）被相邻带电锚点 plot 污染翻车——M3 隔离坑的强化实例。解法：`CertaintyCoverage.isPosCoveredByAnchor(anchor, pos)` 单锚点判定，负向断言锚定被测锚点自身；正向断言不受影响。
- 传送落点安全：复用 M1 debug 命令的螺旋搜索算法（抽为 `transport/ArrivalLocator`，`ChunkStatus.SURFACE` 预筛 + `WORLD_SURFACE_WG` heightmap + FULL 态逐列扫描），debug 命令与游戏入口共用单一来源。
- **空间门槛修订（2026-09-26，runClient 人工验收发现）**：AE2 空间元件（`SpatialStorageCellItem implements ISpatialStorageCell`）**不是 ME 存储元件**——ME 驱动器拒收（只认 `IMEStorageCell`），只能放进空间 IO 端口，永不入 `getAvailableStacks()`；「扫网络存储找空间元件」判定真实世界不可达，GameTest 曾用替身注入验证了一个不可达状态。正确姿势：门槛判定用 `IGrid.getNodes()` 扫 BE 方块 registry id（`ae2:spatial_io_port`，不 import 内部类）+ `grid.getSpatialService().isValidRegion()`（塔阵列合法，公开 API：`hasRegion/isValidRegion/getMin/getMax/requiredPower/currentEfficiency`）。空间 IO 端口 BE **无任何 capability**（`InitCapabilityProviders` 不注册它）→ API 侧读不到端口槽位。AE2 官方最小空间基建（jar 内 guide 示例 `spatial_storage_1x1x1.snbt`）：3 根 2 格直线塔 + cable + 端口 + 供能；pylon cluster 必须直线（L 形非法），region 只由塔撑出的外接盒**收缩 1 格**定义（收缩后每维 ≥1 即合法）。**GameTest 实测新坑：`helper.setBlock` 用 defaultBlockState 放 `ae2:cable_bus` 会跳过连接计算——各方向 connections=false，cable 不暴露任何方向的节点，靠它桥接的塔全部不联网**（塔-塔、塔-核心直接相邻不受影响）。最终测试布局无 cable：3 根直线塔（Z 3 格贴核心 / Y 2 格 / X 2 格）经 `spatial_io_port` 与核心全部直接相邻，撑出 3×3×4 外接盒（收缩后 1×1×2 合法）。`SpatialPylonService.isValidRegion()` 只在网络 boot 状态翻转（`GridBootingStatusChange`）时重算——塔入网引发 reboot 后 idle 40 tick 实证足够。**GameTest 纪律：替身只许模拟真实世界可达的状态——判定逻辑的测试必须用真实方块搭建**。


## M5 核对的硬事实（2026-09-27）

- **data component 值类型禁止裸 `ItemStack`**：运行时抛 `Data components must implement equals and hashCode... Problematic class: ItemStack`——可空组语义用 vanilla `ItemContainerContents`（`CODEC`/`STREAM_CODEC`/`fromItems(List)`/`nonEmptyItems()` 全内建，不可变且实现 equals/hashCode）。
- **AE2 自定义 ME 元件全公开 API 实测**：mod 构造期 `StorageCells.addCellHandler(ICellHandler)`；`ICellHandler.isCell/getCellInventory(ItemStack, ISaveProvider)`；`StorageCell extends MEStorage`（`insert/extract(AEKey, long, Actionable, IActionSource)` 是 default、`getAvailableStacks(KeyCounter)` 往 out 里 add、`getStatus()` → `CellState.{ABSENT,EMPTY,NOT_EMPTY,TYPES_FULL,FULL}`、`persist()`、`canFitInsideCell()`）；`IBasicCellItem` 抽象方法含 `ICellWorkbenchItem` 的 `getFuzzyMode/setFuzzyMode`（不做分区就 no-op + `isEditable=false`）；`addCellInformationToTooltip` 是 default 钩子可覆盖；内容持久化用 `GenericStack.FAULT_TOLERANT_NULLABLE_LIST_CODEC`；数量折算字节用 `AEKeyType.items().getAmountPerByte()`。
- **`BaseEntityBlock` 有抽象 `codec()`**——附属 BE 方块用 `extends Block implements EntityBlock`（M4 模式）绕开；1.21.1 的 `useWithoutItem` 签名是 `protected InteractionResult (BlockState, Level, BlockPos, Player, BlockHitResult)`，**没有 `InteractionHand` 参数**（手要 `player.getItemInHand(InteractionHand.MAIN_HAND)`）。
- **跨维度强加载**：`TicketType.FORCED`（ChunkPos）+ `ServerChunkCache.addRegionTicket(type, pos, 2, pos)`——distance=2 → ticket level 31 = ENTITY_TICKING（BE tick）；`ChunkMap.FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING)` 可证；add/removeRegionTicket 对同 pos 幂等，每 tick 重发无害。
- **GameTest `helper.destroyBlock(pos)` 只换空气不触发 loot**——loot 表测试走 `server.reloadableRegistries().getLootTable(block.getLootTable())` + `LootParams.Builder(level).withParameter(ORIGIN/BLOCK_STATE/TOOL).create(LootContextParamSets.BLOCK)`；**BLOCK 上下文缺 `BLOCK_STATE` 参数会报 "Missing required parameters"**。
- **registry frozen 后 `new Item(...)` 抛 "Registry is already frozen"**——测试里要用注册表已注册实例（`TheTruthItems.X.get()`），不要现场 new 物品类。
- 固化器类无 GUI 机器的正解：NeoForge `ItemStackHandler` 子类（`isItemValid` 限入料、override `insertItem` 让出料槽 take-only）+ `RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BE_TYPE, provider)`——漏斗、AE2 import/export bus、玩家手持右键全走同一个 capability。
- **验收期幽灵方块双 bug（runClient 2026-09-27）**：①per-server 注册表的 `unregister/peerOf` 必须 null 守卫 `pairId`——**vanilla 的方块移除与 chunk 卸载路径都会调 BE `setRemoved()`**，未绑定锚点在 `ConcurrentHashMap.get(null)` 抛 NPE 会把 `LevelChunk` 移除流程炸断：服务端 block state 残留 + BE 已移除 = 幽灵方块（线缆不连、更新后复原、无法再破坏）。②FORCED ticket 的释放目标必须记录在持有端字段（dimension + ChunkPos）并按记录解引用，**不能依赖 `peerOf`**——对端先死时 `peerOf == null` 会永久泄漏票（对端 chunk 永久 ENTITY_TICKING 强加载）。
- **GameTest 全绿 ≠ 无异常**：NPE 只出现在 `runGameTestServer` 日志（关服卸载 plot 时）不计入测试结果——验收 GameTest 时必须 grep 日志 exception/NPE。

## M6 核对的硬事实（2026-09-27）

- **实体注册**：`EntityType.Builder.of(factory, MobCategory).sized(w,h).build(String)`；属性走 `EntityAttributeCreationEvent.put`；生成位走 **NeoForge 21.1 的 `RegisterSpawnPlacementsEvent.register(type, SpawnPlacementType, Heightmap.Types, predicate, Operation.REPLACE)`**（谓词签名 `(EntityType, ServerLevelAccessor, MobSpawnType, BlockPos, RandomSource)`）；自然生成走 `data/<ns>/neoforge/biome_modifier/*.json`（`neoforge:add_spawns`）。
- **1.21.1 没有 `RandomFlyGoal`**——飞行游走自写（`FlyingMoveControl(mob, turn, hover)` + `FlyingPathNavigation`，`createNavigation` override）。
- **`BlockDropsEvent` 存在**（`net.neoforged.neoforge.event.level`）：`getDrops()` 是可修改的 `List<ItemEntity>`——方块掉落数据化/替换的正规钩子；实体侧用 `LivingDropsEvent`。
- **经验数据化钩子**：`LivingExperienceDropEvent.setDroppedExperience(0)` 后自行生成替代实体——`Player.giveExperiencePoints(int)` 做返还。
- **`MobRenderer<T extends Mob, M extends EntityModel<T>>` 的泛型绑定**：模型基类的实体泛型必须绑定到具体实体类型（`SimpleModel<ResidueEntity>`），写 `SimpleModel<Entity>` 编译不过；1.21.1 `renderToBuffer` 第五参是 int 颜色。
- **`ResourceLocation.withDefaultNamespace("ae2:x")` 生成 `minecraft:ae2:x` 双命名空间**（RuntimeException: Non [a-z0-9/._-] character）——带冒号的完整 id 一律 `ResourceLocation.parse(...)`。
- **`ServerPlayerGameMode.changeGameModeForPlayer(GameType)`**（不是 changeGameMode）。
- **GameTest mock 玩家强制 CREATIVE 且 `changeGameModeForPlayer` 改不动**——实体逻辑若依赖"玩家 creative 豁免"分支，GameTest 无法用 mock 驱动该分支（残余激怒因此不设 creative 过滤：激怒是行为反应，不是曲线豁免）。
- GameTest 的 AABB 就近构造用 `new AABB(BlockPos).inflate(r)`（`BlockPos.offset` 是 Vec3 签名，混用编译错）。
- **创造标签页条目强制单堆（2026-09-27 验收期崩溃实锤）**：NeoForge 包装 `CreativeModeTab.Output`，accept 的堆叠 `getCount() != 1` 直接抛 "The stack count must be 1"（`EventHooks.onCreativeModeTabBuildContents`）；而 vanilla `getCount()` 实现 = `isEmpty() ? 0 : count`，AIR 恒 isEmpty——**方块没注册 BlockItem 时 `Block#asItem()` 返回 AIR，`accept(Block)` 即产 count=0 堆叠**，玩家按 E 开物品栏（客户端重建全部标签页）必崩。GameTest 兜不住（标签页只在客户端构建）：回归守卫用服务端 `tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()))` 重放同一条 NeoForge 包装 accept 路径（record 构造序 `(FeatureFlagSet, boolean, HolderLookup.Provider)`），断言全部条目 count==1 且非 AIR。

## M7 核对的硬事实（2026-09-27）

- **SavedData（1.21.1）**：子类实现 `save(CompoundTag, HolderLookup.Provider)` + 静态 `load` 同签名；挂载用 `server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(构造器, 反序列化器), "键名")`（`Factory` 是 record：`(Supplier, BiFunction)`，无参版本不带 DataFixTypes）；缺键 load = 「未终局存档不受影响」的天然实现。
- **NeoForge mod payload（playToClient）对 GameTest mock 玩家必炸**：mock 玩家是 vanilla 连接（未走 mod 握手），`PacketDistributor.sendToPlayer/sendToAllPlayers` 发 mod payload 抛 `UnsupportedOperationException: Payload ... may not be sent to the client!`——**`registrar.optional()` 只免协商侧，不免发送侧**。正解：发送统一包 try-catch（`UnsupportedOperationException` 精准吞，纯视觉镜像可跳过）。登录下发挂 GAME bus `PlayerEvent.PlayerLoggedInEvent`（`@EventBusSubscriber` 默认 bus），payload 注册挂 mod bus `RegisterPayloadHandlersEvent`（`event.registrar("1")` 参数是协议版本）。
- **按物品逐个注册图标装饰**：`RegisterItemDecorationsEvent.register(ItemLike, IItemDecorator)`（client mod bus）——没有"全局兜底"注册法；`IItemDecorator#render(GuiGraphics, Font, ItemStack, xOffset, yOffset)` 在 slot 图标坐标系内绘制。ME 存储元件判定真值源 = `appeng.api.storage.StorageCells.isCellHandled(ItemStack)`（公开 API，handler 链探测、不实例化 inventory，每帧调用无虞）；AE2 19.2.17 的 ME 存储元件共 21 项（`item_storage_cell_*` / `fluid_storage_cell_*` / `portable_item_cell_*` / `portable_fluid_cell_*` 各 1k–256k 五档 + `creative_storage_cell`；id 从 jar 的 `assets/ae2/models/item/` 核对），**spatial cell 无 handler（`isCellHandled=false`）——它不是 ME 网络存储**。
- **终局门槛分层**：玩家路径门槛放 Block 交互层（`useWithoutItem`），BE 数据层方法保持无门槛——GameTest 直调 BE 的既有测试与生产门槛天然解耦（M5 脐带测试零改动）；纯函数分支（维度 × 解锁态）单独成测，全局 flag 的集成测试一律开头幂等置位，保证与测试顺序无关。
- **AE2 元件物品 id 清单核对法**：lang 键（`item.ae2.*`）不等于 registry id（`item_storage_cell_*` vs `storage_cell_*` 混淆风险）——以 `assets/ae2/models/item/*.json` 文件名为准（模型文件名 = item id）。

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
