# ModularGrid

English | [中文](README.md)

ModularGrid is an addon for Minecraft 1.12.2 and Modular Machinery: Community Edition (MMCE). It adds the Data Flow
Access Plug and the channel system behind it.

## Environment

- Minecraft 1.12.2
- Cleanroom Loader 0.6.0-alpha
- JDK 25
- MMCE

## Building

```bash
./gradlew build
```

The build produces three artifacts:

- `build/libs/ModularGrid-1.12.2-cleanroom-1.0.0.jar`: the release jar, remapped to SRG names, ready to drop into a
  normal client.
- `build/libs/ModularGrid-1.12.2-cleanroom-1.0.0-dev.jar`: MCP named, only for the `run/` development instance.
- `build/libs/ModularGrid-1.12.2-cleanroom-1.0.0-source.jar`: the sources of the same build.

## Data Flow Access Plug

`modulargrid:access_plug` stands in for an MMCE energy hatch, works as a Forge Energy port and is the interface to the
channel system. IC2, Flux Networks and Circulation Flow Networks are supported through optional integrations.

## Channels

Player channels are managed with `/modulargrid reg|del|list|set`, are bound to a player and are stored in the world
data. A pack can declare firm channels in `config/modulargrid/firm_channel.json`; `masterTo` must be an array of machine
registry names, and an empty array means every machine.

Energy is taken from, in order: the private firm channel, the shared firm channels, the machine owner's own player
channel, then public channels of other players. The access plug GUI switches the accessible channels on and off.

## CraftTweaker

Scripts can drive recipe data from channel values and can declare gains with `ChannelModifierBuilder`, either
static or sampled when a recipe starts. Lazy expressions take the channel object as their left operand, for example
`inf_point.inverse()` or `inf_point * 0.5`; `1.0 / inf_point` does not work. An expression is read once when a recipe
starts and its gain stays fixed for that craft.

The full MMCE integration and implementation notes live in [docs/mmce-addon.md](docs/mmce-addon.md), the version
history in [CHANGELOG.md](CHANGELOG.md).
