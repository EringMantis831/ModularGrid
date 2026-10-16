package com.modulargrid.common.network;

import com.modulargrid.ModularGrid;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * ModularGrid's network channel.
 */
public final class NetworkHandler {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(ModularGrid.MOD_ID);

    private NetworkHandler() {
    }

    /** Called from both proxies during preInit. */
    public static void register() {
        CHANNEL.registerMessage(PktChannelToggle.class, PktChannelToggle.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PktFluxNetworkCycle.class, PktFluxNetworkCycle.class, 1, Side.SERVER);
    }

    public static void sendToServer(IMessage message) {
        CHANNEL.sendToServer(message);
    }

}
