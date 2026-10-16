package com.modulargrid.common.integration.circulation;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * Tells Circulation Flow Networks that access plugs are energy devices.
 */
public final class AccessPlugEnergyHandlerManager implements IEnergyHandlerManager {

    /** Priority this manager registers itself with. */
    public static final int PRIORITY = 60;

    @Override
    public boolean isAvailable(TileEntity tile) {
        return tile instanceof TileAccessPlug;
    }

    @Override
    public boolean isAvailable(ItemStack stack) {
        // the plug is a block, it has no item form that could be an energy device
        return false;
    }

    @Override
    public Class<? extends IEnergyHandler> getEnergyHandlerClass() {
        return AccessPlugEnergyHandler.class;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new AccessPlugEnergyHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        // never asked for, isAvailable(ItemStack) is false
        return new AccessPlugEnergyHandler();
    }
}
