package com.modulargrid.common.integration.fluxnetworks;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.CommonProxy;
import com.modulargrid.common.block.BlockAccessPlug;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import sonar.fluxnetworks.api.network.AccessLevel;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.api.utils.Coord4D;
import sonar.fluxnetworks.api.utils.NBTType;
import sonar.fluxnetworks.common.connection.FluxNetworkServer;
import sonar.fluxnetworks.common.core.FluxUtils;
import sonar.fluxnetworks.common.data.FluxChunkManager;
import sonar.fluxnetworks.common.data.FluxNetworkData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Everything Flux Networks knows about an access plug: the per connector settings the mod offers (network, priority,
 * limit, surge mode, chunk loading), the transfer handler that bridges one of its cycles to the channel, and the
 * bookkeeping needed to join and leave a network.
 */
public class AccessPlugFluxState {

    // The keys Flux Networks' own connectors use for their settings (see its TileFluxCore).
    private static final String TAG_PRIORITY = "0";
    private static final String TAG_LIMIT = "1";
    private static final String TAG_DISABLE_LIMIT = "2";
    private static final String TAG_SURGE = "3";
    private static final String TAG_NETWORK_ID = "4";
    private static final String TAG_OWNER = "5";
    private static final String TAG_NAME = "6";
    private static final String TAG_FOLDER_ID = "9";
    private static final String TAG_FOLDER_ID_LONG = "folderID";
    private static final String TAG_CHUNK_LOADING = "l";

    // The same settings under the names the network level serialization uses, see its FluxLiteConnector.
    private static final String TAG_LITE_NETWORK_ID = "n_id";
    private static final String TAG_LITE_PRIORITY = "priority";
    private static final String TAG_LITE_LIMIT = "limit";
    private static final String TAG_LITE_DISABLE_LIMIT = "dLimit";
    private static final String TAG_LITE_SURGE = "surge";
    private static final String TAG_LITE_NAME = "name";
    private static final String TAG_LITE_FOLDER_ID = "folder_id";
    private static final String TAG_LITE_FORCED_CHUNK = "forcedChunk";

    /** Key holding this plug's Flux Networks settings inside the plug's own NBT. */
    private static final String TAG_SETTINGS = "fluxNetworks";

    /**
     * Priority a storage connector is placed at, taken from Flux Networks' own storage: storages are charged after
     * everything that consumes energy, which is exactly what a channel should behave like.
     */
    private static final int STORAGE_PRIORITY = -10000;

    /** Offset below the user set priority a storage sits at, mirroring Flux Networks' own storage. */
    private static final int STORAGE_PRIORITY_OFFSET = 1000000;

    private final TileAccessPlug plug;
    private final AccessPlugFluxTransfer transfer;

    private int networkID = -1;
    private int priority;
    private long limit = Long.MAX_VALUE;
    private boolean disableLimit = true;
    private boolean surgeMode;
    private boolean chunkLoading;
    private String customName = "";
    private int folderID;

    /**
     * A network request may be pending until Flux Networks processes its connection queue; do not enqueue a duplicate
     * request every tick.
     */
    private boolean joinRequested;

    @Nullable
    private IFluxNetwork network;
    @Nullable
    private Coord4D coords;

    public AccessPlugFluxState(TileAccessPlug plug) {
        this.plug = plug;
        this.transfer = new AccessPlugFluxTransfer(plug, this);
    }

    // ------------------------------------------------------------- network cycle

    /** Called every server tick, joins the plug to its network or leaves it again. */
    public void update() {
        if (networkID >= 0) {
            if (network == null && !joinRequested) {
                joinRequested = FluxUtils.addConnection(connector());
                if (joinRequested) {
                    ModularGrid.LOGGER.info("Access plug at {} queued for Flux Networks network {}", plug.getPos(), networkID);
                } else {
                    // the network is gone, go back to unconnected so a new one can be assigned
                    ModularGrid.LOGGER.info("Access plug at {} could not join Flux Networks network {}, it is gone",
                            plug.getPos(), networkID);
                    networkID = -1;
                    plug.markSettingsDirty();
                }
            }
            return;
        }
        joinRequested = false;
        if (network != null) {
            FluxUtils.removeConnection(connector(), false);
            network = null;
            ModularGrid.LOGGER.info("Access plug at {} left its Flux Networks network", plug.getPos());
        }
    }

