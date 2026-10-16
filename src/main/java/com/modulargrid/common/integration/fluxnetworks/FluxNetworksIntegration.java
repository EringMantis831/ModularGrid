package com.modulargrid.common.integration.fluxnetworks;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

/**
 * The only door between the access plug and the Flux Networks integration.
 */
public final class FluxNetworksIntegration {

    /** NBT keys the integration uses to show the plug's network in its own GUI. */
    public static final String NBT_NETWORK_ID = "fluxNetworkId";
    public static final String NBT_NETWORK_NAME = "fluxNetworkName";

    private static boolean available;

    private FluxNetworksIntegration() {
    }

    /** Called from the mod's preInit, decides once whether the integration exists. */
    public static void preInit() {
        available = Loader.isModLoaded("fluxnetworks");
        if (available) {
            ModularGrid.LOGGER.info("Flux Networks is installed, access plugs can join a network as storage connectors");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** Called every tick from the plug, joins or leaves the network the plug is assigned to. */
    @Optional.Method(modid = "fluxnetworks")
    public static void tick(TileAccessPlug plug) {
        plug.getFluxState().update();
    }

    /**
     * Called when the player uses the plug's GUI network row: steps to the next (or previous) network the player may
     * join. Flux Networks' own connector GUI cannot be opened for the plug - its handler casts the clicked block to
     * its own {@code TileFluxCore} class - so this is the plug's way into a network.
     *
     * @return {@code true} if the click was handled
     */
    public static boolean cycleNetwork(TileAccessPlug plug, EntityPlayer player, int step) {
        if (!available) {
            return false;
        }
        applyCycle(plug, player, step);
        return true;
    }

    /** Writes the plug's Flux Networks state into a tile entity update, so the GUI can show it. */
    @Optional.Method(modid = "fluxnetworks")
    public static void writeClientState(TileAccessPlug plug, NBTTagCompound compound) {
        AccessPlugFluxState state = plug.getFluxState();
        compound.setInteger(NBT_NETWORK_ID, state.getNetworkID());
        compound.setString(NBT_NETWORK_NAME, state.getNetworkName());
    }

    /** The actual network switch, a separate method so Forge can strip it when the mod is missing. */
    @Optional.Method(modid = "fluxnetworks")
    private static void applyCycle(TileAccessPlug plug, EntityPlayer player, int step) {
        plug.getFluxState().cycleNetwork(player, step);
    }

    /** Called when the plug leaves the world, so the network does not keep a stale connector. */
    @Optional.Method(modid = "fluxnetworks")
    public static void detach(TileAccessPlug plug) {
        plug.getFluxState().onRemoved();
    }

    /**
     * Called when the plug is right-clicked. Kept as the entry point for Flux Networks itself (its connector
     * interface calls {@code open}), it shows the plug's own channel GUI.
     */
    @Optional.Method(modid = "fluxnetworks")
    public static void openConnectorGui(TileAccessPlug plug, EntityPlayer player) {
        plug.getFluxState().open(player);
    }

    @Optional.Method(modid = "fluxnetworks")
    public static void writeSettings(TileAccessPlug plug, NBTTagCompound compound) {
        plug.getFluxState().writeSettings(compound);
    }

    @Optional.Method(modid = "fluxnetworks")
    public static void readSettings(TileAccessPlug plug, NBTTagCompound compound) {
        plug.getFluxState().readSettings(compound);
    }
}
