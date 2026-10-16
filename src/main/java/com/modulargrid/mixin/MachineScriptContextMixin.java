package com.modulargrid.mixin;

import com.modulargrid.common.channel.ChannelScriptContext;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the machine whose tick is running visible to script code, see {@link ChannelScriptContext}.
 */
@Mixin(value = TileMultiblockMachineController.class, remap = false)
public class MachineScriptContextMixin {

    @Inject(method = "doRestrictedTick", at = @At("HEAD"))
    private void modulargrid$pushChannelContext(CallbackInfo callbackInfo) {
        ChannelScriptContext.push(((TileMultiblockMachineController) (Object) this).getOwner());
    }

    @Inject(method = "doRestrictedTick", at = @At("RETURN"))
    private void modulargrid$popChannelContext(CallbackInfo callbackInfo) {
        ChannelScriptContext.pop();
    }

}
