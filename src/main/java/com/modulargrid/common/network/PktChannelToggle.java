package com.modulargrid.common.network;

import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.channel.ChannelRegistry;
import com.modulargrid.common.tile.TileAccessPlug;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Switches one channel on or off for one access plug.
 */
public class PktChannelToggle implements IMessage, IMessageHandler<PktChannelToggle, IMessage> {

    private BlockPos pos = new BlockPos(0, 0, 0);
    private String address = "";
    private boolean enabled;

    public PktChannelToggle() {
    }

    public PktChannelToggle(BlockPos pos, String address, boolean enabled) {
        this.pos = pos;
        this.address = address;
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.address = ByteBufUtils.readUTF8String(buf);
        this.enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, address);
        buf.writeBoolean(enabled);
    }

    @Override
    public IMessage onMessage(PktChannelToggle message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        World world = player.world;
        TileEntity tile = world.getTileEntity(message.pos);
        if (!(tile instanceof TileAccessPlug plug) || plug.getWorld() != world) {
            return null;
        }
        // only the owner of the machine may reconfigure its plug
        if (!plug.isOwner(player)) {
            player.sendMessage(new TextComponentTranslation("message.modulargrid.notOwner"));
            return null;
        }
        Channel channel = ChannelRegistry.getByAddress(world, message.address);
        if (channel == null) {
            // the channel was deleted between the click and the packet
            player.sendMessage(new TextComponentTranslation("message.modulargrid.channelGone"));
            return null;
        }
        plug.setChannelEnabled(channel, message.enabled);
        return null;
    }

}
