package com.modulargrid.common.integration.circulation;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.modulargrid.ModularGrid;
import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * Makes an access plug an energy device of Circulation Flow Networks, so the nodes of a network can charge the
 * channel and draw from it.
 */
public class AccessPlugEnergyHandler implements IEnergyHandler {

    /** Same lifecycle the mod's generic Forge Energy handler uses: refreshed at the start of a tick, fixed role. */
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
            HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
            HandlerBindingPolicy.RoleScope.FIXED,
            HandlerBindingPolicy.MappingScope.NONE,
            HandlerBindingPolicy.PairMatching.NONE);

    private TileAccessPlug plug;
    private HandlerInvalidationSink sink;
    private long activeEpoch = Long.MIN_VALUE;
    private long lastTransferLogTick = Long.MIN_VALUE;

    /** Refreshed every tick: whether the plug currently has a usable channel, and which one it is. */
    private boolean usable;
    private Channel observedChannel;

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    // --------------------------------------------------------------------- binding

    @Override
    public void bindBlockEntity(TileEntity tile, HandlerInvalidationSink sink) {
        plug = tile instanceof TileAccessPlug accessPlug ? accessPlug : null;
        this.sink = sink;
        usable = false;
        observedChannel = null;
        if (plug != null) {
            ModularGrid.LOGGER.info("Access plug at {} is used by Circulation Flow Networks as an energy device",
                    plug.getPos());
        }
    }

    @Override
    public void unbindBlockEntity() {
        plug = null;
        sink = null;
        usable = false;
        observedChannel = null;
    }

    @Override
    public void bindItem(ItemStack stack, HubNode.HubMetadata metadata) {
        // the plug is never bound as an item, see AccessPlugEnergyHandlerManager#isAvailable(ItemStack)
    }

    @Override
    public void unbindItem() {
    }

    // ---------------------------------------------------------------- tick lifecycle

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("Access plug handler epoch must increase: previous " + activeEpoch
                    + ", got " + epoch);
        }
        activeEpoch = epoch;
        if (plug == null || plug.isInvalid()) {
            // the plug is gone, the node has to bind a new handler before it is asked again
            usable = false;
            observedChannel = null;
            return HandlerTickResult.SUSPEND_UNTIL_REBIND;
        }
        // the channel itself is watched, not just whether there is one: forming the machine, switching the channel
        // off in the plug's GUI or letting the plug fall back to another channel all change what this device offers
        Channel channel = plug.getActiveEnergyChannel();
        if (channel == observedChannel && (channel != null) == usable) {
            return HandlerTickResult.UNCHANGED;
        }
        usable = channel != null;
        observedChannel = channel;
        ModularGrid.LOGGER.info("Access plug at {} is now {} to Circulation Flow Networks", plug.getPos(),
                usable ? "available" : "unavailable");
        if (sink != null) {
            // the backing store changed, the mod can re-read what this device offers and refresh its clients
            sink.backendChanged();
        }
        return HandlerTickResult.STATE_CHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Access plug handler uses the begin-only tick lifecycle");
    }

    // ----------------------------------------------------------------- energy access

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount amount, HubNode.HubMetadata metadata) {
        if (amount == null) {
            logTransfer("receive attempted while " + (usable ? "available" : "unavailable"));
            return EnergyAmounts.ZERO;
        }
        long requested = amount.asLongClamped();
        if (!usable) {
            logTransfer("receive rejected while unavailable (requested " + requested + ")");
            return EnergyAmounts.ZERO;
        }
        long accepted = plug.insertEnergy(requested);
        logTransfer("received " + accepted + " of " + requested);
        return accepted > 0L ? EnergyAmount.obtain(accepted) : EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount amount, HubNode.HubMetadata metadata) {
        if (amount == null) {
            logTransfer("extract attempted while " + (usable ? "available" : "unavailable"));
            return EnergyAmounts.ZERO;
        }
        long requested = amount.asLongClamped();
        if (!usable) {
            logTransfer("extract rejected while unavailable (requested " + requested + ")");
            return EnergyAmounts.ZERO;
        }
        long taken = plug.extractEnergy(requested);
        logTransfer("extracted " + taken + " of " + requested);
        return taken > 0L ? EnergyAmount.obtain(taken) : EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(HubNode.HubMetadata metadata) {
        if (!usable || plug == null) {
            logTransfer("receive capacity queried: unavailable");
            return EnergyAmounts.ZERO;
        }
        long room = plug.getChannelRoom();
        logTransfer("receive capacity queried: " + room + " RF");
        return room > 0L ? EnergyAmount.obtain(room) : EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(HubNode.HubMetadata metadata) {
        if (!usable || plug == null) {
            logTransfer("extract capacity queried: unavailable");
            return EnergyAmounts.ZERO;
        }
        long stored = plug.getStoredEnergy();
        logTransfer("extract capacity queried: " + stored + " RF");
        return stored > 0L ? EnergyAmount.obtain(stored) : EnergyAmounts.ZERO;
    }

    @Override
    public boolean canReceive(IEnergyHandler other, HubNode.HubMetadata metadata) {
        EnergyAmount value = canReceiveValue(metadata);
        boolean positive = value.isPositive();
        value.recycle();
        return positive;
    }

    @Override
    public boolean canExtract(IEnergyHandler other, HubNode.HubMetadata metadata) {
        EnergyAmount value = canExtractValue(metadata);
        boolean positive = value.isPositive();
        value.recycle();
        return positive;
    }

    /** Emits at most one CFN energy-path diagnostic every five seconds. */
    private void logTransfer(String details) {
        if (plug == null || plug.getWorld() == null) {
            return;
        }
        long now = plug.getWorld().getTotalWorldTime();
        if (lastTransferLogTick == Long.MIN_VALUE || now - lastTransferLogTick >= 100L) {
            lastTransferLogTick = now;
            ModularGrid.LOGGER.info("Circulation Flow Networks energy call at access plug {}: {} RF", plug.getPos(),
                    details);
        }
    }

    /** @return {@code STORAGE}: the channel both takes energy from a network and hands energy to one */
    @Override
    public IEnergyHandler.EnergyType getType(HubNode.HubMetadata metadata) {
        return IEnergyHandler.EnergyType.STORAGE;
    }
}
