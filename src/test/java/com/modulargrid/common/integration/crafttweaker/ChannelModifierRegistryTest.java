package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.common.channel.FirmChannelDefinition;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether a channel provides its modifiers: declared, switched on for the machine, and declared
 * for that machine.
 */
class ChannelModifierRegistryTest {

    private static FirmChannelDefinition definition(boolean energy, boolean publicChannel, String... masterTo) {
        FirmChannelDefinition definition = FirmChannelDefinition.create("temp", "", publicChannel, energy,
                List.of(masterTo), false);
        assertNotNull(definition);
        return definition;
    }

    @Test
    void aDeclaredEnabledChannelApplies() {
        assertTrue(ChannelModifierRegistry.appliesTo(definition(false, true), "modularmachinery:alloy_furnace", true));
    }

    @Test
    void aChannelSwitchedOffForTheMachineDoesNot() {
        assertFalse(ChannelModifierRegistry.appliesTo(definition(false, true), "modularmachinery:alloy_furnace", false));
    }

    @Test
    void aRestrictedChannelOnlyReachesItsMachines() {
        FirmChannelDefinition definition = definition(false, false,
                FirmChannelDefinition.normalizeMachineName("alloy_furnace"));
        assertTrue(ChannelModifierRegistry.appliesTo(definition, "modularmachinery:alloy_furnace", true));
        assertFalse(ChannelModifierRegistry.appliesTo(definition, "modularmachinery:centrifuge", true));
        // without a formed machine there is no name to match, and a restriction means no
        assertFalse(ChannelModifierRegistry.appliesTo(definition, null, true));
    }

    @Test
    void anEnergyChannelIgnoresTheMachineRestriction() {
        FirmChannelDefinition energy = definition(true, false,
                FirmChannelDefinition.normalizeMachineName("alloy_furnace"));
        assertTrue(ChannelModifierRegistry.appliesTo(energy, "modularmachinery:centrifuge", true));
        assertTrue(ChannelModifierRegistry.appliesTo(energy, null, true));
    }

    @Test
    void aChannelTheConfigurationDroppedAppliesNothing() {
        assertFalse(ChannelModifierRegistry.appliesTo(null, "modularmachinery:alloy_furnace", true));
    }

    @Test
    void aDeclarationCanBeLimitedToOneMachine() {
        // without a machine id the declaration reaches every machine the channel is declared for
        assertTrue(ChannelModifierRegistry.declaresFor(null, "modularmachinery:alloy_furnace"));
        assertTrue(ChannelModifierRegistry.declaresFor(null, null));
        // with one it only reaches that machine
        assertTrue(ChannelModifierRegistry.declaresFor("modularmachinery:alloy_furnace",
                "modularmachinery:alloy_furnace"));
        assertFalse(ChannelModifierRegistry.declaresFor("modularmachinery:alloy_furnace",
                "modularmachinery:centrifuge"));
        assertFalse(ChannelModifierRegistry.declaresFor("modularmachinery:alloy_furnace", null));
    }

    @Test
    void sampledModifiersCaptureTheFunctionResultAsARecipeModifier() {
        RecipeModifier template = new RecipeModifier(null, null, 1.0F,
                RecipeModifier.OPERATION_MULTIPLY, false);
        ChannelModifierRegistry.SampledDeclaration declaration = new ChannelModifierRegistry.SampledDeclaration(
                "inf_point", "modularmachinery:infinity_source", template, amount -> amount == 0.0D
                        ? 1.0D : 1.0D / amount);

        assertEquals(0.5F, declaration.sample(2.0D).getModifier());
        assertEquals(1.0F, declaration.sample(0.0D).getModifier());
    }

    @Test
    void channelExpressionKeepsArithmeticLazyAndComposes() {
        CtChannelExpression expression = CtChannelExpression.constant(2.0D).inverse().mul(0.5D).add(0.25D);

        assertEquals(0.5D, expression.evaluate(channelId -> 2.0D), 1.0E-9D);
        assertEquals("((inverse(constant(2.0))*constant(0.5))+constant(0.25))", expression.serialize());
    }

    @Test
    void channelExpressionReadsValuesFromItsEvaluationContext() {
        CtChannelExpression expression = CtChannelExpression.channel("temp").mul(2.0D)
                .add(CtChannelExpression.channel("factory_water"));

        assertEquals(7.0D, expression.evaluate(channelId -> "temp".equals(channelId) ? 3.0D : 1.0D));
        assertEquals(11.0D, expression.evaluate(channelId -> "temp".equals(channelId) ? 5.0D : 1.0D));
    }

    @Test
    void sampledExpressionBecomesARecipeModifier() {
        RecipeModifier template = new RecipeModifier(null, null, 1.0F,
                RecipeModifier.OPERATION_MULTIPLY, false);
        ChannelModifierRegistry.SampledDeclaration declaration = new ChannelModifierRegistry.SampledDeclaration(
                "inf_point", "modularmachinery:infinity_source", template, null,
                CtChannelExpression.constant(0.25D));

        assertTrue(declaration.hasExpression());
        assertEquals(0.25F, declaration.sample(channelId -> 0.0D).getModifier());
    }

    @Test
    void nonFiniteSampleIsRejectedInsteadOfPoisoningModifierAppliers() {
        RecipeModifier template = new RecipeModifier(null, null, 1.0F,
                RecipeModifier.OPERATION_MULTIPLY, false);
        ChannelModifierRegistry.SampledDeclaration declaration = new ChannelModifierRegistry.SampledDeclaration(
                "inf_point", null, template, null, CtChannelExpression.constant(Double.POSITIVE_INFINITY));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> declaration.sample(channelId -> 0.0D));
    }

    @Test
    void nothingIsDeclaredByDefault() {
        assertTrue(ChannelModifierRegistry.isEmpty());
    }

}
