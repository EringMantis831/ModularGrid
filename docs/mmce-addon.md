# MMCE 附属 mod 开发说明（ModularGrid）

ModularGrid 是 [Modular Machinery: Community Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition)（下称 MMCE）的附属 mod。
参考实现：[NovaEngineering-Core](https://github.com/NovaEngineering-Source/NovaEngineering-Core)（NEC，MMCE 的大型附属）。

## 1. 依赖

`gradle/scripts/dependencies.gradle`：

```groovy
modCompileOnly "curse.maven:${propertyString('mmce_curse_file')}"   // MMCE 发行 jar，Unimined 反混淆成 MCP 名供编译
runMods("curse.maven:${propertyString('mmce_curse_file')}") { transitive = false }
runMods("curse.maven:${propertyString('crafttweaker_curse_file')}") { transitive = false }
```

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
| TileEntity | `com.modulargrid.common.tile.TileAccessPlug`（继承 `TileColorableMachineComponent`，行为待补） |
| 创造模式标签页 | MMCE 的 `CommonProxy.creativeTabModularMachinery` |
| 注册 | `RegistryBlocks` / `RegistryItems`（`RegistryEvent.Register` 事件式） |

继承 `BlockMachineComponent` 后它就是一个合法的 MMCE 机器结构组件：可以在机器 JSON 的结构里使用，并且吃机器颜色（`Config.machineColor` / 单方块 `casingColor`）。

`TileAccessPlug` 目前是空实现，只挂在 `access_plug` 位置；后续“流通数据”的逻辑加在这里（网络/数据流接入、缓存、同步等）。

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

* 用户 AT 文件必须写 **MCP 名**（Unimined 会再映射回 SRG）；这里的内容是 CraftTweaker 的 AT 从 SRG 转成 MCP 后的结果。
* 文件里**不能出现只有 `#` 的行**（纯注释行会导致 AT 工具报 `Invalid access transformer line ...: #` 并让配置失败），`# 注释文字` 这种是允许的。

往 `run/<side>/mods` 里加了自带 AT 的 mod 时，把它的 AT 一并转换后追加到这里。
