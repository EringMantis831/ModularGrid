package com.modulargrid.common.network;

import com.modulargrid.common.tile.TileAccessPlug;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Switches the Flux Networks network of one access plug, sent when the player clicks the network row of the plug's GUI.
 *
 * <p>Only a step ({@code +1} / {@code -1}) is sent: the server picks the next network the player may join, which keeps
 * the client free of any knowledge about the networks that exist and re-checks the permission on the server side. Like
 * {@link PktChannelToggle} the message handles itself and the server answers with the plug's regular tile entity update,
 * so the client never has to guess the result.</p>
 */
public class PktFluxNetworkCycle implements IMessage, IMessageHandler<PktFluxNetworkCycle, IMessage> {

    private BlockPos pos = new BlockPos(0, 0, 0);
    private int step = 1;

    public PktFluxNetworkCycle() {
    }

    public PktFluxNetworkCycle(BlockPos pos, int step) {
        this.pos = pos;
        this.step = step;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.step = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(step);
    }

    @Override
    public IMessage onMessage(PktFluxNetworkCycle message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        World world = player.world;
        TileEntity tile = world.getTileEntity(message.pos);
        if (!(tile instanceof TileAccessPlug plug) || plug.getWorld() != world) {
            return null;
        }
        if (!plug.isPlugOwner(player)) {
            player.sendMessage(new TextComponentTranslation("message.modulargrid.notOwner"));
            return null;
        }
        if (!plug.cycleFluxNetwork(player, message.step)) {
            player.sendMessage(new TextComponentTranslation("message.modulargrid.fluxMissing"));
        }
        return null;
    }
}
