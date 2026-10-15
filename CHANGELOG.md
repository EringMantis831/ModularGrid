# Changelog

## [1.0.0] - 2026-10-10

### Fixed
- Guard IC2 energy-net registration and removal calls when IC2 is absent, avoiding Cleanroom linkage errors

### Added
- Cleanroom Loader 0.6.0 based 1.12.2 mod development workspace on Java 25
- Modular Machinery: Community Edition addon setup (MMCE 2.3.2 + CraftTweaker)
- `access_plug` block: 流通数据节点 / Data Flow Access Plug, built as casing + overlay model like the other MMCE machine components
- The access plug is a general energy hatch in its own right: Forge Energy on all six faces (push in / pull out) plus an
  active push into its neighbours every tick, so machines can share a channel without cables
- Any position that accepts a Modular Machinery energy hatch also accepts the access plug, so machine JSONs do not
  have to be edited to use it
- A plug that is not part of a machine works as a channel port for the player that placed it
- Optional IndustrialCraft 2 support: the access plug joins IC2's energy net as both a sink and a source
  (1 EU = 4 RF, tier 6 / LuV, the same conversion Modular Machinery's own energy hatch uses)
- Optional Flux Networks support: the access plug is a storage connector of the network itself, so it needs no flux
  plug or point next to it - a network charges its channel and draws from it (change reported per connector, so plugs
  sharing a channel are not counted twice)
- The access plug's tooltip states what the block does: unlimited FE/t in and out, its IndustrialCraft 2 voltage
  tier when that mod is there, which of the supported networks it becomes a device of, and that a right click opens its
  channel panel
- Firm channels: a pack defines channels in `config/modulargrid/firm_channel.json` (shared, or one instance per player;
  energy pool or data pool; optionally limited to certain machines), addressed internally as `channelId` or
  `channelId@player`. They are global to the save, cannot be deleted by players, do not count against a player's 16
  channel limit, and are reread together with a Modular Machinery reload
- A per player firm energy channel is that player's energy channel, so it occupies their single energy channel slot
- CraftTweaker scripts read firm channels as `channelId.amount` (`float`, or `long` for an energy channel) and as
  `<channel:channelId>.amount`; a per player channel resolves to the instance of the machine whose logic runs, and
  without a machine context it reads zero instead of somebody else's value
- `addDataConfig(channelId, amount, operation, ioType, isPerTick)` on Modular Machinery's recipe builder writes a channel
  when a recipe starts, when it finishes or every tick, reusing Modular Machinery's own handlers
- `ChannelModifierBuilder.newBuilder(machineId)` limits a declaration to one machine, so a pack can give the same
  channel different modifiers per machine without touching the channel's `masterTo` list
- `addSampledModifier` accepts lazy channel expressions such as `inf_point.inverse()` and `inf_point * 0.5`; it
  evaluates them once when a recipe starts, keeping the sampled value fixed for that recipe
- `ChannelModifierBuilder` lets a pack declare the recipe modifiers a channel provides: they are applied to a machine
  while the plug is part of it and the channel is enabled there, and taken back when either stops being true
- A firm energy channel replaces the energy channels players registered themselves: as soon as the configuration
  declares one, existing player energy channels are dropped from the save (reported in the log) and
  `/modulargrid reg <name> -e` is refused, so the pack decides where the energy of its machines lands
- Which energy channel a machine actually runs on is visible: the row of the channel in use is marked in the plug's GUI,
  the details panel names it as the machine's energy source, and every change is logged with the channel it moved to
- Access plugs do not join a public channel by default: another player's public channel, and a shared firm channel, have
  to be switched on in the plug's GUI, while the owner's own channels stay on until switched off
- `/modulargrid list` additionally lists the firm channels of the pack, marked as firm and named by the pack, and
  `/modulargrid del` refuses to delete one
- Optional Circulation Flow Networks support: the access plug registers its own energy handler (priority above the
  mod's generic Forge Energy handler) and joins the network's own node lifecycle as a port node, so network nodes pick
  it up as a storage device and move `long` amounts without a pocket node next to it
- Channel system: player bound, world wide channels with `/modulargrid reg <name> [-p] [-e]`, `del`, `list` and `set`
- Energy channels hold stored energy as a `long` (`1 RF = 1`, exact over the whole range), every other channel holds a `float`
- Energy channels act as the machine's energy source and sink: input hatches are filled from the channel
  and output hatches are emptied into it, so a machine runs purely on its owner's channel
- Access plug GUI (reuses Modular Machinery's factory controller background) listing the usable channels
  with a per machine on/off switch
- Access plug GUI buttons are drawn from sprites in vanilla style with three states each (normal, disabled, blue
  highlight), the state belongs to one row so clicking a row never restyles the others, and the Flux Networks row is a
  single long button of the same kind
- Channel values are shown in two forms: a channel row shortens values of more than ten digits to `N.NNeNN` so the unit
  still fits, while the details panel always shows the exact value (at most four decimals)
- Channel text keeps fixed colours (name, type/owner, value) and the numbers refresh at 4 Hz while the GUI is open
- This is a default template changelog that follows the [KeepAChangelog Convention](https://keepachangelog.com/en/1.1.0/)
