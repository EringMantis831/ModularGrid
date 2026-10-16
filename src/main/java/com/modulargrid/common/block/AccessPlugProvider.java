package com.modulargrid.common.block;

import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;

/**
 * Implemented by tile entities that need to know which multiblock controller they are part of.
 */
public interface AccessPlugProvider {

    /**
     * Called when the given controller discovers this tile as part of its structure.
     *
     * @param controller the controller this component now belongs to, never {@code null}
     */
    void boundMachine(TileMultiblockMachineController controller);

}
