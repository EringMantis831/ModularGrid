package com.modulargrid.common.tile;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.block.AccessPlugProvider;
import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.channel.ChannelInfo;
import com.modulargrid.common.channel.ChannelRegistry;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.channel.FirmChannelDefinition;
import com.modulargrid.common.integration.crafttweaker.ChannelModifierRegistry;
import com.modulargrid.common.integration.fluxnetworks.AccessPlugFluxConnector;
import com.modulargrid.common.integration.fluxnetworks.AccessPlugFluxState;
import com.modulargrid.common.integration.fluxnetworks.FluxNetworksIntegration;
import com.modulargrid.common.integration.circulation.CirculationIntegration;
import com.modulargrid.common.util.EnergyIo;
import github.kasuminova.mmce.common.tile.base.MachineCombinationComponent;
import hellfirepvp.modularmachinery.common.crafting.helper.ProcessingComponent;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import hellfirepvp.modularmachinery.common.tiles.base.TileEntityRestrictedTick;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.IEnergyHandler;
import hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 流通数据节点 / Data Flow Access Plug tile.
 *
 * <p>The plug is the machine's connection point to the player's {@link Channel channels}. It joins
 * the multiblock as an energy component pair (input + output) whose storage <i>is</i> the owning
 * player's energy channel, so a machine can run without any physical energy hatch, and it keeps a
 * per machine set of channels the owner switched off in its GUI.</p>
 *
 * <p>Binding to the controller is established by {@code TileMultiblockMachineControllerMixin}, see
 * {@link AccessPlugProvider}. The binding is validated lazily: as soon as the controller is gone or
 * its structure is broken the plug lets go of it, which also means "no channel IO while the machine
 * is not formed".</p>
 *
 * <p>The plug is also an energy hatch in its own right: it exposes Forge Energy
 * ({@link CapabilityEnergy#ENERGY}) on all six faces, so external cables and machines can push
 * energy into the channel and pull energy out of it, and it pushes energy into its neighbours every
 * tick like Modular Machinery's own output hatch does. When IndustrialCraft 2 is installed it
 * additionally joins IC2's energy net as both a sink and a source, so EU cables can do the same.</p>
 *
 * <p>With Flux Networks installed the plug is a storage connector of that mod as well, see
 * {@link AccessPlugFluxConnector}: the interface is declared here so the plug can be looked up as a
 * connector, its implementation lives in the integration package and is only loaded while the mod is
 * present.</p>
 */
@Optional.InterfaceList({
        @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = "ic2"),
        @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySource", modid = "ic2"),
        @Optional.Interface(iface = "com.modulargrid.common.integration.fluxnetworks.AccessPlugFluxConnector",
                modid = "fluxnetworks"),
        @Optional.Interface(iface = "com.circulation.circulation_networks.api.INodeBlockEntity",
                modid = "circulation_networks")
})
public class TileAccessPlug extends TileEntityRestrictedTick
        implements MachineComponentTile, MachineCombinationComponent, AccessPlugProvider, IEnergyStorage,
        IEnergySink, IEnergySource, AccessPlugFluxConnector,
        com.circulation.circulation_networks.api.INodeBlockEntity<com.circulation.circulation_networks.network.nodes.PortNode> {

    /**
     * Whether IC2 is installed. The IC2 methods of this class carry {@code @Optional.Method}, and Cleanroom
     * removes such a method together with its body when the mod is missing, so calling one is only safe here.
     */
    private static final boolean IC2_LOADED = Loader.isModLoaded("ic2");

    /** Namespace of the modifiers a channel applies, see {@code addPermanentModifier}. */
    private static final String MODIFIER_NAME_PREFIX = "modulargrid:channel/";

    private static final String NBT_DISABLED_CHANNELS = "disabledChannels";
    private static final String NBT_ENABLED_CHANNELS = "enabledChannels";
    private static final String NBT_CHANNELS = "channels";
    private static final String NBT_FORMED = "formed";
    private static final String NBT_PLACER = "placer";

    /** A public fallback channel is only searched again after this many ticks. */
    private static final int CHANNEL_RESCAN_INTERVAL = 20;

    /** How often the GUI copy of the channel list is rebuilt. */
    private static final int CHANNEL_LIST_REFRESH_INTERVAL = 10;

    /**
     * How often the GUI copy is rebuilt while somebody has the GUI open: 4 times a second, so the numbers of the
     * channel the player is looking at move along with the machine instead of jumping once a second.
     */
    private static final int VIEWER_REFRESH_INTERVAL = 5;

    /** How often disabled addresses of channels that no longer exist are dropped. */
    private static final int DISABLED_PRUNE_INTERVAL = 100;

    /**
     * IndustrialCraft 2 conversion, matching Modular Machinery's own hatches: 1 EU is worth 4 RF.
     */
    private static final int RF_PER_EU = 4;

    /** IC2 voltage tier used both ways, 6 = LuV (32768 EU/t). */
    private static final int IC2_TIER = 6;

    /** EU per tick this plug moves, the same value Modular Machinery uses for its top hatch tier. */
    private static final double IC2_TRANSFER_LIMIT = 1 << ((IC2_TIER + 1) * 2 + 1);

    /**
     * Components handed to the multiblock. They are built once and cached: Modular Machinery keys its
     * component map by tile entity, so both components need a distinct, <b>stable</b> group id or
     * they would overwrite each other on every structure rebuild.
     */
    private final List<MachineComponent<?>> components;

    private final ChannelEnergyHandler energyHandler = new ChannelEnergyHandler();

    /** Addresses ({@code ownerUUID/name}) of the channels the owner switched off for this machine. */
    private final Set<String> disabledChannels = new LinkedHashSet<>();

    /**
     * Addresses of the channels the owner switched <b>on</b> for this machine. A channel that does not belong to the
     * owner - another player's public channel or a shared channel the pack declares - is off until it shows up here:
     * a machine never silently drains somebody else's pool, sharing is opt in.
     */
    private final Set<String> enabledChannels = new LinkedHashSet<>();

    @Nullable
    private TileMultiblockMachineController controller;

    /** Resolved on the server thread, read by the (possibly asynchronous) recipe thread. */
    @Nullable
    private volatile Channel activeEnergyChannel;

    /** Player that placed this block, the channel owner while the plug is not part of a machine. */
    @Nullable
    private UUID placer;

    private long nextChannelScan = 0L;
    private volatile boolean energyValueDirty = false;

    /**
     * Snapshot of the accessible channels, used by the GUI: built on the server and sent to the client
     * through the tile entity network NBT.
     */
    private List<ChannelInfo> syncedChannels = Collections.emptyList();

    private boolean syncedFormed = false;

    /** Players with this plug's GUI open, they are the only ones that need value updates. */
    private final Set<UUID> viewers = new HashSet<>();

    private long nextChannelListRefresh = 0L;
    private long nextDisabledPrune = 0L;
    private boolean networkDirty = false;

    /** Set once the plug has announced itself to IC2's energy net. */
    private boolean energyNetRegistered = false;

    /** Flux Networks settings and transfer state, created on first use while that mod is installed. */
    @Nullable
    private AccessPlugFluxState fluxState;

    /** Network the plug is in, as shown in its GUI (sent to the client with the tile entity update). */
    private int syncedFluxNetworkID = -1;
    private String syncedFluxNetworkName = "";

    /** CFN network node, stored as Object so the optional API stays out of non-CFN verification. */
    @Nullable
    private Object circulationNode;
    private boolean circulationNodeRegistered;

    public TileAccessPlug() {
        List<MachineComponent<?>> built = new ArrayList<>(2);
        built.add(new ChannelEnergyHatch(IOType.INPUT, getUniqueGroupID()));
        built.add(new ChannelEnergyHatch(IOType.OUTPUT, getUniqueGroupID()));
        this.components = Collections.unmodifiableList(built);
    }

    // -------------------------------------------------------------- components

    /**
     * The plug joins the multiblock through {@link #provideComponents()} only, a single component
     * would clash with the second one in Modular Machinery's tile entity keyed component map.
     */
    @Nullable
    @Override
    public MachineComponent<?> provideComponent() {
        return null;
    }

    @Override
    public Collection<MachineComponent<?>> provideComponents() {
        return components;
    }

    /** Energy component whose storage is the player's energy channel. */
    private final class ChannelEnergyHatch extends MachineComponent.EnergyHatch {

        private final long groupId;

        private ChannelEnergyHatch(IOType ioType, long groupId) {
            super(ioType);
            this.groupId = groupId;
        }

        @Override
        public IEnergyHandlerAsync getContainerProvider() {
            return energyHandler;
        }

        @Override
        public long getGroupID() {
            return groupId;
        }
    }

    // ------------------------------------------------------------------ binding

    @Override
    public void boundMachine(TileMultiblockMachineController controller) {
        if (this.controller != controller) {
            this.controller = controller;
            this.nextChannelScan = 0L;
            ModularGrid.LOGGER.info("Access plug at {} bound to the machine at {}", getPos(), controller.getPos());
        }
    }

    /** @return the controller this plug belongs to, {@code null} while the machine is not formed */
    @Nullable
    public TileMultiblockMachineController getController() {
        return controller;
    }

    /** @return the UUID of the machine owner, {@code null} if the machine is unknown or unowned */
    @Nullable
    public UUID getMachineOwner() {
        TileMultiblockMachineController machine = controller;
        if (machine == null || machine.isInvalid()) {
            return null;
        }
        return machine.getOwner();
    }

    /** @return {@code true} if the given player owns the machine this plug belongs to */
    public boolean isOwner(EntityPlayer player) {
        UUID owner = getMachineOwner();
        return owner != null && owner.equals(player.getUniqueID());
    }

    /**
     * @return whether the player owns this plug in the wider sense: the owner of the machine it is part of, or - while
     *         it is not part of one - the player that placed it. A loose plug is a channel port of its placer, so that
     *         player is the one who configures it, for example its Flux Networks network.
     */
    public boolean isPlugOwner(EntityPlayer player) {
        UUID owner = getChannelOwner();
        return owner != null && owner.equals(player.getUniqueID());
    }

    /**
     * @return the player whose channels this plug works with: the owner of the machine it is part of,
     *     or the player that placed it while it is not bound to a formed machine, in which case the
     *     plug is a plain channel port
     */
    @Nullable
    public UUID getChannelOwner() {
        UUID owner = getMachineOwner();
        return owner != null ? owner : placer;
    }

    /** Remembers the player that placed this block, see {@link #getChannelOwner()}. */
    public void setPlacer(@Nullable UUID placer) {
        if (!Objects.equals(this.placer, placer)) {
            this.placer = placer;
            invalidateChannelCache();
            markDirty();
            markChunkDirty();
            networkDirty = true;
        }
    }

    // ------------------------------------------------------------------- ticking

    @Override
    public void doRestrictedTick() {
        if (getWorld() == null || getWorld().isRemote) {
            return;
        }
        validateController();
        refreshEnergyChannel();
        applyChannelModifiers();
        manageEnergyHatches();
        pushEnergyToNeighbors();
        if (IC2_LOADED) {
            registerWithEnergyNet();
        }
        if (FluxNetworksIntegration.isAvailable()) {
            FluxNetworksIntegration.tick(this);
        }
        if (CirculationIntegration.isAvailable()) {
            CirculationIntegration.validateNode(this);
        }

        long now = getWorld().getTotalWorldTime();
        if (now >= nextDisabledPrune) {
            nextDisabledPrune = now + DISABLED_PRUNE_INTERVAL;
            pruneChannelSwitches();
        }
        if (now >= nextChannelListRefresh) {
            nextChannelListRefresh = now
                    + (viewers.isEmpty() ? CHANNEL_LIST_REFRESH_INTERVAL : VIEWER_REFRESH_INTERVAL);
            refreshSyncedChannels();
        }
        if (energyValueDirty) {
            energyValueDirty = false;
            ChannelRegistry.markChanged(getWorld());
        }
        if (networkDirty) {
            networkDirty = false;
            markForUpdateSync();
        }
    }

    /**
     * Drops switched addresses whose channel is gone. {@code ChannelCleanup} already sweeps the loaded
     * plugs when a channel is deleted, this covers plugs that were not loaded back then.
     */
    private void pruneChannelSwitches() {
        if (disabledChannels.isEmpty() && enabledChannels.isEmpty()) {
            return;
        }
        boolean changed = pruneSwitchSet(disabledChannels);
        changed |= pruneSwitchSet(enabledChannels);
        if (changed) {
            invalidateChannelCache();
            markDirty();
            networkDirty = true;
        }
    }

    /**
     * @param addresses a switch set of this plug
     * @return whether an address was dropped
     */
    private boolean pruneSwitchSet(Set<String> addresses) {
        boolean changed = false;
        Iterator<String> iterator = addresses.iterator();
        while (iterator.hasNext()) {
            if (ChannelRegistry.getByAddress(getWorld(), iterator.next()) == null) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Rebuilds the snapshot the GUI shows. A new copy is only pushed to viewers when the list itself
     * changed or when somebody is looking at it and the displayed values moved on.
     */
    private void refreshSyncedChannels() {
        syncedFormed = controller != null && !controller.isInvalid() && controller.isStructureFormed();
        UUID owner = getMachineOwner();
        List<ChannelInfo> previous = syncedChannels;
        if (owner == null) {
            if (!previous.isEmpty()) {
                syncedChannels = Collections.emptyList();
                networkDirty = true;
            }
            return;
        }

        List<Channel> accessible = ChannelRegistry.listAccessible(getWorld(), owner);
        String machineName = getFormedMachineName();
        List<ChannelInfo> infos = new ArrayList<>(accessible.size());
        for (Channel channel : accessible) {
            if (!allowsThisMachine(channel, machineName)) {
                // the pack declared this channel for other machines, so it is neither shown nor usable here
                continue;
            }
            infos.add(new ChannelInfo(channel, isChannelEnabled(channel), isActiveEnergyChannel(channel)));
        }
        boolean structureChanged = infos.size() != previous.size();
        if (!structureChanged) {
            for (int i = 0; i < infos.size(); i++) {
                if (!infos.get(i).sameStructure(previous.get(i))) {
                    structureChanged = true;
                    break;
                }
            }
        }
        boolean valuesChanged = false;
        if (!structureChanged) {
            for (int i = 0; i < infos.size(); i++) {
                // both forms are compared: the details panel shows digits the row may have shortened away
                ChannelInfo updated = infos.get(i);
                ChannelInfo previousInfo = previous.get(i);
                if (!updated.getValueDisplay().equals(previousInfo.getValueDisplay())
                        || !updated.getExactValueDisplay().equals(previousInfo.getExactValueDisplay())) {
                    valuesChanged = true;
                    break;
                }
            }
        }
        syncedChannels = infos;
        if (structureChanged || (valuesChanged && !viewers.isEmpty())) {
            networkDirty = true;
        }
    }

    /**
     * @return whether the pack declared the channel for this machine. Energy channels have no such limit, and a plug
     *         that is not part of a formed machine cannot be matched, so nothing is hidden from it either.
     */
    private static boolean allowsThisMachine(Channel channel, @Nullable String machineName) {
        if (!channel.isFirm() || machineName == null) {
            return true;
        }
        FirmChannelDefinition definition = FirmChannelConfig.find(channel.getFirmChannelId());
        return definition == null || definition.allowsMachine(machineName);
    }

    /**
     * @return the registry name of the machine this plug belongs to, {@code null} while it is not part of a formed one
     */
    @Nullable
    private String getFormedMachineName() {
        TileMultiblockMachineController machine = controller;
        return machine == null ? null : machine.getFormedMachineName();
    }

    /** @return the channel snapshot shown in the GUI, server side it is refreshed every few ticks */
    public List<ChannelInfo> getSyncedChannels() {
        return syncedChannels;
    }

    /** @return whether the machine was formed when the last snapshot was taken */
    public boolean isSyncedFormed() {
        return syncedFormed;
    }

    /** Registers a player that has this plug's GUI open, which enables value updates for them. */
    public void addViewer(EntityPlayer player) {
        if (getWorld() != null && !getWorld().isRemote) {
            viewers.add(player.getUniqueID());
            nextChannelListRefresh = 0L;
            networkDirty = true;
        }
    }

    /** Removes a player that closed this plug's GUI. */
    public void removeViewer(EntityPlayer player) {
        if (getWorld() != null && !getWorld().isRemote) {
            viewers.remove(player.getUniqueID());
        }
    }

    /** Drops the controller reference once the machine is gone or its structure broke apart. */
    private void validateController() {
        TileMultiblockMachineController machine = controller;
        if (machine == null) {
            activeEnergyChannel = null;
            return;
        }
        if (machine.isInvalid() || machine.getWorld() != getWorld() || !machine.isStructureFormed()) {
            // the modifiers live on the controller, so they have to go before the reference does
            clearChannelModifiers(machine);
            controller = null;
            activeEnergyChannel = null;
        }
    }

    /**
     * Resolves the channel the machine draws from and stores into.
     *
     * <p>The pack's energy channels come first - the per player instance of a private one, then the shared ones in
     * configuration order - and while the pack declares one, the channels players registered themselves do not exist
     * any more (see {@code ChannelRegistry#prunePlayerEnergyChannels}). Without firm energy channels the order stays
     * what it was: the machine owner's own channel, then the public channels of other players. The pack's channels are cheap map lookups and are checked every tick; the other
     * players' ones are only searched every {@link #CHANNEL_RESCAN_INTERVAL} ticks, so a server full of machines does not
     * rescan the whole channel map every tick. While the plug is not bound to a formed machine the placer's channels are
     * used, see {@link #getChannelOwner()}.</p>
     */
    private void refreshEnergyChannel() {
        UUID owner = getChannelOwner();
        setActiveEnergyChannel(owner == null ? null : resolveEnergyChannel(owner));
    }

    /**
     * @return the channel the machine should use right now, {@code null} when it has none
     */
    @Nullable
    private Channel resolveEnergyChannel(UUID owner) {
        Channel firm = ChannelRegistry.findFirmEnergyChannelFor(getWorld(), owner, this::isChannelEnabled);
        if (firm != null) {
            return firm;
        }
        Channel own = ChannelRegistry.getEnergyChannel(getWorld(), owner);
        if (own != null && isChannelEnabled(own)) {
            return own;
        }

        Channel cached = activeEnergyChannel;
        long now = getWorld().getTotalWorldTime();
        if (cached != null && cached.isPublic() && !cached.isFirm() && now < nextChannelScan
                && isChannelEnabled(cached)) {
            return cached;
        }
        if (now < nextChannelScan) {
            return null;
        }
        nextChannelScan = now + CHANNEL_RESCAN_INTERVAL;
        return ChannelRegistry.findPublicEnergyChannelFor(getWorld(), owner, this::isChannelEnabled);
    }

    /**
     * Remembers the channel the machine works with, reporting every change: a machine drawing from a shared pool or
     * from somebody else's channel is otherwise invisible, and "why is it running on that channel" is exactly what a
     * pack author has to be able to answer without reading code.
     */
    private void setActiveEnergyChannel(@Nullable Channel channel) {
        Channel previous = activeEnergyChannel;
        String address = channel == null ? null : channel.getAddress();
        String previousAddress = previous == null ? null : previous.getAddress();
        activeEnergyChannel = channel;
        if (Objects.equals(address, previousAddress)) {
            return;
        }
        // the GUI marks the channel the machine currently works with
        networkDirty = true;
        if (channel == null) {
            ModularGrid.LOGGER.info("Access plug at {} stopped using an energy channel", getPos());
        } else {
            ModularGrid.LOGGER.info("Access plug at {} now uses the energy channel {}", getPos(),
                    describeChannel(channel));
        }
    }

    /** @return a channel as it is written in a log line: its id or name and who it belongs to */
    private static String describeChannel(Channel channel) {
        if (channel.isFirm()) {
            FirmChannelDefinition definition = FirmChannelConfig.find(channel.getFirmChannelId());
            return "'" + (definition == null ? channel.getName() : definition.getChannelId()) + "' (firm channel"
                    + (channel.isPublic() ? "" : ", per player") + ')';
        }
        return "'" + channel.getName() + "' (player " + channel.getOwnerName() + ')';
    }

    /** @return whether this channel is the one the machine currently draws energy from */
    public boolean isActiveEnergyChannel(Channel channel) {
        Channel active = activeEnergyChannel;
        return channel != null && active != null && channel.getAddress().equals(active.getAddress());
    }

    /** @return the energy channel currently used by this machine, {@code null} if it has none */
    @Nullable
    public Channel getActiveEnergyChannel() {
        return activeEnergyChannel;
    }

    /** Forces the next tick to resolve the channel again, used when the enable state changes. */
    public void invalidateChannelCache() {
        nextChannelScan = 0L;
    }

    // -------------------------------------------------------------- enable state

    /**
     * @return whether the channel belongs to the player whose channels this machine uses: either a channel they
     *         registered themselves, or one of the pack's per player channels declared for them
     */
    private boolean isOwnChannel(Channel channel) {
        UUID owner = getChannelOwner();
        if (owner == null || channel == null) {
            return false;
        }
        if (channel.isFirm()) {
            return owner.equals(channel.getFirmPlayer());
        }
        return owner.equals(channel.getOwner());
    }

    /**
     * @return whether this machine may use the channel: its own channels are usable unless switched off, every other
     *         channel has to be switched on first
     */
    public boolean isChannelEnabled(Channel channel) {
        if (channel == null) {
            return false;
        }
        String address = channel.getAddress();
        if (disabledChannels.contains(address)) {
            return false;
        }
        return isOwnChannel(channel) || enabledChannels.contains(address);
    }

    /** @return an unmodifiable view of the switched off channel addresses, in insertion order */
    public Set<String> getDisabledChannels() {
        return Collections.unmodifiableSet(disabledChannels);
    }

    /** @return an unmodifiable view of the switched on channel addresses that are not the owner's own, in order */
    public Set<String> getEnabledChannels() {
        return Collections.unmodifiableSet(enabledChannels);
    }

    /**
     * Switches a channel on or off for this machine.
     *
     * <p>Which set records the state depends on who owns the channel: an own channel is on by default and is listed
     * while off, a foreign one is off by default and is listed while on. The other set never keeps the same address,
     * so a channel cannot end up described by both.</p>
     *
     * @return {@code true} if the state changed, in which case the change was persisted
     */
    public boolean setChannelEnabled(Channel channel, boolean enabled) {
        if (channel == null) {
            return false;
        }
        String address = channel.getAddress();
        boolean changed;
        if (isOwnChannel(channel)) {
            changed = enabled ? disabledChannels.remove(address) : disabledChannels.add(address);
            changed |= enabledChannels.remove(address);
        } else {
            changed = enabled ? enabledChannels.add(address) : enabledChannels.remove(address);
            changed |= disabledChannels.remove(address);
        }
        if (changed) {
            invalidateChannelCache();
            markDirty();
            markChunkDirty();
            nextChannelListRefresh = 0L;
            networkDirty = true;
        }
        return changed;
    }

    /**
     * Forgets a channel address, used when a channel is deleted so no stale entry is left behind.
     *
     * @return {@code true} if an entry was removed
     */
    public boolean forgetChannel(String address) {
        boolean changed = disabledChannels.remove(address);
        changed |= enabledChannels.remove(address);
        if (!changed) {
            return false;
        }
        invalidateChannelCache();
        markDirty();
        markChunkDirty();
        nextChannelListRefresh = 0L;
        networkDirty = true;
        return true;
    }

    // --------------------------------------------------------------- energy io

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }

    /**
     * Energy pushed in by a cable or another machine ends up in the channel. Nothing is stored in the
     * plug itself, so the whole amount either lands in the pool or the request is refused.
     */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (activeEnergyChannel == null || maxReceive <= 0) {
            return 0;
        }
        if (simulate) {
            return EnergyIo.toInt(EnergyIo.accepted(getStoredEnergy(), maxReceive));
        }
        return EnergyIo.toInt(insertEnergy(maxReceive));
    }

    /**
     * Energy pulled out of the plug by a cable or another machine is taken from the channel. A pool
     * smaller than the request pays out what it has, like Modular Machinery's own hatches do.
     */
    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (activeEnergyChannel == null || maxExtract <= 0) {
            return 0;
        }
        if (simulate) {
            return EnergyIo.toInt(Math.min(getStoredEnergy(), maxExtract));
        }
        return EnergyIo.toInt(extractEnergy(maxExtract));
    }

    @Override
    public int getEnergyStored() {
        return EnergyIo.toInt(getStoredEnergy());
    }

    @Override
    public int getMaxEnergyStored() {
        return activeEnergyChannel == null ? 0 : Integer.MAX_VALUE;
    }

    /** @return whether the machine has a channel to extract from right now */
    @Override
    public boolean canExtract() {
        return activeEnergyChannel != null;
    }

    /** @return whether the machine has a channel to push energy into right now */
    @Override
    public boolean canReceive() {
        return activeEnergyChannel != null;
    }

    /**
     * @return the Flux Networks side of this plug, the settings it persists and the connector behaviour it needs
     */
    @Override
    @Optional.Method(modid = "fluxnetworks")
    public AccessPlugFluxState getFluxState() {
        AccessPlugFluxState state = fluxState;
        if (state == null) {
            state = new AccessPlugFluxState(this);
            fluxState = state;
        }
        return state;
    }

    /** Marks settings owned by an integration as changed so they are saved and pushed to watching players. */
    public void markSettingsDirty() {
        markDirty();
        markChunkDirty();
        networkDirty = true;
    }

    // ------------------------------------------------- flux networks, GUI facing

    /**
     * Steps this plug through the Flux Networks networks the player may join, which is what the plug GUI's network row
     * does.
     *
     * @return {@code true} when Flux Networks is installed and the click was handled
     */
    public boolean cycleFluxNetwork(EntityPlayer player, int step) {
        return FluxNetworksIntegration.cycleNetwork(this, player, step);
    }

    /** @return the network id shown in the GUI, {@code -1} while the plug is not in a network */
    public int getSyncedFluxNetworkID() {
        return syncedFluxNetworkID;
    }

    /** @return the network name shown in the GUI, empty while the plug is not in a network */
    public String getSyncedFluxNetworkName() {
        return syncedFluxNetworkName;
    }

    // ------------------------------------------------------ circulation node API

    /** CFN node storage is kept untyped so loading this tile never resolves CFN classes when the mod is absent. */
    @Nullable
    public Object getCirculationNode() {
        return circulationNode;
    }

    public void setCirculationNode(@Nullable Object node) {
        circulationNode = node;
        if (node == null) {
            circulationNodeRegistered = false;
        }
    }

    public boolean isCirculationNodeRegistered() {
        return circulationNodeRegistered;
    }

    public void setCirculationNodeRegistered(boolean registered) {
        circulationNodeRegistered = registered;
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public com.circulation.circulation_networks.network.nodes.PortNode getNode() {
        Object node = circulationNode;
        return node instanceof com.circulation.circulation_networks.network.nodes.PortNode
                ? (com.circulation.circulation_networks.network.nodes.PortNode) node : null;
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public com.circulation.circulation_networks.api.node.NodeType<com.circulation.circulation_networks.network.nodes.PortNode>
    getNodeType() {
        return com.modulargrid.common.integration.circulation.AccessPlugCfnNode.getNodeType();
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public BlockPos getNodePos() {
        return getPos();
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public World getNodeWorld() {
        return getWorld();
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public void nodeValidate() {
        CirculationIntegration.validateNode(this);
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public void nodeInvalidate() {
        CirculationIntegration.invalidateNode(this);
    }

    @Override
    @Optional.Method(modid = "circulation_networks")
    public void syncNodeAfterNetworkInit() {
        CirculationIntegration.syncNodeAfterNetworkInit(this);
    }

    // ---------------------------------------------------------- integration io

    /**
     * @return {@code true} while this plug has a usable channel, which is the case when it is part of a formed
     *         machine or an unbound port of the player that placed it and the channel was not switched off in its GUI
     */
    public boolean isEnergyPortActive() {
        return activeEnergyChannel != null;
    }

    /**
     * Puts energy into this plug's channel. Used by the wireless network integrations, which hand over a
     * {@code long} and would lose the upper range of it through the {@code int} based Forge Energy API.
     *
     * @return the part of {@code amount} that actually ended up in the channel
     */
    public long insertEnergy(long amount) {
        Channel channel = activeEnergyChannel;
        if (channel == null || amount <= 0L) {
            return 0L;
        }
        long before = channel.getEnergyValue();
        channel.giveEnergy(amount);
        long gained = channel.getEnergyValue() - before;
        if (gained > 0L) {
            energyValueDirty = true;
        }
        return gained;
    }

    /**
     * Takes energy out of this plug's channel.
     *
     * @return the part of {@code amount} that was actually available
     */
    public long extractEnergy(long amount) {
        Channel channel = activeEnergyChannel;
        if (channel == null || amount <= 0L) {
            return 0L;
        }
        long taken = channel.takeEnergy(amount);
        if (taken > 0L) {
            energyValueDirty = true;
        }
        return taken;
    }

    /** @return the energy currently held by this plug's channel, {@code 0} while it has none */
    public long getStoredEnergy() {
        Channel channel = activeEnergyChannel;
        return channel == null ? 0L : channel.getEnergyValue();
    }

    /**
     * @return how much energy this plug's channel could still take, {@code 0} while it has none. A channel is not
     *         capped, so only the saturation point of the stored {@code long} limits this.
     */
    public long getChannelRoom() {
        return EnergyIo.accepted(getStoredEnergy(), Long.MAX_VALUE);
    }

    // --------------------------------------------------------- energy priority

    /**
     * Enforces "all energy IO happens on the channel".
     *
     * <p>Modular Machinery picks its energy handler by iterating the components of the recipe context in an
     * order we cannot control, so leaving the physical hatches alone would make an ordinary hatch win over
     * the channel. Instead the plug keeps those hatches in a state that makes the channel the only real
     * source and sink:</p>
     *
     * <ul>
     *     <li>input hatches are topped up from the channel until they are full, so an external cable has no
     *         room left to push energy in and whatever the machine consumes effectively comes from the channel,</li>
     *     <li>output hatches are emptied into the channel every tick, so an external cable has nothing to pull
     *         and the surplus ends up in the channel as well.</li>
     * </ul>
     *
     * <p>Refilling to the brim (instead of trickling in the recipe's rate) also hides the race against the
     * asynchronous recipe thread: the hatch is always full enough to satisfy a whole tick of consumption.</p>
     */
    private void manageEnergyHatches() {
        TileMultiblockMachineController machine = controller;
        Channel channel = activeEnergyChannel;
        if (machine == null || channel == null) {
            return;
        }

        // Modular Machinery merges the group-less components into every group, so the same hatch can be
        // visited more than once
        Set<TileEntity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean transferred = false;
        for (Map<TileEntity, ProcessingComponent<?>> group : machine.getFoundComponents().values()) {
            for (Map.Entry<TileEntity, ProcessingComponent<?>> entry : group.entrySet()) {
                if (!visited.add(entry.getKey())) {
                    continue;
                }
                MachineComponent<?> component = entry.getValue().getComponent();
                if (!(component instanceof MachineComponent.EnergyHatch)) {
                    continue;
                }
                // our own handler is the channel itself, moving energy between them is a no-op
                if (!(component.getContainerProvider() instanceof IEnergyHandler hatch) || hatch == energyHandler) {
                    continue;
                }
                transferred |= component.getIOType() == IOType.INPUT
                        ? topUpInputHatch(channel, hatch)
                        : drainOutputHatch(channel, hatch);
            }
        }
        if (transferred) {
            energyValueDirty = true;
        }
    }

    /**
     * Fills an input hatch from the channel. The channel is debited first and refunded if the hatch refuses
     * or takes less than offered, so the pool can never be drained for energy that was not delivered.
     *
     * @return {@code true} if energy was moved
     */
    private static boolean topUpInputHatch(Channel channel, IEnergyHandler hatch) {
        long offer = Math.min(hatch.getRemainingCapacity(), channel.getEnergyValue());
        if (offer <= 0L) {
            return false;
        }
        long taken = channel.takeEnergy(offer);
        if (taken <= 0L) {
            return false;
        }
        long before = hatch.getCurrentEnergy();
        if (!fill(hatch, before, taken)) {
            channel.giveEnergy(taken);
            return false;
        }
        long moved = hatch.getCurrentEnergy() - before;
        if (moved < taken) {
            channel.giveEnergy(taken - moved);
        }
        return moved > 0L;
    }

    /**
     * Empties an output hatch into the channel.
     *
     * @return {@code true} if energy was moved
     */
    private static boolean drainOutputHatch(Channel channel, IEnergyHandler hatch) {
        long stored = hatch.getCurrentEnergy();
        if (stored <= 0L) {
            return false;
        }
        if (hatch instanceof IEnergyHandlerAsync async) {
            if (!async.extractEnergy(stored)) {
                return false;
            }
        } else {
            hatch.setCurrentEnergy(0L);
        }
        long moved = stored - hatch.getCurrentEnergy();
        if (moved <= 0L) {
            return false;
        }
        channel.giveEnergy(moved);
        return true;
    }

    /**
     * Adds energy to a hatch. The asynchronous API is all or nothing (it returns {@code false} when it cannot
     * take the whole amount), the plain one clamps to the hatch capacity on its own.
     */
    private static boolean fill(IEnergyHandler hatch, long current, long amount) {
        if (hatch instanceof IEnergyHandlerAsync async) {
            return async.receiveEnergy(amount);
        }
        hatch.setCurrentEnergy(current + amount);
        return true;
    }

    // ------------------------------------------------------------- pushing out

    /**
     * Pushes channel energy into the neighbours' Forge Energy handlers.
     *
     * <p>Modular Machinery's own input hatches do not pull, they rely on something pushing into them, so
     * the plug has to push just like an output hatch does - that is what makes "two machines, no cables"
     * work. Identical to those hatches it offers one {@code int} clamped amount per face and tick, and it
     * skips energy hatches and other access plugs so hatches never feed each other in a loop.</p>
     */
    private void pushEnergyToNeighbors() {
        World world = getWorld();
        Channel channel = activeEnergyChannel;
        if (world == null || channel == null || channel.getEnergyValue() <= 0L) {
            return;
        }

        boolean moved = false;
        for (EnumFacing facing : EnumFacing.VALUES) {
            long available = channel.getEnergyValue();
            if (available <= 0L) {
                break;
            }
            TileEntity tile = world.getTileEntity(getPos().offset(facing));
            if (tile == null || tile instanceof TileAccessPlug || tile instanceof TileEnergyHatch) {
                continue;
            }
            EnumFacing side = facing.getOpposite();
            if (!tile.hasCapability(CapabilityEnergy.ENERGY, side)) {
                continue;
            }
            IEnergyStorage storage = tile.getCapability(CapabilityEnergy.ENERGY, side);
            if (storage == null || !storage.canReceive()) {
                continue;
            }
            // debit first and refund what the neighbour refused, a pool can never pay out twice
            long taken = channel.takeEnergy(EnergyIo.toInt(available));
            if (taken <= 0L) {
                continue;
            }
            int accepted = storage.receiveEnergy(EnergyIo.toInt(taken), false);
            if (accepted < taken) {
                channel.giveEnergy(taken - accepted);
            }
            moved |= accepted > 0;
        }
        if (moved) {
            energyValueDirty = true;
        }
    }

    // ----------------------------------------------------------- industrialcraft

    /**
     * Joins IC2's energy net, so EU cables can reach the channel. Announced once, on the first server
     * tick, the same way Modular Machinery's own hatches do it.
     */
    @Optional.Method(modid = "ic2")
    private void registerWithEnergyNet() {
        if (energyNetRegistered || getWorld() == null || getWorld().isRemote) {
            return;
        }
        energyNetRegistered = true;
        MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
    }

    @Optional.Method(modid = "ic2")
    private void unregisterFromEnergyNet() {
        if (!energyNetRegistered) {
            return;
        }
        energyNetRegistered = false;
        MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (CirculationIntegration.isAvailable()) {
            CirculationIntegration.validateNode(this);
        }
    }

    /**
     * Names of the modifiers this plug has applied to its controller, so only differences are applied. Every add and
     * remove makes Modular Machinery rebuild the modifier context of the controller, which is worth avoiding.
     */
    private final Map<String, RecipeModifier> appliedChannelModifiers = new LinkedHashMap<>();

    /**
     * Keeps the modifiers of the controller in step with the channels this machine may use.
     *
     * <p>A channel gives its modifiers while the plug is part of a formed machine, the channel is not switched off for
     * that machine, and the channel is declared for it. A shared channel is therefore switched on explicitly, the same
     * rule the energy side follows, and a restricted channel only reaches its machines.</p>
     *
     * <p>The set is rebuilt whenever anything could have changed and compared, so the controller is only touched when
     * it really differs. The modifiers are registered under a name of ours, which is what makes removing exactly them
     * again possible.</p>
     */
    private void applyChannelModifiers() {
        TileMultiblockMachineController machine = controller;
        if (machine == null) {
            appliedChannelModifiers.clear();
            return;
        }
        if (ChannelModifierRegistry.isEmpty() && appliedChannelModifiers.isEmpty()) {
            // nothing declares a modifier and nothing is applied, which is the normal case
            return;
        }
        Map<String, RecipeModifier> desired = desiredChannelModifiers(machine);
        boolean changed = false;
        Iterator<String> names = appliedChannelModifiers.keySet().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (!desired.containsKey(name)) {
                machine.removePermanentModifier(name);
                names.remove();
                changed = true;
            }
        }
        for (Map.Entry<String, RecipeModifier> entry : desired.entrySet()) {
            if (appliedChannelModifiers.get(entry.getKey()) != entry.getValue()) {
                machine.addPermanentModifier(entry.getKey(), entry.getValue());
                appliedChannelModifiers.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            ModularGrid.LOGGER.info("Access plug at {} applies {} channel modifier(s) from {}", getPos(),
                    appliedChannelModifiers.size(), appliedChannelModifiers.keySet());
        }
    }

    /**
     * @return the modifiers the machine should currently have, by the name they are registered under
     */
    private Map<String, RecipeModifier> desiredChannelModifiers(TileMultiblockMachineController machine) {
        Map<String, RecipeModifier> desired = new LinkedHashMap<>();
        if (ChannelModifierRegistry.isEmpty()) {
            return desired;
        }
        String machineName = machine.getFormedMachineName();
        UUID owner = machine.getOwner();
        for (String channelId : ChannelModifierRegistry.getDeclaringChannels()) {
            FirmChannelDefinition definition = FirmChannelConfig.find(channelId);
            if (definition == null) {
                continue;
            }
            Channel channel = ChannelRegistry.getOrCreateFirmValue(getWorld(), definition,
                    definition.isPublic() ? null : owner);
            if (!ChannelModifierRegistry.appliesTo(definition, machineName, isChannelEnabled(channel))) {
                continue;
            }
            List<RecipeModifier> modifiers = ChannelModifierRegistry.getFor(channelId, machineName);
            for (int i = 0; i < modifiers.size(); i++) {
                desired.put(modifierName(channelId, i), modifiers.get(i));
            }
        }
        return desired;
    }

    /** @return the name a channel's modifier is registered on the controller under, see {@code addPermanentModifier} */
    private static String modifierName(String channelId, int index) {
        return MODIFIER_NAME_PREFIX + channelId + '#' + index;
    }

    /**
     * Drops the modifiers this plug applied to a controller. They live on the controller rather than on this plug, so
     * they have to be taken back when the machine is no longer there.
     */
    private void clearChannelModifiers(@Nullable TileMultiblockMachineController machine) {
        if (machine != null) {
            for (String name : appliedChannelModifiers.keySet()) {
                machine.removePermanentModifier(name);
            }
        }
        appliedChannelModifiers.clear();
    }

    @Override
    public void invalidate() {
        if (CirculationIntegration.isAvailable()) {
            CirculationIntegration.invalidateNode(this);
        }
        clearChannelModifiers(controller);
        super.invalidate();
        if (IC2_LOADED) {
            unregisterFromEnergyNet();
        }
        if (FluxNetworksIntegration.isAvailable()) {
            FluxNetworksIntegration.detach(this);
        }
    }

    @Override
    public void onChunkUnload() {
        if (CirculationIntegration.isAvailable()) {
            CirculationIntegration.invalidateNode(this);
        }
        clearChannelModifiers(controller);
        super.onChunkUnload();
        if (IC2_LOADED) {
            unregisterFromEnergyNet();
        }
        if (FluxNetworksIntegration.isAvailable()) {
            FluxNetworksIntegration.detach(this);
        }
    }

    /** @return how much EU this plug is willing to take in per tick, 0 while it has no channel */
    @Override
    @Optional.Method(modid = "ic2")
    public double getDemandedEnergy() {
        return activeEnergyChannel == null ? 0.0D : IC2_TRANSFER_LIMIT;
    }

    @Override
    @Optional.Method(modid = "ic2")
    public int getSinkTier() {
        return IC2_TIER;
    }

    /**
     * Energy injected by an IC2 cable ends up in the channel.
     *
     * @return the part of {@code amount} that could not be accepted, as IC2 expects
     */
    @Override
    @Optional.Method(modid = "ic2")
    public double injectEnergy(EnumFacing directionFrom, double amount, double voltage) {
        if (activeEnergyChannel == null || amount <= 0.0D) {
            return amount;
        }
        long offeredEu = (long) Math.floor(Math.min(amount, IC2_TRANSFER_LIMIT));
        long acceptedEu = insertEnergy(offeredEu * RF_PER_EU) / RF_PER_EU;
        return amount - acceptedEu;
    }

    @Override
    @Optional.Method(modid = "ic2")
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
        return activeEnergyChannel != null;
    }

    /** @return the EU this plug can hand out this tick, limited by the channel and by the tier's rate */
    @Override
    @Optional.Method(modid = "ic2")
    public double getOfferedEnergy() {
        if (activeEnergyChannel == null) {
            return 0.0D;
        }
        return Math.min(getStoredEnergy() / (double) RF_PER_EU, IC2_TRANSFER_LIMIT);
    }

    /** EU drawn by an IC2 cable is taken from the channel. */
    @Override
    @Optional.Method(modid = "ic2")
    public void drawEnergy(double amount) {
        if (amount <= 0.0D) {
            return;
        }
        extractEnergy((long) Math.floor(amount) * RF_PER_EU);
    }

    @Override
    @Optional.Method(modid = "ic2")
    public int getSourceTier() {
        return IC2_TIER;
    }

    @Override
    @Optional.Method(modid = "ic2")
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, EnumFacing side) {
        return activeEnergyChannel != null;
    }

    // ---------------------------------------------------------------------- nbt

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);
        disabledChannels.clear();
        enabledChannels.clear();
        NBTTagList list = compound.getTagList(NBT_DISABLED_CHANNELS, Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            disabledChannels.add(list.getStringTagAt(i));
        }
        NBTTagList enabledList = compound.getTagList(NBT_ENABLED_CHANNELS, Constants.NBT.TAG_STRING);
        for (int i = 0; i < enabledList.tagCount(); i++) {
            enabledChannels.add(enabledList.getStringTagAt(i));
        }
        placer = compound.hasUniqueId(NBT_PLACER) ? compound.getUniqueId(NBT_PLACER) : null;
        invalidateChannelCache();
        if (CirculationIntegration.isAvailable()) {
            CirculationIntegration.readNode(this, compound);
        }
        if (FluxNetworksIntegration.isAvailable()) {
            FluxNetworksIntegration.readSettings(this, compound);
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);
        NBTTagList list = new NBTTagList();
        for (String address : disabledChannels) {
            list.appendTag(new NBTTagString(address));
        }
        compound.setTag(NBT_DISABLED_CHANNELS, list);
        NBTTagList enabledList = new NBTTagList();
        for (String address : enabledChannels) {
            enabledList.appendTag(new NBTTagString(address));
        }
        compound.setTag(NBT_ENABLED_CHANNELS, enabledList);
        if (FluxNetworksIntegration.isAvailable()) {
            FluxNetworksIntegration.writeSettings(this, compound);
        }
        if (placer != null) {
            compound.setUniqueId(NBT_PLACER, placer);
        }
        if (CirculationIntegration.isAvailable()) {
            CirculationIntegration.writeNode(this, compound);
        }
    }

    /**
     * The GUI data: which channels exist for this machine and whether they are switched on. Sent as part
     * of the standard tile entity update packet, see {@link #addViewer(EntityPlayer)}.
     */
    @Override
    public void writeNetNBT(NBTTagCompound compound) {
        super.writeNetNBT(compound);
        compound.setBoolean(NBT_FORMED, syncedFormed);
        NBTTagList list = new NBTTagList();
        for (ChannelInfo info : syncedChannels) {
            list.appendTag(info.writeToNBT());
        }
        compound.setTag(NBT_CHANNELS, list);
        if (FluxNetworksIntegration.isAvailable()) {
            FluxNetworksIntegration.writeClientState(this, compound);
        }
    }

    @Override
    public void readNetNBT(NBTTagCompound compound) {
        super.readNetNBT(compound);
        syncedFormed = compound.getBoolean(NBT_FORMED);
        NBTTagList list = compound.getTagList(NBT_CHANNELS, Constants.NBT.TAG_COMPOUND);
        List<ChannelInfo> infos = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            infos.add(ChannelInfo.readFromNBT(list.getCompoundTagAt(i)));
        }
        syncedChannels = infos;
        syncedFluxNetworkID = compound.hasKey(FluxNetworksIntegration.NBT_NETWORK_ID)
                ? compound.getInteger(FluxNetworksIntegration.NBT_NETWORK_ID) : -1;
        syncedFluxNetworkName = compound.getString(FluxNetworksIntegration.NBT_NETWORK_NAME);
    }

    // ------------------------------------------------------- channel energy pool

    /**
     * The storage of the plug's energy components: it maps Modular Machinery's energy API straight
     * onto the player's energy channel, which is an unbounded {@code long} pool.
     *
     * <p>While no channel is available (no controller, no channel, channel switched off) the handler
     * reports zero stored energy and zero capacity, so the machine behaves as if the plug was not
     * there at all - it neither receives power from the grid nor pushes power into it.</p>
     */
    private final class ChannelEnergyHandler implements IEnergyHandlerAsync {

        @Override
        public long getCurrentEnergy() {
            Channel channel = activeEnergyChannel;
            return channel == null ? 0L : channel.getEnergyValue();
        }

        @Override
        public void setCurrentEnergy(long energy) {
            Channel channel = activeEnergyChannel;
            if (channel != null) {
                channel.setEnergyValue(energy);
                energyValueDirty = true;
            }
        }

        @Override
        public long getMaxEnergy() {
            return activeEnergyChannel == null ? 0L : Long.MAX_VALUE;
        }

        @Override
        public boolean extractEnergy(long energy) {
            Channel channel = activeEnergyChannel;
            if (channel == null || energy <= 0L || channel.getEnergyValue() < energy) {
                return false;
            }
            if (channel.takeEnergy(energy) != energy) {
                return false;
            }
            energyValueDirty = true;
            return true;
        }

        @Override
        public boolean receiveEnergy(long energy) {
            Channel channel = activeEnergyChannel;
            if (channel == null || energy <= 0L) {
                return false;
            }
            long before = channel.getEnergyValue();
            channel.giveEnergy(energy);
            energyValueDirty = true;
            // an unbounded pool can only refuse when it is already saturated
            return channel.getEnergyValue() != before;
        }

        @Override
        public String toString() {
            Channel channel = activeEnergyChannel;
            return "ChannelEnergyHandler{" + (channel == null ? "no channel" : channel.getAddress())
                    + ", energy=" + getCurrentEnergy() + '}';
        }
    }

}
