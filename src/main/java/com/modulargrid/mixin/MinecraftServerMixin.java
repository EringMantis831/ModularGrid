package com.modulargrid.mixin;

import com.modulargrid.ExampleMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "loadAllWorlds", at = @At("HEAD"))
    private void modulargrid$onLoadAllWorlds(CallbackInfo ci) {
        ExampleMod.LOGGER.info("Mixin into MinecraftServer#loadAllWorlds works!");
    }
}
