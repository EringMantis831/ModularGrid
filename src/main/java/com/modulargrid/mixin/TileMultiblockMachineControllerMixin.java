package com.modulargrid.mixin;

import com.modulargrid.common.block.AccessPlugProvider;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentSelectorTag;
import hellfirepvp.modularmachinery.common.crafting.helper.ProcessingComponent;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.BlockArrayCache;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Binds {@link AccessPlugProvider} components to the controller that discovered them.
 */
@Mixin(value = TileMultiblockMachineController.class, remap = false)
public class TileMultiblockMachineControllerMixin {

    /** Binds every plug of the structure before Modular Machinery collects the machine's components. */
    @Inject(method = "updateComponents", at = @At("HEAD"))
    private void modulargrid$bindAccessPlugs(CallbackInfo callbackInfo) {
        TileMultiblockMachineController controller = (TileMultiblockMachineController) (Object) this;
        TaggedPositionBlockArray pattern = controller.getFoundPattern();
        if (pattern == null || controller.getWorld() == null || controller.getControllerRotation() == null) {
            return;
        }
        TaggedPositionBlockArray rotated = BlockArrayCache.getBlockArrayCache(pattern, controller.getControllerRotation());
        for (BlockPos offset : rotated.getPattern().keySet()) {
            TileEntity tile = controller.getWorld().getTileEntity(controller.getPos().add(offset));
            if (tile instanceof AccessPlugProvider accessPlug) {
                accessPlug.boundMachine(controller);
            }
        }
    }

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