    /** Called when the plug is removed from the world. */
    public void onRemoved() {
        if (network != null || joinRequested) {
            FluxUtils.removeConnection(connector(), false);
        }
        network = null;
        joinRequested = false;
        if (chunkLoading) {
            chunkLoading = false;
            applyChunkLoading(false);
        }
    }

    public void connect(IFluxNetwork joined) {
        network = joined;
        networkID = joined.getNetworkID();
        joinRequested = false;
        ModularGrid.LOGGER.info("Access plug at {} connected to Flux Networks network {}", plug.getPos(), networkID);
        plug.markSettingsDirty();
    }

    public void disconnect(IFluxNetwork left) {
        if (network == left) {
            network = null;
        }
        joinRequested = false;
        ModularGrid.LOGGER.info("Access plug at {} disconnected from Flux Networks network {}", plug.getPos(), left.getNetworkID());
        plug.markSettingsDirty();
    }

    // ------------------------------------------------------------------ settings

    public int getNetworkID() {
        return networkID;
    }

    /** @return the name of the joined network, or an empty string while the plug is not in one */
    public String getNetworkName() {
        IFluxNetwork joined = network;
        if (joined != null) {
            return joined.getNetworkName();
        }
        if (networkID < 0) {
            return "";
        }
        // assigned to a network but not joined yet (or the chunk is not loaded), so ask the network table
        FluxNetworkData data = FluxNetworkData.get();
        IFluxNetwork known = data == null ? null : data.networks.get(networkID);
        return known == null ? "" : known.getNetworkName();
    }

    /**
     * Assigns the plug to a network (or takes it out of one with {@code -1}). Joining and leaving happens on the next
     * tick, see {@link #update()}.
     */
    public void setNetworkID(int id) {
        if (id == networkID) {
            return;
        }
        networkID = id;
        joinRequested = false;
        plug.markSettingsDirty();
    }

    /**
     * Steps through the networks the player may join and assigns the next one, which is what the plug's GUI does with
     * its network row. The "no network" entry is part of that list, so a plug can also be taken out of a network
     * without touching Flux Networks' own GUI.
     *
     * @param step {@code 1} for the next entry, {@code -1} for the previous one
     */
    public void cycleNetwork(EntityPlayer player, int step) {
        List<Integer> candidates = new ArrayList<>();
        candidates.add(-1);
        FluxNetworkData data = FluxNetworkData.get();
        if (data != null) {
            for (IFluxNetwork candidate : data.networks.values()) {
                if (candidate == null || candidate.isInvalid()) {
                    continue;
                }
                AccessLevel level = candidate.getMemberPermission(player);
                if (level != null && level.canAccess()) {
                    candidates.add(candidate.getNetworkID());
                }
            }
            Collections.sort(candidates);
        }
        int index = candidates.indexOf(networkID);
        // The unconnected entry is always index zero. If the stored id is stale or permission was revoked,
        // treat the current selection as unconnected so a forward click can still select the first network.
        if (index < 0) {
            index = 0;
        }
        int next = Math.floorMod(index + step, candidates.size());
        int selected = candidates.get(next);
        ModularGrid.LOGGER.info("Flux Networks selection for plug at {}: network {} ({} permitted candidates)",
                plug.getPos(), selected, candidates.size() - 1);
        setNetworkID(selected);
    }

    @Nullable
    public IFluxNetwork getNetwork() {
        return network;
    }

    @Nullable
    public UUID getConnectionOwner() {
        return plug.getChannelOwner();
    }

    public AccessPlugFluxTransfer getTransferHandler() {
        return transfer;
    }

    public World getFluxWorld() {
        return plug.getWorld();
    }

    public Coord4D getCoords() {
        Coord4D cached = coords;
        if (cached == null) {
            cached = new Coord4D(plug);
            coords = cached;
        }
        return cached;
    }

    public int getRawPriority() {
        return priority;
    }

    /**
     * @return the priority a network sorts this connector by, which places it among the storages so the channel is
     *         charged after the network served its consumers
     */
    public int getLogicPriority() {
        if (surgeMode) {
            return STORAGE_PRIORITY;
        }
        return Math.min(priority - STORAGE_PRIORITY_OFFSET, STORAGE_PRIORITY);
    }

    public long getRawLimit() {
        return limit;
    }

