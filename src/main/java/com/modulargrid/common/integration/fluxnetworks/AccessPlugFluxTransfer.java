package com.modulargrid.common.integration.fluxnetworks;

import com.modulargrid.common.tile.TileAccessPlug;
import com.modulargrid.common.util.EnergyIo;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import sonar.fluxnetworks.api.network.ITransferHandler;
import sonar.fluxnetworks.api.utils.NBTType;

/**
 * Bridges one of Flux Networks' transfer cycles to the plug's channel.
 *
 * <p>Flux Networks calls this handler once per cycle to move energy between a connector and its network: a storage is
 * charged through {@link #addToBuffer(long)} and discharged through {@link #removeFromBuffer(long)}. Both directions
 * are the channel itself here, so the plug holds no buffer of its own that could drift away from the pool.</p>
 *
 * <p>The amounts reported to the network ({@link #getRequest()}, {@link #getBuffer()}, {@link #getChange()}) belong to
 * this plug only. Several plugs may share one channel, and reporting the pool in each of them would make the network
 * count the same energy twice, so the change is tracked as "what this connector moved" (the way Flux Networks' own
 * storage handler does it) instead of "how much the pool grew".</p>
 */
public class AccessPlugFluxTransfer implements ITransferHandler {

    /**
     * Upper bound for the energy requested in one cycle. A channel has no capacity of its own, so without a bound the
     * request would be the room up to the saturation point of a {@code long}, and Flux Networks adds the requests of
     * all connectors of a network together before it distributes energy - that sum must not overflow. The bound is
     * far above any throughput a network can actually move in a tick, so it does not cap real transfers.
     */
    private static final long MAX_REQUEST_PER_CYCLE = 1L << 58;

    /** Key Flux Networks' own storage handler persists its buffer under. */
    private static final String TAG_ENERGY = "energy";

    private final TileAccessPlug plug;
    private final AccessPlugFluxState state;

    /** Energy this connector moved during the cycle that ended last, and the running totals of the current one. */
    private long change;
    private long added;
    private long removed;

    public AccessPlugFluxTransfer(TileAccessPlug plug, AccessPlugFluxState state) {
        this.plug = plug;
        this.state = state;
    }

    /**
     * Starts a cycle: the change reported from now on is what the previous cycle moved, and the counters of this one
     * start at zero.
     */
    @Override
    public void onCycleStart() {
        change = added - removed;
        added = 0L;
        removed = 0L;
    }

    @Override
    public void onCycleEnd() {
        // the channel is the storage, Flux Networks' buffer is nothing but the value it last read
    }

    /** @return the energy the channel holds, which is what a network sees as this connector's charge */
    @Override
    public long getBuffer() {
        return plug.getStoredEnergy();
    }

    /**
     * @return how much this connector can take in during the cycle, which is the room left in the channel, limited by
     *         the limit the player configured in Flux Networks' GUI for this connector
     */
    @Override
    public long getRequest() {
        if (!plug.isEnergyPortActive()) {
            return 0L;
        }
        return EnergyIo.request(plug.getStoredEnergy(), state.getLogicLimit(), MAX_REQUEST_PER_CYCLE);
    }

    /** @return this connector's net change of the last cycle, positive while the network charged the channel */
    @Override
    public long getChange() {
        return change;
    }

    @Override
    public void addToBuffer(long amount) {
        added += plug.insertEnergy(amount);
    }

    @Override
    public long removeFromBuffer(long amount) {
        long taken = plug.extractEnergy(amount);
        removed += taken;
        return taken;
    }

    /**
     * A storage drains into its network through {@link #removeFromBuffer(long)}, so nothing has to be pulled from a
     * neighbouring block here.
     */
    @Override
    public long receiveFromSupplier(long amount, EnumFacing side, boolean simulate) {
        return 0L;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound, NBTType type) {
        if (type == NBTType.ALL_SAVE || type == NBTType.TILE_DROP) {
            // Flux Networks' own storage saves its charge here; the channel persists its own value, this keeps the
            // saved data shaped the way the mod expects it
            compound.setLong(TAG_ENERGY, plug.getStoredEnergy());
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound, NBTType type) {
        // the saved value is deliberately not read back: the channel is the single source of truth and restores its
        // own value, so restoring it here could only overwrite a newer one
    }

    @Override
    public void updateTransfers(EnumFacing... facings) {
        // nothing to look at next to the plug, its energy comes from the channel
    }

    @Override
    public void reset() {
        change = 0L;
        added = 0L;
        removed = 0L;
    }
}
