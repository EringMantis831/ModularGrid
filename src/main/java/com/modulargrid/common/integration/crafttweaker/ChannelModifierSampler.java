package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.channel.ChannelRegistry;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.channel.FirmChannelDefinition;
import com.modulargrid.common.tile.TileAccessPlug;
import github.kasuminova.mmce.common.event.recipe.FactoryRecipeStartEvent;
import github.kasuminova.mmce.common.event.recipe.RecipeEvent;
import github.kasuminova.mmce.common.event.recipe.RecipeStartEvent;
import hellfirepvp.modularmachinery.common.crafting.helper.ProcessingComponent;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Samples channel-provided modifiers once into the context of a recipe as it starts. */
final class ChannelModifierSampler {

    /**
     * Last logged state per machine and declaration. A machine that runs a recipe forever would otherwise fill the log
     * with the same line every cycle, while the interesting moments - the sample started or stopped being applied, or
     * its value changed - are rare.
     */
    private static final java.util.Map<String, String> LAST_STATE = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public void onRecipeStart(RecipeEvent event) {
        if (!(event instanceof RecipeStartEvent) && !(event instanceof FactoryRecipeStartEvent)) {
            return;
        }
        if (!ChannelModifierRegistry.hasSampledModifiers()) {
            return;
        }
        TileMultiblockMachineController controller = event.getController();
        RecipeCraftingContext context = event.getContext();
        if (controller == null || context == null) {
            return;
        }
        String machineName = controller.getFormedMachineName();
        if (machineName == null) {
            return;
        }
        UUID owner = controller.getOwner();
        World world = DimensionManager.getWorld(0);
        if (world == null) {
            return;
        }

        Set<TileAccessPlug> plugs = getAccessPlugs(controller);
        if (plugs.isEmpty()) {
            return;
        }

        for (ChannelModifierRegistry.SampledDeclaration declaration : ChannelModifierRegistry.getSampledFor(machineName)) {
            FirmChannelDefinition definition = FirmChannelConfig.find(declaration.channelId());
            if (definition == null || !definition.allowsMachine(machineName)) {
                continue;
            }
            UUID channelOwner = definition.isPublic() ? null : owner;
            Channel channel = ChannelRegistry.getOrCreateFirmValue(world, definition, channelOwner);
            String stateKey = machineName + '#' + declaration.channelId() + '#' + declaration.template().serialize();
            if (channel == null || !isEnabledOnAnyPlug(plugs, channel)) {
                logOnce(stateKey, "off", "Machine {} samples channel '{}' but the channel is not enabled on its access "
                        + "plug, so its modifiers are not applied", machineName, declaration.channelId());
                continue;
            }
            double amount = channel.isEnergy() ? channel.getEnergyValue() : channel.getFloatValue();
            try {
                RecipeModifier sampled = declaration.hasExpression()
                        ? declaration.sample(channelId -> readEnabledChannel(channelId, machineName, owner, world,
                                plugs))
                        : declaration.sample(amount);
                context.addModifier(sampled);
                logOnce(stateKey, sampled.serialize().toString(), "Machine {} samples channel '{}' -> {}",
                        machineName, declaration.channelId(), sampled.serialize());
            } catch (RuntimeException exception) {
                // A bad expression or compatibility callback should not prevent MMCE from starting the recipe.
                com.modulargrid.ModularGrid.LOGGER.error("Could not sample channel modifier '{}' for machine {}",
                        declaration.channelId(), machineName, exception);
            }
        }
    }

    /** Logs one line while the state is new, so a machine that keeps running reports a change instead of every cycle. */
    private static void logOnce(String stateKey, String state, String message, Object... arguments) {
        if (state.equals(LAST_STATE.put(stateKey, state))) {
            return;
        }
        com.modulargrid.ModularGrid.LOGGER.info(message, arguments);
    }

    private static double readEnabledChannel(String channelId, String machineName, UUID owner, World world,
                                              Set<TileAccessPlug> plugs) {
        FirmChannelDefinition definition = FirmChannelConfig.find(channelId);
        if (definition == null || !definition.allowsMachine(machineName)) {
            return 0.0D;
        }
        UUID channelOwner = definition.isPublic() ? null : owner;
        Channel channel = ChannelRegistry.getFirmValue(world, definition, channelOwner);
        return channel == null || !isEnabledOnAnyPlug(plugs, channel) ? 0.0D
                : channel.isEnergy() ? channel.getEnergyValue() : channel.getFloatValue();
    }

    private static boolean isEnabledOnAnyPlug(Set<TileAccessPlug> plugs, Channel channel) {
        for (TileAccessPlug plug : plugs) {
            if (plug.isChannelEnabled(channel)) {
                return true;
            }
        }
        return false;
    }

    private static Set<TileAccessPlug> getAccessPlugs(TileMultiblockMachineController controller) {
        Set<TileAccessPlug> plugs = new LinkedHashSet<>();
        addPlugs(controller.getGeneralComponents(), plugs);
        for (Map<TileEntity, ProcessingComponent<?>> components : controller.getFoundComponents().values()) {
            addPlugs(components, plugs);
        }
        return plugs;
    }

    private static void addPlugs(Map<TileEntity, ProcessingComponent<?>> components, Set<TileAccessPlug> plugs) {
        for (TileEntity tile : components.keySet()) {
            if (tile instanceof TileAccessPlug plug) {
                plugs.add(plug);
            }
        }
    }

}
