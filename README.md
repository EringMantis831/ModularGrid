# ModularGrid

[English](README.en.md) | 中文

ModularGrid 是面向 Minecraft 1.12.2 与 Modular Machinery: Community Edition (MMCE) 的附属 mod：为 MMCE 加入频道系统，并提供新的数据调参方式。

## 环境

- Minecraft 1.12.2
- Cleanroom Loader 0.6.0-alpha
- JDK 25
- MMCE

## 构建

```bash
./gradlew build
```

构建会同时产出：

- `build/libs/ModularGrid-1.12.2-cleanroom-1.0.0.jar`：发布用 SRG jar，可直接放入普通客户端。
- `build/libs/ModularGrid-1.12.2-cleanroom-1.0.0-dev.jar`：MCP 命名，仅供 `run/` 开发实例使用。
- `build/libs/ModularGrid-1.12.2-cleanroom-1.0.0-source.jar`：同一构建的源码包。

## 流通数据节点

`modulargrid:access_plug` 可作为 MMCE 能量仓、Forge Energy 端口和频道接口。支持 IC2、Flux Networks 与 Circulation Flow Networks 可选集成。

## 频道

玩家频道通过 `/modulargrid reg|del|list|set` 管理，按玩家绑定并保存于世界数据。整合包可在 `config/modulargrid/firm_channel.json` 定义固有频道；`masterTo` 必须是机器注册名数组，空数组表示不限制机器。

能量选择优先级：私有固有频道、公共固有频道、自己的玩家频道、其他玩家的公共频道。接入点 GUI 用于启用可访问频道。

## CraftTweaker

脚本可通过频道值配置配方数据，也可使用 `ChannelModifierBuilder` 声明静态或配方开始时采样的增益。惰性表达式以频道对象为左操作数，例如 `inf_point.inverse()`、`inf_point * 0.5`；`1.0 / inf_point` 无效。表达式在配方开始时读取，并固定本次配方的增益值。

完整的 MMCE 接入与实现说明见 [docs/mmce-addon.md](docs/mmce-addon.md)，版本记录见 [CHANGELOG.md](CHANGELOG.md)。
