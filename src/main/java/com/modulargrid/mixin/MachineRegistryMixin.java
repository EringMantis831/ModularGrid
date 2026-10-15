package com.modulargrid.mixin;

import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.machine.EnergyHatchPatternCompat;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineRegistry;
import net.minecraft.command.ICommandSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Extends every loaded machine with the access plug wherever it accepts an energy hatch, see
 * {@link EnergyHatchPatternCompat}.
 *
 * <p>These two methods are the only places machines enter the registry: the initial load during
 * Modular Machinery's postInit and every reload triggered by a CraftTweaker reload. Both run before
 * Modular Machinery rebuilds its rotated pattern cache, so the plug ends up in the cache as well
 * and no machine data has to be patched afterwards. {@code loadMachines} additionally rereads the firm channel
 * configuration, so {@code /modularmachinery reload} covers both.</p>
 */
@Mixin(value = MachineRegistry.class, remap = false)
public class MachineRegistryMixin {

    @Inject(method = "registerMachines", at = @At("HEAD"))
    private static void modulargrid$acceptAccessPlugInRegisteredMachines(Collection<DynamicMachine> machines,
                                                                        CallbackInfo callbackInfo) {
        EnergyHatchPatternCompat.apply(machines);
    }

    @Inject(method = "reloadMachine", at = @At("HEAD"))
    private static void modulargrid$acceptAccessPlugInReloadedMachines(Collection<DynamicMachine> machines,
                                                                      CallbackInfo callbackInfo) {
        EnergyHatchPatternCompat.apply(machines);
    }

    /**
     * A machine reload is the "reload the pack" moment of Modular Machinery, so the firm channel configuration is read
     * again with it. The access plug pattern injection above runs on the same reload.
     */
    @Inject(method = "loadMachines", at = @At("HEAD"))
    private static void modulargrid$rereadFirmChannels(ICommandSender sender,
                                                       CallbackInfoReturnable<Collection<DynamicMachine>> callbackInfo) {
        FirmChannelConfig.reload();
    }

}
