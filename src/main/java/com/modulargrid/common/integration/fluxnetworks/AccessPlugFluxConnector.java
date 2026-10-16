package com.modulargrid.common.integration.fluxnetworks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import sonar.fluxnetworks.api.network.ConnectionType;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.api.network.ITransferHandler;
import sonar.fluxnetworks.api.tiles.IFluxStorage;
import sonar.fluxnetworks.api.utils.Coord4D;
import sonar.fluxnetworks.api.utils.NBTType;

import java.util.UUID;

/**
 * Turns an access plug into a Flux Networks storage connector, so the plug can be part of a network without a
 * separate Flux plug or point next to it.
 */
public interface AccessPlugFluxConnector extends IFluxStorage {

    /** @return the Flux Networks settings and transfer state of this plug */
    AccessPlugFluxState getFluxState();

    // ------------------------------------------------------------------ identity

    @Override
    default int getNetworkID() {
        return getFluxState().getNetworkID();
    }

    @Override
    default IFluxNetwork getNetwork() {
        return getFluxState().getNetwork();
    }

    @Override
    default UUID getConnectionOwner() {
        return getFluxState().getConnectionOwner();
    }

    @Override
    default ConnectionType getConnectionType() {
        return ConnectionType.STORAGE;
    }

    @Override
    default World getFluxWorld() {
        return getFluxState().getFluxWorld();
    }

    @Override
    default Coord4D getCoords() {
        return getFluxState().getCoords();
    }

    // ------------------------------------------------------------------- settings

    @Override
    default int getRawPriority() {
        return getFluxState().getRawPriority();
    }

    @Override
    default int getLogicPriority() {
        return getFluxState().getLogicPriority();
    }

    @Override
    default long getRawLimit() {
        return getFluxState().getRawLimit();
    }

    @Override
    default long getLogicLimit() {
        return getFluxState().getLogicLimit();
    }

    @Override
    default long getMaxTransferLimit() {
        return getFluxState().getMaxTransferLimit();
    }

    @Override
    default boolean getDisableLimit() {
        return getFluxState().getDisableLimit();
    }

    @Override
    default boolean getSurgeMode() {
        return getFluxState().getSurgeMode();
    }

    @Override
    default int getFolderID() {
        return getFluxState().getFolderID();
    }

    @Override
    default String getCustomName() {
        return getFluxState().getCustomName();
    }

    @Override
    default boolean isForcedLoading() {
        return getFluxState().isForcedLoading();
    }

    @Override
    default boolean isChunkLoaded() {
        return getFluxState().isChunkLoaded();
    }

    @Override
    default boolean isActive() {
        return getFluxState().isActive();
    }

    @Override
    default boolean canAccess(EntityPlayer player) {
        return getFluxState().canAccess(player);
    }

    // ------------------------------------------------------------- network cycle

    @Override
    default void connect(IFluxNetwork network) {
        getFluxState().connect(network);
    }

    @Override
    default void disconnect(IFluxNetwork network) {
        getFluxState().disconnect(network);
    }

    @Override
    default ITransferHandler getTransferHandler() {
        return getFluxState().getTransferHandler();
    }

    @Override
    default long getTransferBuffer() {
        return getTransferHandler().getBuffer();
    }

    @Override
    default long getTransferChange() {
        return getTransferHandler().getChange();
    }

    // ---------------------------------------------------------------- persistence

    @Override
    default NBTTagCompound writeCustomNBT(NBTTagCompound compound, NBTType type) {
        return getFluxState().writeCustomNBT(compound, type);
    }

    @Override
    default void readCustomNBT(NBTTagCompound compound, NBTType type) {
        getFluxState().readCustomNBT(compound, type);
    }

    // --------------------------------------------------------------------- player

    @Override
    default void open(EntityPlayer player) {
        getFluxState().open(player);
    }

    @Override
    default void close(EntityPlayer player) {
        getFluxState().close(player);
    }

    @Override
    default ItemStack getDisplayStack() {
        return getFluxState().getDisplayStack();
    }
}
