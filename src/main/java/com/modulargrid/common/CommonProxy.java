package com.modulargrid.common;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.container.ContainerAccessPlug;
import com.modulargrid.common.network.NetworkHandler;
import com.modulargrid.common.registry.RegistryBlocks;
import com.modulargrid.common.registry.RegistryItems;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import javax.annotation.Nullable;

/**
 * Common (server + client) side proxy.
 */
public class CommonProxy implements IGuiHandler {

    public CommonProxy() {
        // Registry events are fired after every mod's preInit, so this is early enough
        MinecraftForge.EVENT_BUS.register(new RegistryBlocks());
        MinecraftForge.EVENT_BUS.register(new RegistryItems());
    }

    public void preInit() {
        NetworkHandler.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(ModularGrid.MOD_ID, this);
    }

    public void init() {
    }

    // ------------------------------------------------------------------ gui

    /** GUI ids, the ordinal doubles as the id passed to the GUI handler. */
    public enum GuiType {

        ACCESS_PLUG(TileAccessPlug.class);

        /** Tile entity the world position has to hold for this GUI to open. */
        public final Class<? extends TileEntity> requiredTileEntity;

        GuiType(Class<? extends TileEntity> requiredTileEntity) {
            this.requiredTileEntity = requiredTileEntity;
        }

        @Nullable
        public static GuiType byId(int id) {
            GuiType[] values = values();
            return id < 0 || id >= values.length ? null : values[id];
        }

    }

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        GuiType type = GuiType.byId(id);
        if (type == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (type.requiredTileEntity == null || !type.requiredTileEntity.isInstance(tile)) {
            return null;
        }
        if (type == GuiType.ACCESS_PLUG) {
            TileAccessPlug plug = (TileAccessPlug) tile;
            return plug.isPlugOwner(player) ? new ContainerAccessPlug(plug, player) : null;
        }
        return null;
    }

    /** @return {@code null} on the server, the client proxy creates the actual screen */
    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

}
