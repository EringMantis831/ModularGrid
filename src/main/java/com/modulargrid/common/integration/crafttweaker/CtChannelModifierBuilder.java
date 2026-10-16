package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.channel.FirmChannelDefinition;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.RecipeModifierBuilder;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets a pack say what a channel gives the machines using it.
 *
 * <pre>
 * ChannelModifierBuilder.newBuilder()
 *     .addNewModifier("temp", "modularmachinery:duration", "input", 0.5, 1, false)
 *     .addNewModifier("temp", "modularmachinery:energy", "input", 2.0, 1, false)
 *     .build();
 * </pre>
 */
@ZenRegister
@ZenClass("mods.modulargrid.ChannelModifierBuilder")
public final class CtChannelModifierBuilder {

    private final List<Declaration> declarations = new ArrayList<>();
    private final List<SampledDeclaration> sampledDeclarations = new ArrayList<>();

    /** Machine every declaration of this builder is limited to, {@code null} for every machine the channel reaches. */
    private final String machineId;

    private CtChannelModifierBuilder(String machineId) {
        this.machineId = machineId;
    }

    /** Starts a declaration that applies to every machine the channel is declared for. */
    @ZenMethod
    public static CtChannelModifierBuilder newBuilder() {
        return new CtChannelModifierBuilder(null);
    }

    /**
     * Starts a declaration for one machine only, independent of the channel's {@code masterTo} list.
     *
     * @param machineId registry name of the machine, e.g. {@code alloy_furnace} or {@code modularmachinery:contributor}
     */
    @ZenMethod
    public static CtChannelModifierBuilder newBuilder(String machineId) {
        return new CtChannelModifierBuilder(FirmChannelDefinition.normalizeMachineName(machineId));
    }

    /**
     * Declares one modifier a channel provides.
     *
     * @param channelId the firm channel, which decides which machines get the modifier by being enabled for them
     * @param target the requirement to change, as Modular Machinery names it, e.g. {@code modularmachinery:duration}
     * @param ioType {@code "input"} or {@code "output"}
     * @param modifier the operand
     * @param operation {@code 0} adds, {@code 1} multiplies
     * @param chance whether the modifier applies to chance values instead of amounts
     */
    @ZenMethod
    public CtChannelModifierBuilder addNewModifier(String channelId, String target, String ioType, int modifier,
                                                  int operation, boolean chance) {
        return addNewModifier(channelId, target, ioType, (double) modifier, operation, chance);
    }

    /** See the {@code int} overload; this one takes whole numbers beyond the {@code int} range. */
    @ZenMethod
    public CtChannelModifierBuilder addNewModifier(String channelId, String target, String ioType, long modifier,
                                                  int operation, boolean chance) {
        return addNewModifier(channelId, target, ioType, (double) modifier, operation, chance);
    }

    /** See the {@code int} overload. */
    @ZenMethod
    public CtChannelModifierBuilder addNewModifier(String channelId, String target, String ioType, float modifier,
                                                  int operation, boolean chance) {
        return addNewModifier(channelId, target, ioType, (double) modifier, operation, chance);
    }

    /** See the {@code int} overload. */
    @ZenMethod
    public CtChannelModifierBuilder addNewModifier(String channelId, String target, String ioType, double modifier,
                                                  int operation, boolean chance) {
        if (!isDeclared(channelId)) {
            return this;
        }
        // Modular Machinery's own builder parses the identifiers and complains about a bad one itself
        declarations.add(new Declaration(channelId,
                RecipeModifierBuilder.create(target, ioType, (float) modifier, operation, chance).build()));
        return this;
    }

    @ZenMethod
    public CtChannelModifierBuilder addSampledModifier(String channelId, String target, String ioType, int value,
                                                       int operation, boolean chance) {
        return addSampledModifier(channelId, target, ioType, CtChannelExpression.constant(value), operation, chance);
    }

    @ZenMethod
    public CtChannelModifierBuilder addSampledModifier(String channelId, String target, String ioType, long value,
                                                       int operation, boolean chance) {
        return addSampledModifier(channelId, target, ioType, CtChannelExpression.constant(value), operation, chance);
    }

    @ZenMethod
    public CtChannelModifierBuilder addSampledModifier(String channelId, String target, String ioType, float value,
                                                       int operation, boolean chance) {
        return addSampledModifier(channelId, target, ioType, CtChannelExpression.constant(value), operation, chance);
    }