    /** @return the limit that applies, which is none while the limit is switched off */
    public long getLogicLimit() {
        return disableLimit ? Long.MAX_VALUE : limit;
    }

    /** @return the largest limit Flux Networks' GUI offers, a channel itself is not capped */
    public long getMaxTransferLimit() {
        return Long.MAX_VALUE;
    }

    public boolean getDisableLimit() {
        return disableLimit;
    }

    public boolean getSurgeMode() {
        return surgeMode;
    }

    public int getFolderID() {
        return folderID;
    }

    public String getCustomName() {
        return customName;
    }

    public boolean isForcedLoading() {
        return chunkLoading;
    }

    public boolean isChunkLoaded() {
        World world = plug.getWorld();
        return world != null && !plug.isInvalid();
    }

    /** @return whether the plug currently has a channel, so the network sees a usable storage */
    public boolean isActive() {
        return plug.isEnergyPortActive();
    }

    public boolean canAccess(EntityPlayer player) {
        UUID owner = getConnectionOwner();
        if (owner != null && owner.equals(player.getUniqueID())) {
            return true;
        }
        IFluxNetwork joined = network;
        if (joined == null || joined.isInvalid()) {
            return false;
        }
        AccessLevel level = joined.getMemberPermission(player);
        return level != null && level.canAccess();
    }

    public ItemStack getDisplayStack() {
        return new ItemStack(BlockAccessPlug.INSTANCE);
    }

    // ------------------------------------------------------------------- player

    /**
     * Opens the plug's own channel GUI, which is also where its Flux Networks network is switched (see
     * {@link #cycleNetwork}). Flux Networks' own connector GUI cannot be used for this: its handler casts the clicked
     * block to its own {@code TileFluxCore} class, which the plug is not, so a foreign connector has no way into it.
     */
    public void open(EntityPlayer player) {
        World world = plug.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        player.openGui(ModularGrid.instance, CommonProxy.GuiType.ACCESS_PLUG.ordinal(), world,
                plug.getPos().getX(), plug.getPos().getY(), plug.getPos().getZ());
    }

    public void close(EntityPlayer player) {
        // nothing is held open on our side, the container tracks its viewers itself
    }

    // -------------------------------------------------------------- persistence

    public NBTTagCompound writeCustomNBT(NBTTagCompound compound, NBTType type) {
        compound.setInteger(TAG_PRIORITY, priority);
        compound.setLong(TAG_LIMIT, limit);
        compound.setBoolean(TAG_DISABLE_LIMIT, disableLimit);
        compound.setBoolean(TAG_SURGE, surgeMode);
        compound.setInteger(TAG_NETWORK_ID, networkID);
        UUID owner = getConnectionOwner();
        if (owner != null) {
            compound.setUniqueId(TAG_OWNER, owner);
        }
        compound.setString(TAG_NAME, customName);
        compound.setBoolean(TAG_CHUNK_LOADING, chunkLoading);
        compound.setInteger(TAG_FOLDER_ID, folderID);
        compound.setInteger(TAG_FOLDER_ID_LONG, folderID);
        // and under the names a network level serialization uses
        compound.setInteger(TAG_LITE_NETWORK_ID, networkID);
        compound.setInteger(TAG_LITE_PRIORITY, priority);
        compound.setLong(TAG_LITE_LIMIT, limit);
        compound.setBoolean(TAG_LITE_DISABLE_LIMIT, disableLimit);
        compound.setBoolean(TAG_LITE_SURGE, surgeMode);
        compound.setString(TAG_LITE_NAME, customName);
        compound.setInteger(TAG_LITE_FOLDER_ID, folderID);
        compound.setBoolean(TAG_LITE_FORCED_CHUNK, chunkLoading);
        transfer.writeCustomNBT(compound, type);
        return compound;
    }

