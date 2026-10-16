package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.channel.ChannelOps;
import com.modulargrid.common.channel.ChannelRegistry;
import com.modulargrid.common.channel.ChannelScriptContext;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.channel.FirmChannelDefinition;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import github.kasuminova.mmce.common.event.recipe.RecipeEvent;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.RecipePrimer;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.annotations.ZenMethod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Adds {@code addDataConfig} to Modular Machinery's recipe builder, the one recipe method ModularGrid brings.
 *
 * <pre>
 * RecipeBuilder.newBuilder("modularmachinery:alloy_furnace", "heating", 200)
 *     .addDataConfig("temp", 500, 0, "input", false)
 * </pre>
 *
 * <table>
 *     <caption>Where the write lands</caption>
 *     <tr><th>ioType</th><th>isPerTick</th><th>handler</th><th>when</th></tr>
 *     <tr><td>{@code input}</td><td>{@code false}</td><td>{@code addStartHandler}</td><td>inputs are consumed, the
 *         recipe starts</td></tr>
 *     <tr><td>{@code output}</td><td>{@code false}</td><td>{@code addFinishHandler}</td><td>the outputs are
 *         produced</td></tr>
 *     <tr><td>any</td><td>{@code true}</td><td>{@code addPreTickHandler}</td><td>every tick, before the recipe
 *         progresses</td></tr>
 * </table>
 */
@ZenRegister
@ZenExpansion("mods.modularmachinery.RecipePrimer")
public final class CtRecipePrimer {

    private static final String IO_INPUT = "input";
    private static final String IO_OUTPUT = "output";

    private CtRecipePrimer() {
    }

    /**
     * Adds a channel write to a recipe.
     *
     * @param channelId the firm channel to write, as declared by the pack
     * @param amount the operand, see {@code operation}
     * @param operation {@code 0} adds, {@code 1} multiplies, {@code 2} replaces
     * @param ioType {@code "input"} for the recipe start, {@code "output"} for the recipe finish
     * @param isPerTick {@code true} to write every tick instead of once, which also overrides {@code ioType}
     */
    @ZenMethod
    public static RecipePrimer addDataConfig(RecipePrimer primer, String channelId, int amount, int operation,
                                             String ioType, boolean isPerTick) {
        return registerWrite(primer, channelId, amount, operation, ioType, isPerTick);
    }

    /** See the {@code int} overload; this one takes whole numbers beyond the {@code int} range. */
    @ZenMethod
    public static RecipePrimer addDataConfig(RecipePrimer primer, String channelId, long amount, int operation,
                                             String ioType, boolean isPerTick) {
        return registerWrite(primer, channelId, amount, operation, ioType, isPerTick);
    }

    /** See the {@code int} overload. */
    @ZenMethod
    public static RecipePrimer addDataConfig(RecipePrimer primer, String channelId, float amount, int operation,
                                             String ioType, boolean isPerTick) {
        return registerWrite(primer, channelId, amount, operation, ioType, isPerTick);
    }

    /** See the {@code int} overload. */
    @ZenMethod
    public static RecipePrimer addDataConfig(RecipePrimer primer, String channelId, double amount, int operation,
                                             String ioType, boolean isPerTick) {
        return registerWrite(primer, channelId, amount, operation, ioType, isPerTick);
    }

    private static RecipePrimer registerWrite(RecipePrimer primer, String channelId, double amount, int operation,
                                              String ioType, boolean isPerTick) {
        // everything that can be wrong with the call is reported while the script is loaded, not on every tick
        if (FirmChannelConfig.find(channelId) == null) {
            CraftTweakerAPI.logError("addDataConfig: '" + channelId + "' is not a firm channel declared in "
                    + FirmChannelConfig.FILE_NAME);
            return primer;
        }
        if (!ChannelOps.isValid(operation)) {
            CraftTweakerAPI.logError("addDataConfig: '" + operation + "' is not an operation, use 0 (add), 1 "
                    + "(multiply) or 2 (set)");
            return primer;
        }
        if (isPerTick) {
            primer.addPreTickHandler(event -> write(event, channelId, amount, operation));
            return primer;
        }
        if (IO_OUTPUT.equalsIgnoreCase(ioType)) {
            primer.addFinishHandler(event -> write(event, channelId, amount, operation));
        } else if (IO_INPUT.equalsIgnoreCase(ioType)) {
            primer.addStartHandler(event -> write(event, channelId, amount, operation));
        } else {
            CraftTweakerAPI.logError("addDataConfig: '" + ioType + "' is not an io type, use \"input\" or \"output\"");
        }
        return primer;
    }

    /**
     * Writes the channel of the machine the recipe belongs to.
     */
    private static void write(RecipeEvent event, String channelId, double amount, int operation) {
        FirmChannelDefinition definition = FirmChannelConfig.find(channelId);
        World world = definition == null ? null : DimensionManager.getWorld(0);
        if (world == null) {
            return;
        }
        UUID player = definition.isPublic() ? null : ownerOf(event);
        Channel channel = ChannelRegistry.getOrCreateFirmValue(world, definition, player);
        if (channel == null) {
            return;
        }
        ChannelOps.apply(channel, amount, operation);
        ChannelRegistry.markChanged(world);
    }

    /**
     * @return the owner whose per player channel the recipe writes: the owner of the machine running it
     */
    @Nullable
    private static UUID ownerOf(RecipeEvent event) {
        TileMultiblockMachineController machine = event.getController();
        if (machine != null && machine.getOwner() != null) {
            return machine.getOwner();
        }
        // the event carries no owner, which only happens for a controller nobody owns
        return ChannelScriptContext.currentPlayer();
    }

}
