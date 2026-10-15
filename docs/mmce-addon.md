# MMCE 附属 mod 开发说明（ModularGrid）

ModularGrid 是 [Modular Machinery: Community Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition)（下称 MMCE）的附属 mod。
参考实现：[NovaEngineering-Core](https://github.com/NovaEngineering-Source/NovaEngineering-Core)（NEC，MMCE 的大型附属）。

## 1. 依赖

`gradle/scripts/dependencies.gradle`：

```groovy
modCompileOnly "curse.maven:${propertyString('mmce_curse_file')}"   // MMCE 发行 jar，Unimined 反混淆成 MCP 名供编译
runMods("curse.maven:${propertyString('mmce_curse_file')}") { transitive = false }
runMods("curse.maven:${propertyString('crafttweaker_curse_file')}") { transitive = false }
compileOnly 'software.bernie.geckolib:geckolib-forge-1.12.2:3.0.31'     // 仅编译期，见下
runMods('mezz:jei:4.35.0:dev') { transitive = false }                    // 仅开发运行，见下
```

**GeckoLib 为什么需要**：`TileMultiblockMachineController implements software.bernie.geckolib3.core.IAnimatable`，
不把这个接口所在 jar 放到编译 classpath 上，连 `controller.getPos()` 这种成员访问都会编译失败（javac 需要完整类型层级）。
本 mod 自己**不引用任何 GeckoLib 类型**（编译产物里没有 geckolib 引用），所以它只是 `compileOnly`，运行时和 MMCE 一样是可选的。

**JEI 为什么需要**：MMCE 的 GUI（蓝图预览、控制器配方槽等）会无条件引用 JEI 的渲染类，所以开发运行的
`run/<side>/mods` 里放一份 JEI 的 `dev` classifier jar（MCP 名）。它不是本 mod 的依赖，发布产物里没有它。

* 版本写在 `gradle.properties` 的 `mmce_curse_file` / `crafttweaker_curse_file`（CurseForge 的 `slug-projectId:fileId`）。
* **编译**用 `modCompileOnly`：Unimined 会把 MMCE 从 SRG 反混淆到 MCP，所以这里能直接写 `@Override public EnumBlockRenderType getRenderType(...)` 这种覆盖 MC 方法的代码；打出来的 jar 再由 `remapJar` 映射回 SRG，和玩家手上的 MMCE 发行版对得上。
* **运行**不能走 classpath：`runMods` 配置 + `copyRunModsClient` / `copyRunModsServer` 任务会把发行 jar 复制进 `run/client/mods`、`run/server/mods`，由 Cleanroom 的 launch class loader 加载。放到 `-cp` 上会被 app class loader 加载，MMCE 的 `MMCELateMixinLoader implements zone.rong.mixinbooter.ILateMixinLoader` 就会因为两个 class loader 而抛 `ClassCastException`。
* `run/<side>/mods` 是 `.gitignore` 里的运行产物，换 MMCE 版本只需改 `gradle.properties` 再跑一次。

## 2. 已实现的方块

| 项 | 值 |
| --- | --- |
| 注册名 | `modulargrid:access_plug` |
| 中文名 | 流通数据节点 |
| 英文名 | Data Flow Access Plug |
| 类 | `com.modulargrid.common.block.BlockAccessPlug`（继承 `BlockMachineComponent`） |
| TileEntity | `com.modulargrid.common.tile.TileAccessPlug`（继承 `TileEntityRestrictedTick`） |
| 创造模式标签页 | MMCE 的 `CommonProxy.creativeTabModularMachinery` |
| 注册 | `RegistryBlocks` / `RegistryItems`（`RegistryEvent.Register` 事件式） |

继承 `BlockMachineComponent` 后它就是一个合法的 MMCE 机器结构组件：可以在机器 JSON 的结构里使用，并且吃机器颜色（`Config.machineColor` / 单方块 `casingColor`）。

接入点是**机器接入频道的接口**（见 §5）：把它放进机器结构后，机器的能量 IO 可以完全走拥有者的能量频道；
它本身对外也是能量仓（六面 Forge Energy + 每 tick 主动外推），并且任何接受 MMCE 能量仓的位置都接受它，
所以整合包不必修改机器 JSON，就能用接入点顶替能量输入/输出仓。
拥有者右键它可打开 GUI 逐个频道切换启用/停用。

## 3. 渲染：普通外壳 + 专属 overlay

与 MMCE 的功能方块（输入仓、能量仓等）一致，模型是 **两个重合的完整立方体**：

```
assets/modulargrid/models/block/blockmodel_overlay.json        <- 复制自 MMCE 的同名父模型
assets/modulargrid/models/block/blockmodel_overlay_all.json    <- bg_*/ov_* 映射到 bg_all/ov_all
assets/modulargrid/models/block/access_plug.json               <- 组装：外壳 + overlay
```

```json
{
    "parent": "modulargrid:block/blockmodel_overlay_all",
    "textures": {
        "bg_all": "modularmachinery:blocks/blockcasing_plain",
        "ov_all": "modulargrid:blocks/access_grid"
    }
}
```

* **外壳层**（`bg_all`）用 MMCE 的普通外壳贴图 `modularmachinery:blocks/blockcasing_plain`，面带 `tintindex: 1` → 会被机器颜色着色，放在 MMCE 多方块结构里跟其它外壳完全一致。
* **overlay 层**（`ov_all`）是 `assets/modulargrid/textures/blocks/access_grid.png`（16×16，四周透明），面**不带** `tintindex` → 保持自己的颜色，不会被机器颜色染。
* 方块因此必须是 `BlockRenderLayer.CUTOUT`（overlay 有透明像素），且 `getRenderType` 为 `MODEL`。

模型/贴图的颜色处理由 `ClientProxy.init()` 完成：把 `RegistryBlocks.DYNAMIC_COLOR_BLOCKS` 里的方块注册到 `BlockColors`（`ttintindex 1` 走 `BlockMachineComponent#getColorMultiplier`），物品同理走 `ItemColors`。

新增方块时的清单：

1. `common/block/BlockXxx.java`（继承 `BlockMachineComponent`，构造函数里 `setRegistryName` / `setTranslationKey` / `setCreativeTab`）
2. `common/tile/TileXxx.java`（不需要额外行为就直接用 `TileColorableMachineComponent`）
3. 在 `RegistryBlocks.registerBlocks()` + `registerTileEntities()` 里登记
4. `assets/modulargrid/blockstates/xxx.json`、`models/block/xxx.json`（可复用上面的 overlay 父模型）、`models/item/xxx.json`
5. `assets/modulargrid/lang/en_us.lang` / `zh_cn.lang`：`tile.modulargrid.xxx.name=...`

## 4. 开发环境专用访问转换器

`gradle/dev_access_transformers.cfg`，由 `build.gradle` 的 `unimined.minecraft.cleanroom.accessTransformer` 引用。

为什么需要：Cleanroom 的 `ModAccessTransformer` 会把 mod 自带（**SRG 名**）的 AT 原样应用到 **MCP 名**的开发环境类上，等于不生效；CraftTweaker 因此直接崩在 `LanguageMap.instance`（private）。

这个文件是 **仅开发环境** 的：Unimined 只把它打到开发用的 Minecraft jar 上（日志里的 `Applying ATs [forge_at.cfg, ...dev_access_transformers.cfg]`），产物 jar 里没有它，也不会写 `FMLAT`。

注意两点：

* 用户 AT 文件必须写 **MCP 名**（Unimined 会再映射回 SRG）；这里的内容是 CraftTweaker 与 JEI 的 AT 从 SRG 转成 MCP 后的结果。
* 文件里**不能出现只有 `#` 的行**（纯注释行会导致 AT 工具报 `Invalid access transformer line ...: #` 并让配置失败），`# 注释文字` 这种是允许的。

往 `run/<side>/mods` 里加了自带 AT 的 mod 时，把它的 AT 一并转换后追加到这里。

### 已收录的 AT

| 来源 | 为什么需要 |
| --- | --- |
| CraftTweaker | `LanguageMap.instance` 等私有成员，不补就无法启动 |
| JEI | `TextureMap.initMissingImage()` 等，MMCE 的 GUI 会用到 |
| CoFH Core | `ItemSpade/ItemPickaxe/ItemAxe.EFFECTIVE_ON` 等；不补则热力基础直接 `IllegalAccessError` |
| CodeChickenLib | 贴图/模型/渲染相关的几十个私有成员；不补则 CCL 初始化失败 |

### 开发环境专用的第三方兼容补丁

除了 AT，开发环境还需要两个补丁才能让常见的 1.12.2 老 mod 跑在 JDK 21+ 上（都**不影响**产物在正常环境的行为）：

* `mixin/CodeChickenLibReflectionMixin` + `common/util/FieldModifiers`（仅当装了 CCL 生效，见
  `mixins.modulargrid.compat.json` 与 `CompatMixinPlugin`）：
  * `ReflectionManager.removeFinal` 在 Java 17+ 会 `removeFinal → getField → getField_Unsafe → removeFinal` 无限递归，用递归守卫掐断；
  * Java 17+ 拒绝为 `static final` 字段构建反射访问器（**连读都会抛 InternalError**），也拒绝写 `final` 字段，
    因此 `ReflectionManager` 里的字段读写改走 `Unsafe`。
* 编译期用 `libs/dev/CodeChickenLib-*.jar`（`compileOnly`，该目录已 gitignore）：只为了能编译上面这个补丁，
  产物不引用 CCL，CCL 依旧是可选邻居。
* **环流网络**是 coremod 且自带 AT，dev 环境里它的 AT 同样转成 MCP 追加到了 `gradle/dev_access_transformers.cfg`。
  它的 mixin 是按 **SRG 名**写的（`@Shadow func_xxx`），而 dev 环境的类名是 MCP，且它的 refmap 只覆盖它自己注入的成员，
  所以 dev 运行改用 Unimined 生成的 **MCP 名副本**（`build.gradle` 的 `prepareCfnDevJar`），
  并把副本里那份 refmap **清空**（MCP 名的类不需要重映射，留着反而会把名字映射回 SRG）。
  这也是为什么不去全局改 Forge 的映射属性：`srg.srg-mcp` 同时被 Forge 的反射助手与 CCL 的 `ObfRemapper` 解析，
  格式一动它们就崩（CCL 会报 `ObfRemapper` 的 `StringIndexOutOfBoundsException`），所以只在**我们自己生成的 dev 副本**里动手。

## 5. 频道系统

频道是**绑定玩家 UUID、存档级全局**的数据槽，用来给机器提供数据 / 能量。

### 数据层（`common/channel/`）

| 类 | 作用 |
| --- | --- |
| `Channel` | 一个频道：`name` / `owner(UUID)` / `ownerName` / `public` / `energy` / 值 |
| `ChannelRegistry` | 静态入口：注册、删除、查询、按地址反查、列出可接入频道、解析机器应用的能量频道 |
| `ChannelWorldData` | `WorldSavedData`，挂在**主世界** → 跨维度全局；布局 `{ownerUUID: {name: channel}}` |
| `ChannelInfo` | 同步给 GUI 的不可变快照（含是否启用） |
| `ChannelCleanup` | 删除频道时清扫所有已加载接入点禁用表里的该地址 |

* 值类型：**能量频道是 `AtomicLong`**（`1 RF = 1`，全 `long` 范围精确，`takeEnergy` / `giveEnergy` 原子且加法饱和），其他频道是 `float`；解析、NBT、显示全程不经过 `double`。
* 约束：名字 `[A-Za-z0-9_中文]{1,24}`、每人上限 16 个、每人最多一个能量频道；公开频道任何玩家可接入，删除仅限创建者。
* 机器使用的能量频道：配置声明了固有能量频道时**只有固有频道**（玩家自设的能量频道会被清掉，见 §7）；否则才是
  **拥有者自己的能量频道** → **他人公开能量频道中第一个被本接入点启用的**（按 UUID 排序，保证确定性）。
* 公共频道不默认接入：别人的公共频道与公共固有频道都要在接入点 GUI 里手动启用；当前实际使用的那条在 GUI 里带 `▶` 标记。

### 接入点 ↔ 机器绑定

MMCE 没有公开的「组件 → 控制器」反查（`MachineComponentManager` 只提供写入接口），只对 `UpgradeBusProvider` /
`SmartInterfaceProvider` 做了硬编码回调。`TileMultiblockMachineControllerMixin` 注入
`TileMultiblockMachineController#addComponent` 的尾部，对实现了 `AccessPlugProvider` 的组件调用 `boundMachine(controller)`；
接入点每 tick 惰性校验（控制器失效或结构解体就放手），不需要第二个 Mixin 做解绑。

### 组件与能量优先

接入点是 `MachineComponentTile` + `MachineCombinationComponent`：

* `provideComponent()` 返回 `null`（仍必须实现 `MachineComponentTile`，否则 MMCE 的发现逻辑会跳过这个 TE），
  两个组件都走 `provideComponents()`：`EnergyHatch(INPUT)` 与 `EnergyHatch(OUTPUT)`，
  它们的 `IEnergyHandlerAsync` 直接读写能量频道（`getMaxEnergy() = Long.MAX_VALUE`）。
* **groupID 必须互不相同且稳定**：MMCE 的组件 map 以 TileEntity 为 key，同 groupID 会互相覆盖，
  因此两个组件在构造时各取一次 `getUniqueGroupID()` 并缓存。
* 每 tick 还会管理**物理能量仓**：输入仓从频道补满、输出仓抽空进频道（按仓的实际变化量结算，被拒收的差额退还），
  于是无论 MMCE 以什么顺序遍历组件，能量 IO 实际都落在频道上。

### 接入点本身的能量 IO

* `TileAccessPlug` 实现 `IEnergyStorage`，在六面暴露 `CapabilityEnergy.ENERGY`：外部推入的能量进频道、抽取的能量出自频道
  （都走 `Channel#giveEnergy/takeEnergy`，没有第二份存储）；数值转换由 `common/util/EnergyIo` 做饱和钳制，`long` 池 ↔ `int` API 不会回绕。
* 每 tick 往 6 个相邻方块各推一次（与 MMCE 输出仓同样的做法，同样跳过 MMCE 能量仓与接入点，避免仓对仓互推）。
   MMCE 的输入仓自己不拉，所以主动推才是「两机器不用线缆」的关键。先扣频道再交接，被拒收的差额退还，和补满输入仓同一套结算。
* 未绑定机器的接入点用**放置者**的频道（NBT `placer`，由 `BlockAccessPlug#onBlockPlacedBy` 写入），因此单独摆着也能当频道端口；GUI 仍然只在已成形机器里可用。

### IndustrialCraft 2（可选）

* 类上用 `@Optional.InterfaceList` 声明 `ic2.api.energy.tile.IEnergySink` 与 `IEnergySource`，方法都带 `@Optional.Method(modid = "ic2")`；
  没装 IC2 时 Forge 会把接口和这些方法体整个去掉，mod 不需要 IC2。
* 首次服务端 tick 向 `MinecraftForge.EVENT_BUS` 发 `EnergyTileLoadEvent` 加入能量网，`invalidate()` / `onChunkUnload()` 发
  `EnergyTileUnloadEvent` 退出（与 MMCE 的 `IntegrationIC2EventHandlerHelper` 同一套机制）。
* 换算：**1 EU = 4 RF**；档位 **6（LuV，32768 EU/t）**，两方向都按此上限节流。`injectEnergy` 按频道实际增额返回未能接收的余量，
  `drawEnergy` 从频道扣；无频道（未成形 / 被 GUI 关掉）时 `getDemandedEnergy`/`getOfferedEnergy` 均为 0 且不收不发。
* 注意 IC2 固有行为：本档位电压高于目标机器 tier 时会被 IC2 惩罚，给低压机器供电需要 IC2 侧变压器。

### 自动取代能量仓

* `common/machine/EnergyHatchPatternCompat` 把接入点追加进每个**接受 MMCE 能量仓**（`BlockEnergyHatch`）位置的允许方块列表：
  `BlockInformation#addMatchingStates` 幂等，并且会同步刷新采样列表，所以蓝图与 JEI 预览里该位置能刷出接入点。
* 挂点是 `Mixin/MachineRegistryMixin`：注入 `MachineRegistry#registerMachines` 与 `#reloadMachine` 的 HEAD，时机在 MMCE 重建
  `BlockArrayCache` 之前，旋转缓存会一并带上接入点，`/modularmachinery reload` 也自动重新应用。
* 结构校验与组件收集都不需要改：能量仓位置本身是 TE 位置，控制器遍历 `foundPattern.getTileBlocksArray()` 时就会拿到接入点。

### GUI 与同步

* 右键接入点 → 校验拥有者（非拥有者 / 机器未成形各给一条消息）→ `openGui`；
  `CommonProxy implements IGuiHandler`（`GuiType.ACCESS_PLUG`），`ClientProxy` 负责创建 `GuiAccessPlug`。
* GUI 复用 MMCE 工厂控制器的背景 `guifactory.png`（280×213）：左侧 6 格是频道行（**点击切换启用/停用**）、
  旁边是 `GuiScrollbar`、右侧面板显示鼠标悬停行的详情；玩家背包槽位对齐贴图里的背包面板。
* **按钮是自绘的原版风格三态贴图**（`textures/gui/access_plug_toggle.png` 频道行 85×32、`flux_toggle.png` 通量行 127×16，
  各自纵向排布「普通 / 禁用 / 高亮」三块）：普通为浅灰凸起，禁用转灰，鼠标悬停时用原版蓝 `#9EB2F3`，
  按下按原版做法只把文字下移 1px。状态按**行**独立计算，所以点一行不会牵动其他行；过宽的按钮只拉伸中间平色区，
  左右边框保持 1:1。
* **文字**：频道名 `§5`、类型/拥有者行 `§9`、数值行 `§6`，三种颜色不随启用状态变化；文字用原版字体渲染并显式开启投影。
* **数值显示两种形式**（`common/util/ValueFormat`）：
  * 频道行用短形式，超过 **10 位**（≥ `1e9`）转 `N.NNeNN`（尾数两位小数，进位会并入指数）——再长就会把 `RF` 挤出格子；
  * 详情面板用精确形式，能量频道输出完整整数，浮点频道**至多 4 位小数**并去掉末尾零。
  两种形式各自同步（`ChannelInfo` 带 `value` 与 `exactValue`），服务器判断「值变了」时两份都比较。
* **只有一个 C2S 包**：`PktChannelToggle`（自发自收的单类消息，服务端会再校验一次拥有者与频道是否存在）。
  服务端 → 客户端全部走接入点 TE 的**标准方块更新包**（`writeNetNBT` 携带频道快照，`markForUpdateSync()` 触发），
  打开 GUI 时通过 `addViewer` 触发推送，**有观众时每 5 tick（4Hz）刷新数值**，没人看时降到 10 tick。

## 6. 无线能量网络兼容（可选）

两个网络都是**可选邻居**：集成类只在对应 mod 存在时加载，接口也只在存在时才挂在接入点上
（`@Optional.Interface` + `@Optional.Method`，与 §5 的 IC2 同一套机制；守卫方法体的类型引用必须被 Forge 剥掉，
否则 JVM 校验时会因缺少接口而 `NoClassDefFoundError`）。

### Flux Networks

接入点**自己就是** FN 的 storage 连接器，不需要在旁边再放输入/输出节点：

* **接口**：`AccessPlugFluxConnector extends sonar…IFluxStorage`。FN 会用 `instanceof IFluxConnector` 从方块实体上找连接器
  （它的 GUI、TOP 集成、实体处理器都这么查），所以接口必须挂在接入点本体上。该接口把 `IFluxConnector` 的三十来个方法
  都写成 `default` 并转接到 `AccessPlugFluxState`，于是接入点的类里不出现任何 FN 类型；没装 FN 时 Forge 把整个接口摘掉。
* **桥接**：`AccessPlugFluxTransfer implements ITransferHandler` 是能量层，语义对照 FN 自家的 storage handler：
  `addToBuffer` / `removeFromBuffer` 就是频道的存/取（没有第二份缓冲，也不会和池子跑偏），
  `getBuffer` 报频道余量（网络看到的是真实池子），而 `getChange` 只报**本连接器自己**这一轮的增减
  —— 多个接入点共用一个频道时，报池子总量会在网络统计里被重复计入。
* **设置**：网络 ID / 优先级 / 限流 / surge / 强制加载 / 名字沿用 FN 自己的 NBT 键（它的 tile 一套、网络序列化一套，两套都能读写），
  玩家在 FN 的 GUI 里改；接入点自己的 NBT 里也存一份，重启后保留。
* **语义**：类型 `STORAGE`，优先级按 FN 自家 storage 的规则（**在消费方之后充电**）。频道本身没有容量上限，
  **每 tick 流量由玩家给这个连接器设的 limit 决定**；`getRequest()` 另有一个上限（`1 << 58`）——
  FN 会把一张网里所有连接器的请求**裸 `long` 相加**（无溢出检查），不限幅的话那个和会溢出，而这个值远高于任何真实吞吐，
  不构成实际限制。
* **入网**：首次服务端 tick 调 `FluxUtils.addConnection`，`invalidate()` / `onChunkUnload()` 调 `removeConnection`；
  FN 再通过 `FluxConnectionEvent` 回调 `connect`/`disconnect`。`open(player)` 打开接入点自己的频道 GUI。

### 环流网络（Circulation Flow Networks）

* 通过它公开的 `API.registerEnergyHandler` 注册 `AccessPlugEnergyHandlerManager`（**优先级 60**，高于它自带的通用 FE 处理器 10
  与 MMCE 处理器 51），所以接入点总是走自己的处理器，而不会降级成 `int` 的 Forge Energy 存储。注册必须在它的
  `RegistryEnergyHandler#lock()` 之前完成，它在 postInit 才上锁。
* `AccessPlugEnergyHandler implements IEnergyHandler`：类型 `STORAGE`，`receiveEnergy`/`extractEnergy` 直接对频道做 `long` 增减，
  `canReceiveValue`/`canExtractValue` 分别报频道余量与存量。`EnergyAmount` 按它的对象池约定使用：
  自己的量用 `obtain`，无量为共享的 `EnergyAmounts.ZERO`，临时对象读完 `recycle`，调用方传进来的对象不碰。
* 生命周期与它自家的 FE 处理器一致（BEGIN_TICK + 固定角色），且每个 tick 观察的是**频道本身**而不只是「有没有频道」：
  成机、在接入点 GUI 里关掉频道、回退到另一个频道都算状态变化，会回调 `HandlerInvalidationSink#backendChanged`；
  接入点被拆掉时返回 `SUSPEND_UNTIL_REBIND`。

#### 接入点自身就是网络节点

只注册能量处理器只能让环流网络把接入点当作「一台可读写的能源机器」：它的能源路由要求机器落在**某个能源节点**的供能范围内，
而节点才是网络图（grid）的成员。因此接入点额外把自己接进它的节点生命周期：

* `TileAccessPlug` 可选实现 `INodeBlockEntity<PortNode>`（`@Optional.Interface`），节点类型用环流网络自己的
  `NodeTypes.PORT_NODE` 与 `PortNode`，供能/连接范围也就沿用它的默认配置。
* 节点的创建、加入与移除全部复用它的 `NetworkManager`（`API.getNodeAt` / `addNode` / `removeNode`），不自建第二套网络图；
  节点对象存在接入点里并在存档中序列化（`circulationNode`，由 `NodeTypes.deserialize` 读回），
  重载后先认领同位置的已注册节点，已登记则不再重复 `addNode`。
* 生命周期挂点：`nodeValidate()` 在方块加载与首次服务端 tick 时登记，`nodeInvalidate()` 在 `invalidate()` / `onChunkUnload()`
  时移出网络；`syncNodeAfterNetworkInit()` 供网络初始化后补齐。登记失败只记一条 warn（带 `AddNodeResult` 的状态），
  不影响接入点作为能量端口的基本功能。

于是环流网络看到的是「一个网络节点 + 它的能源设备」两层：节点负责被网络寻路，能量处理器负责把能量记进频道。

## 7. 固有频道、脚本与频道增益

阶段 4 给整合包作者补的三件事：**用配置文件定义频道**、**在脚本里读写频道数值**、**让频道给机器提供 `RecipeModifier`**。

### 固有频道（firm channel）

配置文件 `config/modulargrid/firm_channel.json`（缺失时生成最小文件）：

```json
{
  "allowPlayerEnergyChannels": false,
  "channels": [
    {
      "channelId": "temp",
      "channelLocalization": "channel.modulargrid.firm.temp",
      "isPublic": true,
      "isEnergy": false,
      "masterTo": []
    },
    {
      "channelId": "grid_power",
      "channelLocalization": "电网",
      "isPublic": false,
      "isEnergy": true,
      "masterTo": [""]
    },
    {
      "channelId": "factory_water",
      "channelLocalization": "channel.modulargrid.firm.factory_water",
      "isPublic": false,
      "isEnergy": false,
      "masterTo": ["alloy_furnace"]
    }
  ]
}
```

| 键 | 含义 |
| --- | --- |
| `allowPlayerEnergyChannels` | 是否允许玩家自己注册能量频道。关掉只拦**新注册**，老存档里已有的照常可用 |
| `channelId` | 频道 id，也是脚本里的变量名：`[A-Za-z_][A-Za-z0-9_]{0,31}`，全局唯一（重复只保留前者并 warn） |
| `channelLocalization` | 显示名；先按约定 key `modulargrid.channel.<id>` 查语言文件，未命中则用这里的原文 |
| `isPublic` | `true` = 全世界共用一份；`false` = **每个玩家各一份**，按需惰性创建 |
| `isEnergy` | `true` = 能量池（值走 `long`，可被接入点当能量频道用）；`false` = 数据池（`float`） |
| `masterTo` | **必须是数组**，限定的机器列表；元素 `""`、空数组或缺省 = 所有机器。写成裸机器名（`alloy_furnace`）即可，也接受全名（`modularmachinery:alloy_furnace`） |

语义要点：

* **固有频道不占**玩家的 16 个配额；它们存在内部的全零「固有 owner」下，公共实例的键就是 `channelId`，私有实例是
  `channelId@玩家UUID`（地址、GUI 开关键、存档结构都沿用阶段 1 的）。
* **私有的固有能量频道占**该玩家唯一的能量频道名额（`isPublic: false` + `isEnergy: true`）：配置里只允许一个
  （多写会 warn 且只用第一个），该玩家不能再注册自己的能量频道，接入点解析时直接用它。
* **`isEnergy: true` 忽略 `masterTo`**：能量池是机器的电源，不是按机器开关；作用范围已经由「私有 = 每个玩家一份」限定。
* **配置只存定义、存档只存数值**：删掉配置里的条目后，随配置读取一起清理它的数值并记日志；
  改 `isPublic` 或 `isEnergy` 同样会丢弃旧数值（键形状/类型变了）。只有配置**成功读取**过才会清理，写坏配置文件不会删存档数据。
* 跟随 MMCE 重载：`/modularmachinery reload`（以及 CrT 脚本加载前）会重新读取配置并清理失效数值；
  服务端启动时也会读一次（配置文件在游戏外被改过也生效）。

### 接入点侧的规则

* **固有能量频道会顶掉玩家自设的能量频道**：配置里只要声明了**任意**能量固有频道（公共或私有），玩家自设的能量频道就不存在了——
  存档里已有的会被**直接清掉并记日志**，`/modulargrid reg -e` 也会被拒绝。整合包声明了池子，就由整合包决定能量落在哪，
  不做「解析时优先级比较」这种留旧数据的做法（本 mod 的预览版本从未外发，旧存档不兼容就不兼容）。
* **解析顺序**（只剩下能存在的东西）：① 私有固有能量频道（该玩家那份）→ ② 公共固有能量频道（配置顺序，需在 GUI 里启用）→
  ③ 该玩家自己注册的能量频道 → ④ 其他玩家的公共能量频道；③④ 只在配置**没有**声明任何能量固有频道时才会出现。
* **当前用的是哪条能看得见**：接入点 GUI 里正在使用的那条能量频道行首带 `▶`，详情里的状态行会写成
  「状态: 已启用（本机正在使用）」，并且在切换的那一刻写一条日志（`Access plug at ... now uses the energy channel '...' (firm channel, per player)`），
  所以「能量到底跑在哪条频道上」不需要靠猜。
* **公共频道默认不接入**：只有「属于自己」的频道（自己注册的、以及配置里给每个玩家各一份的）默认可用，
  别人的公共频道 / 公共固有频道必须在该机器的接入点 GUI 里**手动启用**（GUI 里仍可停用自己的频道）。
* **`masterTo` 只在数据频道上生效**：非能量的固有频道不匹配本机器时不出现在 GUI 里、也不会提供增益；
  接入点还没成形时无法匹配机器名，此时不做过滤（成形后按机器名生效）。
* GUI 里固有频道显示为「固有」，名字走上面的语言 key → 配置原文 → id 三级回退。

### 脚本里读写频道

只有**固有频道**会暴露给脚本，而且**只暴露 `channelId`**（不带 UUID、不带地址）：

```zenscript
// 数据频道 → float；能量频道 → long
if (temp.amount < 5000) { ... }
val stored = grid_power.amount;      // long
val same = <channel:temp>.amount;    // 括号语法等价

RecipeBuilder.newBuilder("modularmachinery:alloy_furnace", "heating", 200)
    // 判断逻辑用 MMCE 自己的 handler；闭包**必须标类型**，泛型参数会被擦除成 Object
    .addPostCheckHandler(function(event as mods.modularmachinery.RecipeCheckEvent) {
        if (temp.amount < 5000) {
            event.setFailed("温度不足 5000K");
            return;
        }
    })
    // 每 tick 判断（失败 API 不同）
    .addPreTickHandler(function(event as mods.modularmachinery.RecipeTickEvent) {
        if (temp.amount < 5000) {
            event.preventProgressing("温度不足 5000K");
        }
    })
    // 唯一新增的配方方法：改频道数值
    .addDataConfig("temp", 500, 0, "input", false);
```

* **求值上下文**：机器 tick 里的脚本按「当前机器」解析——私有的固有频道取该机器拥有者的那份实例；没有机器上下文
  （机器逻辑之外的脚本、或连服务器的客户端）时私有频道读 0、公共频道正常读。频道值存在于服务端，脚本要跑在服务端才有值。
* 每次读取都是**当前值**（不是编译期常量），所以 `amount` 可以放心用在每 tick 的判断里。
* `addDataConfig(channelId, amount, operation, ioType, isPerTick)`：写到**该机器拥有者**的那份实例上。

| 参数 | 取值 |
| --- | --- |
| `amount` | 数值；数值字面量类型（`500` / `2L` / `0.5` / `2.0`）都有对应重载 |
| `operation` | `0` 加、`1` 乘、`2` 覆盖（前两个与 MMCE `RecipeModifier` 的常量同名同值） |
| `ioType` | `"input"` = 配方开始（输入已消耗）时写一次；`"output"` = 配方完成（产出已结算）后写一次 |
| `isPerTick` | `true` 时每 tick 写一次（`ioTick` **之前**），并覆盖 `ioType` |

* 能量频道按 `long` 处理（加法饱和、不会变成负数），数据频道按 `float` 处理；参数检查（频道是否存在、`operation` /
  `ioType` 是否合法）在**脚本加载时**报错，不会每 tick 刷日志。

### 频道增益（`RecipeModifier`）

给某条频道声明一组修饰符，接上并启用这条频道的机器就会得到它们：

```zenscript
// 短名可直接用（不写 import 也行），也可以先 import mods.modulargrid.ChannelModifierBuilder;
// 不带参数 = 该频道 masterTo 覆盖到的所有机器
ChannelModifierBuilder.newBuilder()
    .addNewModifier("temp", "modularmachinery:duration", "input", 0.5, 1, false)   // 速度 ×0.5
    .addNewModifier("temp", "modularmachinery:energy", "input", 2.0, 1, false)     // 能耗 ×2
    .build();

// 也可以直接把 MMCE 自己 builder 的产物交进来
ChannelModifierBuilder.newBuilder()
    .addNewModifier("factory_water",
        mods.modularmachinery.RecipeModifierBuilder.create("modularmachinery:duration", "input", 0.75, 1, false).build())
    .build();

// 只给某一台机器（不必去改频道配置里的 masterTo）
ChannelModifierBuilder.newBuilder("modularmachinery:infinity_source")
    .addNewModifier("inf_point", "modularmachinery:duration", "input", 0.5, 1, false)
    .build();

// val 保存的是惰性频道表达式，不是脚本加载时的数值快照
val InfPower = inf_point;

// 配方开始时计算 1 / max(inf_point.amount, 1)，本次配方期间保持不变
ChannelModifierBuilder.newBuilder("modularmachinery:infinity_source")
    .addSampledModifier("inf_point", "modularmachinery:duration", "input",
        InfPower.max(1.0).inverse(), 1, false)
    .build();

// 表达式可组合多个频道和常量；频道对象需放在 + - * / % 的左侧
ChannelModifierBuilder.newBuilder("modularmachinery:infinity_source")
    .addSampledModifier("inf_point", "modularmachinery:energy", "input",
        (inf_point + factory_water) * 1000.0, 0, false)
    .build();
```

* 参数顺序：`channelId` / `target` / `ioType` / `modifier` / `operation` / `chance`，其中 `target`、`ioType`、
  `operation` 的含义与 MMCE 自己的 `RecipeModifierBuilder` 完全一致（`target` 形如 `modularmachinery:duration`，
  `operation` 用 `RecipeModifier.OPERATION_ADD/MULTIPLY` 的数值）。
* `build()` 即完成注册；同一条声明重复注册不会叠加（脚本重载会重新声明，因此按内容去重）。
* **生效条件**：接入点已接入该频道 **且** 该频道未在 GUI 里被本机器停用 **且** `masterTo` 匹配（能量频道忽略
  `masterTo`）**且** 声明里的机器 id（`newBuilder(machineId)`，缺省 = 不限机器）匹配。声明里的机器 id 写法和
  `masterTo` 一样（裸名自动补 `modularmachinery:`）。条件不成立就撤销，所以频道本身就是开关，`masterTo` / 机器 id 就是作用范围。
* 实现上落到控制器的**具名修饰符**（`modulargrid:channel/<id>#<序号>`）：只在集合真的变化时才调用增删，
  因为每次增删都会让 MMCE 重建一次修饰符上下文；机器解体、接入点被拆或所在区块卸载时会撤销自己加的那部分。
* `addSampledModifier` 的数值表达式在 `RecipeStartEvent` 时求值一次（输入已消耗，MMCE 计算本次配方总时长之前），算出的修饰符复制进该配方的 context，配方期间固定；配方结束 context 重置，不会串到下一条配方。本 API **不做每 tick 动态重算**。旧版 `function(amount as double) as double` 重载仍保留兼容，但新声明建议用频道表达式。
* 频道对象本身（例如 `inf_point`）参与 `+ - * / %` 运算会建立惰性表达式；`.amount` 仍是即时数值，不能用 `.amount` 构造延迟表达式。ZenScript 在左操作数类型上解析运算符，因此频道对象要放在算式左边；`1.0F / inf_point` 不可用，倒数写 `inf_point.inverse()` 或 `inf_point.pow(-1.0)`，乘方写 `inf_point.pow(exponent)`。例如 `val InfPower = inf_point;` 保存的是惰性表达式，`val InfPower = inf_point.amount;` 才是脚本当前时刻的数值。
* 表达式中每条频道都必须在本机接入点启用并通过各自的 `masterTo` 限制，否则按 0 参与计算。能量频道在算术表达式中转为 `double`（大于 2^53 的值可能损失整数精度）。表达式得到非有限值（例如对 0 求倒数）或超出 MMCE 的 float modifier 范围时，会记录错误并跳过该 modifier，不会污染本次配方上下文。
* 若逻辑需要在配方进行期间响应频道变化，仍用 MMCE 自己的 handler 读 `amount` 并调用 `setFailed` / `preventProgressing` 等；这与采样式 modifier 是两种不同语义。

### 指令的变化

* `/modulargrid reg <名字> -e`：配置关掉 `allowPlayerEnergyChannels`、或配置已声明固有能量频道时都会被拒绝，提示统一为「你不能注册能量频道！」。
* `/modulargrid reg <名字>`：名字与固有频道 id 相同时被拒绝（脚本按 id 寻址，不允许被玩家频道遮蔽）。
* `/modulargrid del <名字>`：固有频道**不可删除**，会明确提示由整合包定义。
* `/modulargrid list`：自己的频道之后会列出配置给该玩家的固有频道，标为「固有」并按上面的三级回退显示名字。
