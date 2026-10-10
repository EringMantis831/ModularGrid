# ModularGrid — Cleanroom 1.12.2 模组开发环境

基于 [CleanroomMC/ForgeDevEnv](https://github.com/CleanroomMC/ForgeDevEnv) 模板改造，把其中的
**RetroFuturaGradle + Forge 14.23.5.2847** 工具链替换为 **Unimined（Cleanroom 分支）+ Cleanroom Loader 0.6.0**，
编译目标为 **Java 25**。

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.12.2 |
| Cleanroom Loader | `0.6.0-alpha` |
| Unimined (Cleanroom fork) | `1.4.43-kappa`（仓库 `https://maven.arcseekers.com/releases`） |
| Gradle | 9.8.0（wrapper 自带） |
| JDK / toolchain | 25 |
| MCP 映射 | `stable` / `39-1.12` |
| 内置 Mixin | CleanMix 0.4.6（Mixin 0.8.7 分支，随 Cleanroom 附带，无需额外依赖） |
| LWJGL | 3.4.1（Cleanroom 自带） |
| 目标 mod | **Modular Machinery: Community Edition** `2.3.2`（+ 其硬依赖 CraftTweaker `4.1.20.715`） |

本仓库已经是一个 **MMCE 附属 mod**（见 [docs/mmce-addon.md](docs/mmce-addon.md)），已实现方块
`modulargrid:access_plug`（流通数据节点 / Data Flow Access Plug）。

> 注意：`Cleanroom 0.6.x` 只能跑在 Java 21+，本工作区按你的要求固定为 **JDK 25**。

---

## 1. 环境要求

* **JDK 25**（Gradle 自身也要跑在 JDK 25 上）。
  * 本机已验证路径：`C:\Program Files\Java\jdk-25.0.4.1`
* 如果 Gradle 找不到 JDK 25，任选一种方式：
  * 设置 `JAVA_HOME` 指向 JDK 25；或
  * 在 `gradle.properties` 里取消注释并修改：
    ```properties
    org.gradle.java.installations.paths=C:/Program Files/Java/jdk-25.0.4.1
    ```
  * `settings.gradle` 里已经启用了 `foojay-resolver`，缺失的 toolchain 会自动下载。

命令行示例（Git Bash）：

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-25.0.4.1"
./gradlew build
```

---

## 2. 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew build` | 编译 + 打包，产物在 `build/libs/` |
| `./gradlew runClient` | 启动开发环境客户端 |
| `./gradlew runServer` | 启动开发环境服务端 |
| `./gradlew genSources` | 反编译/生成 Minecraft 源码（供 IDEA 跳转查看） |
| `./gradlew publishToMavenLocal` | 发布到本地 Maven 仓库 |
| `./gradlew cleanroomAfterSync` | 生成 `Tags` 类与缺失的 Mixin 配置（IDEA 同步后自动执行） |

产物（`build/libs/`）：

* `modulargrid-1.0.0.jar` —— **重映射后的正式产物**（SRG 名），发布/放进 `mods` 目录用这个
* `modulargrid-1.0.0-dev.jar` —— 开发用未重映射 jar（MCP 名），不要发布
* `modulargrid-1.0.0-sources.jar` —— 源码包

服务端首次运行需要同意 EULA：在 `run/server/eula.txt` 写入 `eula=true`。

---

## 3. IDEA 使用

1. 用 IDEA 打开项目根目录。
2. `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM` 选择 **JDK 25**。
3. 右键 `build.gradle` → `Link Gradle Project`，然后在 Gradle 面板 `Refresh All`。
4. 运行配置直接用右边 Gradle 面板里 `idea-ext` 生成的
   `1. Run Client` / `2. Run Server` / `3. Build` / `4. Gen Sources`。
   * **不要**用 IDEA 自动生成的蓝图标 `Minecraft Client` 配置（已通过 `genIntellijRuns` 禁用，它不支持 Cleanroom）。
5. 写 Mixin 建议安装 [MinecraftDev (RetroFuturaGradle fork)](https://github.com/eigenraven/MinecraftDev/releases)。

---

## 4. 改这里就等于改你的 mod

全部集中在 `gradle.properties`：

```properties
mod_version = 1.0.0
root_package = com.modulargrid     # group，也是 Java 包根
mod_id       = modulargrid
mod_name     = ModularGrid
mod_authors  =                      # 逗号分隔
mod_description =
mod_url      =
mod_logo_path =
```

包名 / 目录对应关系：

```
src/main/java/com/modulargrid/
├── ModularGrid.java                                  <- 主类（@Mod）
├── client/ClientProxy.java                           <- 模型 + 颜色注册
├── common/CommonProxy.java                           <- 注册事件挂载
├── common/block/BlockAccessPlug.java                 <- 流通数据节点
├── common/tile/TileAccessPlug.java                   <- 其 TileEntity（行为待补）
├── common/registry/RegistryBlocks.java               <- 方块/方块物品/TileEntity
└── common/registry/RegistryItems.java                <- 物品

src/main/resources/
├── mcmod.info / pack.mcmeta                          <- 由 gradle.properties 自动填充
├── modulargrid_at.cfg                                <- 自己的访问转换器示例
└── assets/modulargrid/
    ├── blockstates/access_plug.json
    ├── models/block/{access_plug,blockmodel_overlay,blockmodel_overlay_all}.json
    ├── models/item/access_plug.json
    ├── textures/blocks/access_grid.png               <- overlay 贴图（外壳沿用 MMCE 的）
    └── lang/{en_us,zh_cn}.lang

gradle/dev_access_transformers.cfg                    <- 仅开发环境生效的 AT（见文档）
tags.properties                                      <- 生成 com.modulargrid.Tags 的键值
```

`Tags` 类是构建时由 `generateTags` 任务根据 `tags.properties` 生成到
`build/generated/sources/tags/java/main`，`compileJava` 会自动依赖它。

---

## 5. 功能开关（`gradle.properties`）

| 开关 | 默认 | 说明 |
| --- | --- | --- |
| `cleanroom_version` | `0.6.0-alpha` | Cleanroom Loader 版本，改这里换版本 |
| `use_mixins` | `true` | Mixin 支持。**见 §7 的 0.6.0 兼容性说明** |
| `mixin_configs` | `mixins.${mod_id}.json` | 会被写进 jar manifest 的 `MixinConfigs` |
| `mixin_package` | `${root_package}.mixin` | 自动生成 Mixin 配置时的包名 |
| `generate_mixins_json` | `false` | 为 true 时缺少的 Mixin 配置文件会自动生成 |
| `use_access_transformer` | `false` | 访问转换器；开启后读取 `src/main/resources/${mod_id}_at.cfg` |
| `is_coremod` / `coremod_plugin_class_name` | `false` / 空 | coremod，需实现 `IFMLLoadingPlugin` |
| `use_asset_mover` | `false` | 引入 [AssetMover](https://github.com/CleanroomMC/AssetMover) |
| `enable_shadow` | `false` | 打独立 shaded jar（`shadow` 配置） |
| `enable_lwjglx` | `true` | 提供 LWJGL2 兼容 API（`com.cleanroommc:lwjlx`） |
| `minecraft_username` | `Developer` | 开发环境用户名 |
| `extra_jvm_args` | 空 | 追加 JVM 参数（空格分隔） |
| `extra_tweak_classes` | 空 | 追加 `--tweakClass`（空格分隔） |
| `enable_foundation_debug` | `false` | 打印 Foundation 详细日志 |

依赖写在 `gradle/scripts/dependencies.gradle`，常用配置：

* `modImplementation` / `modCompileOnly` / `modRuntimeOnly` —— 会做 SRG→MCP 重映射的 mod 依赖（**推荐**）
* `implementation` / `compileOnly` / `runtimeOnly` —— 普通依赖
* `contain` —— 打进 jar（Cleanroom 的 `ContainedDeps`，jar-in-jar）
* `shadow` —— 用 shadow 插件合并进 shaded jar

CurseMaven 与 Modrinth 仓库已在 `dependencies.gradle` 中配置好，例如：

```groovy
modImplementation 'curse.maven:jei-238222:5101365'
modCompileOnly    'maven.modrinth:jei:4.16.1.1000'
```

发布相关（CurseForge / Modrinth / Maven）在 `gradle/scripts/publishing.gradle`，默认全部关闭。

---

## 6. 相对 ForgeDevEnv 模板的改动

ForgeDevEnv 原本是 **RFG（RetroFuturaGradle）+ Forge 14.23.5.2847**，而 RFG 并没有 Cleanroom 支持
（RFG 源码中没有任何 cleanroom 相关代码），所以工具链换成了官方 Cleanroom 模板用的
**Unimined Cleanroom 分支**：

| 项 | ForgeDevEnv | 现在 |
| --- | --- | --- |
| Gradle 插件 | `com.gtnewhorizons.retrofuturagradle` 2.0.3 | `xyz.wagyourtail.unimined` 1.4.43-kappa |
| 运行目标 | Forge 14.23.5.2847 | Cleanroom 0.6.0-alpha（`unimined.minecraft.cleanroom.loader`） |
| Java 编译级别 | Java 8 + Jabel（`use_modern_java_syntax`） | Java 25（`java_version`） |
| 模版替换 | RFG `injectTags`（`tags.properties`） | 自写 `generateTags` 任务，仍读 `tags.properties` |
| 资源填充 | `processResources` + `expand` | 保留（`mcmod.info` / `pack.mcmeta`） |
| Mixin 依赖 | `zone.rong:mixinbooter` + `modUtils.enableMixins` | Cleanroom 自带 CleanMix，只需 manifest `MixinConfigs` + `crl.dev.mixin` |
| Mixin refmap | RFG 生成 `mixins.${mod_id}.refmap.json` | Unimined `remapJar { mixinRemap { ... } }` 自动生成 |
| 重混淆任务 | `reobfJar` | `remapJar`（发布脚本已同步修改） |
| 混淆运行 | `runObfClient` / `runObfServer` | 无（Unimined 无对应任务） |
| 依赖去混淆 | `rfg.deobf(...)` | 不需要，直接用 `modImplementation` 等 |

`gradle/scripts/helpers.gradle`（`propertyString` / `assertProperty` / `setDefaultProperty`…）、
发布脚本风格、`.github/workflows/deploy.yml` 等 ForgeDevEnv 的东西都保留了。

---

## 7. Cleanroom 0.6.0-alpha 的已知坑（重要）

`0.6.0-alpha` 的 `CleanroomModDiscoverer#scan` **不能处理目录形式的 classpath 条目**
（源码里那段 `if (!isArchive(...)) return;` 被注释掉了，0.6.1+ 才修好）。

Unimined 会把项目输出目录 `build/classes/java/main`、`build/resources/main`
通过 `-Dcrl.dev.extrapath` 交给 Cleanroom，于是：

* Cleanroom 会把它当压缩包打开 → 报
  `Failed to read mod metadata from main (拒绝访问)` + `Found 0 discovered mod ids`；
* 由于 mod 没有被“发现”，`crl.dev.mixin` 指定的 Mixin 配置在
  `MixinBootstrap.init()` 阶段读不到 → `MixinInitialisationError: ... was invalid or could not be read`。

另外，Cleanroom 把 `crl.dev.extrapath` 加入 `LaunchClassLoader` 的时机是在
`FMLLaunchHandler#setupHome` 里 **`MixinBootstrap.init()` 之后**，所以只靠 `crl.dev.extrapath`
路径也无法在 Mixin 初始化时被读到。

本工作区在 `use_mixins = true` 时自动做了两件事（见 `build.gradle` 的「Development runs」段）：

1. 把 `-Dcrl.dev.extrapath` 指向 `build/libs/<mod>-<ver>-dev.jar`（一个真正的 jar，能被扫描 → mod 被发现）；
2. 同时把这个 dev jar 加到运行 classpath 上（`MixinBootstrap.init()` 时就能读到 Mixin 配置），
   运行任务自动 `dependsOn jar`。

已在本地验证：`runServer` 中 Mixin 成功注入 `MinecraftServer#loadAllWorlds`。
这两条报错（`Failed to read mod metadata...`）会随之消失。

> 如果你把 `cleanroom_version` 升到 `0.6.1-alpha` 或更高（当前最新 `0.6.13-alpha`），
> 目录问题会被官方修掉，但上面的「dev jar」逻辑仍然安全可用，可以保留。

---

## 8. Mixin 用法

示例已经放在仓库里并默认启用：

```
src/main/java/com/modulargrid/mixin/MinecraftServerMixin.java   <- 示例 mixin
src/main/resources/mixins.modulargrid.json                      <- 配置（package 指向 com.modulargrid.mixin）
```

新增一个 Mixin：

1. 在 `com.modulargrid.mixin` 包下写类：

   ```java
   @Mixin(Minecraft.class)
   public class MinecraftMixin {
       @Inject(method = "createDisplay", at = @At("HEAD"))
       private void mymod$createDisplay(CallbackInfo ci) {
           ExampleMod.LOGGER.info("Mixin works!");
       }
   }
   ```

2. 把类名加到 `mixins.modulargrid.json` 的 `mixins` / `client` / `server` 列表里；
3. `gradle.properties` 里保持 `use_mixins = true`（多份配置用空格分隔写在 `mixin_configs`）。

`remapJar` 会自动生成 refmap，产物 jar 的 manifest 会带上 `MixinConfigs`，
CleanMix 在正式环境中直接读取，不需要 MixinBooter 依赖，也不需要手写 refmap 名。

---

## 9. 升级 Cleanroom 版本

改一行即可：

```properties
cleanroom_version = 0.6.13-alpha
```

然后 `./gradlew --refresh-dependencies build`。
（最新版本见 <https://github.com/CleanroomMC/Cleanroom/releases> 或
<https://maven.cleanroommc.com/com/cleanroommc/cleanroom/maven-metadata.xml>）

---

## 10. 常见问题

* **`Cannot mutate content repository descriptor 'modsRemap(...)'`**
  说明在 Unimined 的 `mods {}` 之后又用了 `exclusiveContent {}` 添加仓库。
  本工作区的 `dependencies.gradle` 已改成普通 `maven {}` 声明；自己加仓库时也请用普通 `maven {}`。
* **`ClassCastException: ... MMCELateMixinLoader cannot be cast to zone.rong.mixinbooter.ILateMixinLoader`**
  mod 被放在运行 classpath（app class loader）而不是 launch class loader 上。MMCE 这类 coremod 必须放进
  `run/<side>/mods`（本仓库的 `runMods` + `copyRunMods*` 任务就是这么做的）。
* **`NoSuchMethodError: JsonParser.parseReader` / 巨量 `Unable to read a class file correctly`**
  有 mod 从 Maven 带进了旧版 `gson` / `asm-debug-all:5.2`，遮蔽了 Cleanroom 自带的现代版本。
  从 classpath 上排除它们（本仓库不再把 mod 依赖放 classpath，改为放 `run/<side>/mods`）。
* **`Invalid access transformer line ...: #`（配置阶段就失败）**
  AT 文件里出现了只有 `#` 的纯注释行，删掉即可。
* **`IllegalAccessError: tried to access private field ...`（例如 CraftTweaker 的 `LanguageMap.instance`）**
  Cleanroom 不会在开发环境把 mod 自带的 SRG 名 AT 映射成 MCP 名。用
  `gradle/dev_access_transformers.cfg` 补（见 [docs/mmce-addon.md](docs/mmce-addon.md) §4）。
* **客户端日志里一堆 `modularmachinery:xxx_controller` 模型加载失败**
  MMCE 的“自定义控制器”模型是运行时生成的，开发环境没生成就会有这些报错，与附属 mod 无关。
* **`Failed to read mod metadata from <目录>`**
  见 §7。开启 `use_mixins` 后本工作区已自动规避；不开 Mixin 时该报错无害，mod 仍能正常加载。
* **Gradle 找不到 JDK 25**
  见 §1。
* **`runObfClient` / `runObfServer` 不存在**
  Unimined 没有混淆运行任务，用 `remapJar` 产出的 jar 放进真实客户端/服务端的 `mods` 目录测试即可。
* **想同时用 Forge 和 Cleanroom 测试**
  本环境是 Cleanroom 环境；因为 Cleanroom 兼容约 99% 的 Forge mod，直接按 Forge 写法开发通常没问题。

---

## 11. 许可证 / 来源

* 模板来源：[CleanroomMC/ForgeDevEnv](https://github.com/CleanroomMC/ForgeDevEnv)（MIT），
  本工作区已按上文改造，原 `.git` 历史已清空以便你作为自己的项目开始。
* 工具链：[Unimined Cleanroom fork](https://github.com/CleanroomMC/CleanroomGradle)、
  [Cleanroom Loader](https://github.com/CleanroomMC/Cleanroom)。
* 模板本体沿用 MIT（见 `LICENSE`）。