    /**
     * Applies a settings update. Flux Networks sends these as NBT (its GUI edits a copy of the connector), and its own
     * tiles use one key set for their saved data and another one for network updates, so both are accepted here.
     */
    public void readCustomNBT(NBTTagCompound compound, NBTType type) {
        boolean changed = false;

        int newPriority = compound.hasKey(TAG_PRIORITY) ? compound.getInteger(TAG_PRIORITY)
                : compound.getInteger(TAG_LITE_PRIORITY);
        if (newPriority != priority) {
            priority = newPriority;
            changed = true;
        }
        long newLimit = compound.hasKey(TAG_LIMIT) ? compound.getLong(TAG_LIMIT)
                : compound.getLong(TAG_LITE_LIMIT);
        if (!compound.hasKey(TAG_LIMIT) && !compound.hasKey(TAG_LITE_LIMIT)) {
            newLimit = limit;
        }
        if (newLimit != limit) {
            limit = newLimit;
            changed = true;
        }
        boolean newDisableLimit = compound.hasKey(TAG_DISABLE_LIMIT) ? compound.getBoolean(TAG_DISABLE_LIMIT)
                : compound.hasKey(TAG_LITE_DISABLE_LIMIT) ? compound.getBoolean(TAG_LITE_DISABLE_LIMIT) : disableLimit;
        if (newDisableLimit != disableLimit) {
            disableLimit = newDisableLimit;
            changed = true;
        }
        boolean newSurge = compound.hasKey(TAG_SURGE) ? compound.getBoolean(TAG_SURGE)
                : compound.hasKey(TAG_LITE_SURGE) ? compound.getBoolean(TAG_LITE_SURGE) : surgeMode;
        if (newSurge != surgeMode) {
            surgeMode = newSurge;
            changed = true;
        }
        String newName = compound.hasKey(TAG_NAME) ? compound.getString(TAG_NAME)
                : compound.hasKey(TAG_LITE_NAME) ? compound.getString(TAG_LITE_NAME) : customName;
        if (!newName.equals(customName)) {
            customName = newName;
            changed = true;
        }
        int newFolder = compound.hasKey(TAG_FOLDER_ID) ? compound.getInteger(TAG_FOLDER_ID)
                : compound.hasKey(TAG_LITE_FOLDER_ID) ? compound.getInteger(TAG_LITE_FOLDER_ID) : folderID;
        if (newFolder != folderID) {
            folderID = newFolder;
            changed = true;
        }
        boolean newChunkLoading = compound.hasKey(TAG_CHUNK_LOADING) ? compound.getBoolean(TAG_CHUNK_LOADING)
                : compound.hasKey(TAG_LITE_FORCED_CHUNK) ? compound.getBoolean(TAG_LITE_FORCED_CHUNK) : chunkLoading;
        if (newChunkLoading != chunkLoading) {
            chunkLoading = newChunkLoading;
            applyChunkLoading(chunkLoading);
            changed = true;
        }

        int newNetworkID = compound.hasKey(TAG_NETWORK_ID) ? compound.getInteger(TAG_NETWORK_ID)
                : compound.hasKey(TAG_LITE_NETWORK_ID) ? compound.getInteger(TAG_LITE_NETWORK_ID) : networkID;
        if (newNetworkID != networkID) {
            // the next tick joins the new network (or drops the connection when the id went back to -1)
            networkID = newNetworkID;
            joinRequested = false;
            changed = true;
        }

        transfer.readCustomNBT(compound, type);
        if (changed) {
            plug.markSettingsDirty();
            notifySettingsChanged();
        }
    }

    /** Stores the settings in the plug's own NBT, they outlive a world reload like any other connector's do. */
    public void writeSettings(NBTTagCompound compound) {
        NBTTagCompound settings = new NBTTagCompound();
        writeCustomNBT(settings, NBTType.TILE_SAVE);
        compound.setTag(TAG_SETTINGS, settings);
    }

    public void readSettings(NBTTagCompound compound) {
        if (compound.hasKey(TAG_SETTINGS)) {
            readCustomNBT(compound.getCompoundTag(TAG_SETTINGS), NBTType.TILE_SAVE);
        }
    }

    // -------------------------------------------------------------------- helper

    private AccessPlugFluxConnector connector() {
        return (AccessPlugFluxConnector) plug;
    }
    private void applyChunkLoading(boolean loading) {
        World world = plug.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        ChunkPos chunk = new ChunkPos(plug.getPos().getX() >> 4, plug.getPos().getZ() >> 4);
        if (loading) {
            FluxChunkManager.forceChunk(world, chunk);
        } else {
            FluxChunkManager.releaseChunk(world, chunk);
        }
    }

    /** Lets the network refresh the copy of this connector its clients hold, so a settings change is visible. */
    private void notifySettingsChanged() {
        IFluxNetwork joined = network;
        if (joined instanceof FluxNetworkServer server) {
            server.markLiteSettingChanged(connector());
        }
    }
}
