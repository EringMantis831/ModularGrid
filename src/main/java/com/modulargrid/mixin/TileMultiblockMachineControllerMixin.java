package com.modulargrid.mixin;

import com.modulargrid.common.block.AccessPlugProvider;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentSelectorTag;
import hellfirepvp.modularmachinery.common.crafting.helper.ProcessingComponent;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Binds {@link AccessPlugProvider} components to the controller that discovered them.
 *
 * <p>{@code TileMultiblockMachineController#addComponent} is the single point where every
 * structure component is registered, so injecting at its tail covers both the initial
 * structure build and every rebuild. MMCE itself does the same kind of type check for its
 * upgrade bus and smart interface, it just has no generic hook for addons.</p>
 */
@Mixin(value = TileMultiblockMachineController.class, remap = false)
public class TileMultiblockMachineControllerMixin {

    @Inject(method = "addComponent", at = @At("TAIL"))
    private void modulargrid$bindAccessPlug(MachineComponent<?> component, ComponentSelectorTag selectorTag,
                                            TileEntity tileEntity,
                                            Map<Long, Map<TileEntity, ProcessingComponent<?>>> components,
                                            CallbackInfo callbackInfo) {
        if (tileEntity instanceof AccessPlugProvider accessPlug) {
            accessPlug.boundMachine((TileMultiblockMachineController) (Object) this);
        }
    }

}
