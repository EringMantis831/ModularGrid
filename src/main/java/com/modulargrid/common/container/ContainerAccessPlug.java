package com.modulargrid.common.container;

import com.modulargrid.common.tile.TileAccessPlug;
import hellfirepvp.modularmachinery.common.container.ContainerBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

/**
 * Container of the access plug GUI.
 */
public class ContainerAccessPlug extends ContainerBase<TileAccessPlug> {

    private static final int INVENTORY_LEFT = 112;
    private static final int INVENTORY_TOP = 131;
    private static final int HOTBAR_TOP = 189;

    public ContainerAccessPlug(TileAccessPlug owner, EntityPlayer opening) {
        super(owner, opening);
        // lets the plug push value updates while somebody is looking at it
        owner.addViewer(opening);
    }

    @Override
    protected void addPlayerSlots(EntityPlayer opening) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Slot(opening.inventory, column + row * 9 + 9,
                        INVENTORY_LEFT + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new Slot(opening.inventory, column, INVENTORY_LEFT + column * 18, HOTBAR_TOP));
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        owner.removeViewer(playerIn);
    }

}
