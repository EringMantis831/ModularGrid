package com.modulargrid.common.block;

import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;

/**
 * Implemented by tile entities that need to know which multiblock controller they are part of.
 *
 * <p>Modular Machinery binds such components through explicit callbacks (see
 * {@code TileUpgradeBus.UpgradeBusProvider}, {@code TileSmartInterface.SmartInterfaceProvider}),
 * but it has no generic hook for addon components. ModularGrid therefore injects into
 * {@code TileMultiblockMachineController#addComponent} and calls {@link #boundMachine} for every
 * component tile implementing this interface, mirroring MMCE's own pattern.</p>
 *
 * <p>The binding is re-established on every structure (re)build, so implementations simply
 * replace their controller reference. There is no unbind callback: a plug validates its
 * controller reference lazily (see {@code TileAccessPlug}).</p>
 */
public interface AccessPlugProvider {

    /**
     * Called when the given controller discovers this tile as part of its structure.
     *
     * @param controller the controller this component now belongs to, never {@code null}
     */
    void boundMachine(TileMultiblockMachineController controller);

}
