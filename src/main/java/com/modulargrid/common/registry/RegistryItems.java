package com.modulargrid.common.registry;

import hellfirepvp.modularmachinery.common.item.ItemDynamicColor;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Item registration, wired to the forge registry events.
 */
public class RegistryItems {

    public static final List<Item> ITEMS_TO_REGISTER    = new ArrayList<>();
    public static final List<Item> ITEM_MODEL_TO_REGISTER = new ArrayList<>();
    public static final List<Item> DYNAMIC_COLOR_ITEMS  = new ArrayList<>();

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        GenericRegistryPrimer.INSTANCE.wipe(event.getGenericType());

        registerItems();

        GenericRegistryPrimer.INSTANCE.fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    public static void registerItems() {
        ITEMS_TO_REGISTER.forEach(RegistryItems::registerItem);
        ITEMS_TO_REGISTER.clear();
    }

    @SideOnly(Side.CLIENT)
    public static void registerItemModels() {
        ITEM_MODEL_TO_REGISTER.forEach(RegistryItems::registerItemModel);
        ITEM_MODEL_TO_REGISTER.clear();
    }

    public static <T extends Item> T registerItem(T item) {
        ITEM_MODEL_TO_REGISTER.add(item);
        if (item instanceof ItemDynamicColor) {
            DYNAMIC_COLOR_ITEMS.add(item);
        }
        GenericRegistryPrimer.INSTANCE.register(item);
        return item;
    }

    @SideOnly(Side.CLIENT)
    public static void registerItemModel(Item item) {
        ResourceLocation registryName = Objects.requireNonNull(item.getRegistryName());
        ModelBakery.registerItemVariants(item, registryName);
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(registryName, "inventory"));
    }

}
