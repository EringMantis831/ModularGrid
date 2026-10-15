package com.modulargrid.client;

import com.modulargrid.client.gui.GuiAccessPlug;
import com.modulargrid.common.CommonProxy;
import com.modulargrid.common.registry.RegistryBlocks;
import com.modulargrid.common.registry.RegistryItems;
import com.modulargrid.common.tile.TileAccessPlug;
import hellfirepvp.modularmachinery.common.block.BlockDynamicColor;
import hellfirepvp.modularmachinery.common.item.ItemDynamicColor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Client side proxy. Handles model and color registration.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public ClientProxy() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Fired after the block/item registry events, so the model lists are already filled.
     */
    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event) {
        RegistryBlocks.registerBlockModels();
        RegistryItems.registerItemModels();
    }

    /**
     * MMCE tints machine components with the machine color, the handlers have to be
     * registered on the client color managers.
     */
    @Override
    public void init() {
        BlockColors blockColors = Minecraft.getMinecraft().getBlockColors();
        for (Block block : RegistryBlocks.DYNAMIC_COLOR_BLOCKS) {
            blockColors.registerBlockColorHandler(((BlockDynamicColor) block)::getColorMultiplier, block);
        }

        ItemColors itemColors = Minecraft.getMinecraft().getItemColors();
        for (Item item : RegistryItems.DYNAMIC_COLOR_ITEMS) {
            itemColors.registerItemColorHandler(((ItemDynamicColor) item)::getColorFromItemstack, item);
        }
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (GuiType.byId(id) != GuiType.ACCESS_PLUG) {
            return null;
        }
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        return tile instanceof TileAccessPlug plug ? new GuiAccessPlug(plug, player) : null;
    }

}