    @ZenMethod
    public CtChannelModifierBuilder addSampledModifier(String channelId, String target, String ioType, double value,
                                                       int operation, boolean chance) {
        return addSampledModifier(channelId, target, ioType, CtChannelExpression.constant(value), operation, chance);
    }

    /**
     * Declares a modifier whose value is calculated once from a lazy channel expression when a recipe starts.
     *
     * @param channelId the firm channel to sample
     * @param target the requirement to modify
     * @param ioType {@code "input"} or {@code "output"}
     * @param expression a lazy expression built from channel values and arithmetic, or a numeric constant
     * @param operation {@code 0} adds, {@code 1} multiplies
     * @param chance whether the modifier applies to chance values
     */
    @ZenMethod
    public CtChannelModifierBuilder addSampledModifier(String channelId, String target, String ioType,
                                                       CtChannelExpression expression, int operation,
                                                       boolean chance) {
        if (!isDeclared(channelId) || expression == null) {
            return this;
        }
        RecipeModifier template = RecipeModifierBuilder.create(target, ioType, 1.0F, operation, chance).build();
        sampledDeclarations.add(new SampledDeclaration(channelId, template, null, expression));
        return this;
    }

    /** Compatibility overload for scripts that already use a callback. Prefer the lazy channel-expression overload. */
    @ZenMethod
    public CtChannelModifierBuilder addSampledModifier(String channelId, String target, String ioType,
                                                       ChannelAmountFunction amountFunction, int operation,
                                                       boolean chance) {
        if (!isDeclared(channelId) || amountFunction == null) {
            return this;
        }
        RecipeModifier template = RecipeModifierBuilder.create(target, ioType, 1.0F, operation, chance).build();
        sampledDeclarations.add(new SampledDeclaration(channelId, template, amountFunction, null));
        return this;
    }

    /**
     * Declares a modifier that was built with Modular Machinery's own {@code RecipeModifierBuilder}.
     *
     * @param channelId the firm channel, see the other overload
     */
    @ZenMethod
    public CtChannelModifierBuilder addNewModifier(String channelId, RecipeModifier modifier) {
        if (!isDeclared(channelId) || modifier == null) {
            return this;
        }
        declarations.add(new Declaration(channelId, modifier));
        return this;
    }

    /**
     * Registers everything that was declared. Called once at the end of the chain.
     */
    @ZenMethod
    public void build() {
        if (declarations.isEmpty() && sampledDeclarations.isEmpty()) {
            return;
        }
        for (Declaration declaration : declarations) {
            ChannelModifierRegistry.add(declaration.channelId, machineId, declaration.modifier);
        }
        for (SampledDeclaration declaration : sampledDeclarations) {
            if (declaration.expression != null) {
                ChannelModifierRegistry.addSampled(declaration.channelId, machineId, declaration.template,
                        declaration.expression);
            } else {
                ChannelModifierRegistry.addSampled(declaration.channelId, machineId, declaration.template,
                        declaration.amountFunction);
            }
        }
        ModularGrid.LOGGER.info("Registered {} channel modifier declaration(s)",
                declarations.size() + sampledDeclarations.size());
        declarations.clear();
        sampledDeclarations.clear();
    }

    /**
     * @return whether the id is a firm channel of the pack, reported once while the script is loaded instead of
     *         silently doing nothing
     */
    private static boolean isDeclared(String channelId) {
        if (FirmChannelConfig.find(channelId) != null) {
            return true;
        }
        CraftTweakerAPI.logError("ChannelModifierBuilder: '" + channelId + "' is not a firm channel declared in "
                + FirmChannelConfig.FILE_NAME);
        return false;
    }

    /** One declared modifier and the channel it belongs to. */
    private static final class Declaration {

        private final String channelId;
        private final RecipeModifier modifier;

        private Declaration(String channelId, RecipeModifier modifier) {
            this.channelId = channelId;
            this.modifier = modifier;
        }

    }

    /** One sampled modifier template and exactly one value source. */
    private static final class SampledDeclaration {

        private final String channelId;
        private final RecipeModifier template;
        private final ChannelAmountFunction amountFunction;
        private final CtChannelExpression expression;

        private SampledDeclaration(String channelId, RecipeModifier template,
                                   ChannelAmountFunction amountFunction, CtChannelExpression expression) {
            this.channelId = channelId;
            this.template = template;
            this.amountFunction = amountFunction;
            this.expression = expression;
        }

    }

}
