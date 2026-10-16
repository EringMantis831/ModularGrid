package com.modulargrid.common.integration.circulation;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.INode;
import com.modulargrid.ModularGrid;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

/**
 * Registers the access plug with Circulation Flow Networks.
 */
public final class CirculationIntegration {

    private static boolean available;

    private CirculationIntegration() {
    }

    /** Called from the mod's preInit, registers the energy handler once. */
    public static void preInit() {
        available = Loader.isModLoaded("circulation_networks");
        if (available) {
            registerHandler();
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    @Optional.Method(modid = "circulation_networks")
    public static void validateNode(TileAccessPlug plug) {
        AccessPlugCfnNode.validate(plug);
    }

    @Optional.Method(modid = "circulation_networks")
    public static void invalidateNode(TileAccessPlug plug) {
        AccessPlugCfnNode.invalidate(plug);
    }

    @Optional.Method(modid = "circulation_networks")
    public static void syncNodeAfterNetworkInit(TileAccessPlug plug) {
        AccessPlugCfnNode.syncAfterNetworkInit(plug);
    }

    @Optional.Method(modid = "circulation_networks")
    public static void writeNode(TileAccessPlug plug, NBTTagCompound compound) {
        INode node = AccessPlugCfnNode.getNode(plug);
        if (node != null) {
            compound.setTag("circulationNode", node.serialize());
        }
        compound.setBoolean("circulationNodeRegistered", plug.isCirculationNodeRegistered());
    }

    @Optional.Method(modid = "circulation_networks")
    public static void readNode(TileAccessPlug plug, NBTTagCompound compound) {
        if (compound.hasKey("circulationNode")) {
            plug.setCirculationNode(com.circulation.circulation_networks.registry.NodeTypes.deserialize(
                    compound.getCompoundTag("circulationNode")));
        }
        plug.setCirculationNodeRegistered(compound.getBoolean("circulationNodeRegistered"));
    }

    @Optional.Method(modid = "circulation_networks")
    private static void registerHandler() {
        API.registerEnergyHandler(new AccessPlugEnergyHandlerManager());
        ModularGrid.LOGGER.info("Circulation Flow Networks is installed, access plugs are registered as its energy devices");
    }
}
