package com.modulargrid.common.registry;

import com.modulargrid.Tags;
import com.modulargrid.common.block.BlockAccessPlug;
import com.modulargrid.common.tile.TileAccessPlug;
import hellfirepvp.modularmachinery.common.block.BlockDynamicColor;
import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Block registration, wired to the forge registry events.
 */
public class RegistryBlocks {

    public static final List<Block> BLOCK_MODEL_TO_REGISTER = new ArrayList<>();
    public static final List<Block> DYNAMIC_COLOR_BLOCKS     = new ArrayList<>();

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        GenericRegistryPrimer.INSTANCE.wipe(event.getGenericType());

        registerBlocks();
        registerTileEntities();

        GenericRegistryPrimer.INSTANCE.fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    public static void registerBlocks() {
        // 流通数据节点 / Data Flow Access Plug
        prepareItemBlockRegister(registerBlock(BlockAccessPlug.INSTANCE));
    }

    public static void registerTileEntities() {
        registerTileEntity(TileAccessPlug.class, BlockAccessPlug.REGISTRY_NAME);
    }

    @SideOnly(Side.CLIENT)
    public static void registerBlockModels() {
        BLOCK_MODEL_TO_REGISTER.forEach(RegistryBlocks::registerBlockModel);
        BLOCK_MODEL_TO_REGISTER.clear();
    }

    public static void registerTileEntity(Class<? extends TileEntity> tile, String name) {
        GameRegistry.registerTileEntity(tile, new ResourceLocation(Tags.MOD_ID, name));
    }

    public static <T extends Block> T registerBlock(T block) {
        BLOCK_MODEL_TO_REGISTER.add(block);
        if (block instanceof BlockDynamicColor) {
            DYNAMIC_COLOR_BLOCKS.add(block);
        }
        GenericRegistryPrimer.INSTANCE.register(block);
        return block;
    }

    /**
     * Machine components get MMCE's item block, which carries the machine color tint.
     */
    public static ItemBlock prepareItemBlockRegister(Block block) {
        if (block instanceof BlockMachineComponent) {
            return prepareItemBlockRegister(new ItemBlockMachineComponent(block));
        }
        return prepareItemBlockRegister(new ItemBlock(block));
    }

    public static <T extends ItemBlock> T prepareItemBlockRegister(T item) {
        Block block = item.getBlock();
        ResourceLocation registryName = Objects.requireNonNull(block.getRegistryName());
        item.setRegistryName(registryName).setTranslationKey(block.getTranslationKey());
        RegistryItems.ITEMS_TO_REGISTER.add(item);
        return item;
    }

    @SideOnly(Side.CLIENT)
    public static void registerBlockModel(Block block) {
        Item item = Item.getItemFromBlock(block);
        ResourceLocation registryName = Objects.requireNonNull(item.getRegistryName());
        ModelBakery.registerItemVariants(item, registryName);
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(registryName, "inventory"));
    }

}
