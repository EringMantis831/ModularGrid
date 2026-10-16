package com.modulargrid.common.compat;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterAccessor;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The isolation Modular Machinery's adapter loading gets: a broken adapter is skipped, not fatal.
 *
 * <p>A {@code MachineRecipe} cannot be built outside a bootstrapped game, so the pass-through case checks that the very
 * same list instance comes back instead of comparing recipe contents.</p>
 */
class AdapterFailuresTest {

    private static RecipeAdapterAccessor accessor() {
        return new RecipeAdapterAccessor(
                new ResourceLocation("astralsorcery", "starlight_infuser"),
                new ResourceLocation("modularmachinery", "illum_pool"));
    }

    @Test
    void anAdapterThrowingAnExceptionIsSkipped() {
        List<MachineRecipe> loaded = AdapterFailures.loadSafely(accessor(), () -> {
            throw new NullPointerException("Cannot read the array length because ... compiledRecipes is null");
        });

        assertTrue(loaded.isEmpty());
    }

    @Test
    void anAdapterFailingWithALinkageErrorIsSkippedToo() {
        List<MachineRecipe> loaded = AdapterFailures.loadSafely(accessor(), () -> {
            throw new NoSuchMethodError("void some.optional.Neighbour.register()");
        });

        assertTrue(loaded.isEmpty());
    }

    @Test
    void anAdapterReturningNullIsTreatedAsEmpty() {
        List<MachineRecipe> loaded = AdapterFailures.loadSafely(accessor(), () -> null);

        assertTrue(loaded.isEmpty());
    }

    @Test
    void aWorkingAdapterKeepsItsRecipes() {
        List<MachineRecipe> produced = Collections.singletonList(null);

        assertSame(produced, AdapterFailures.loadSafely(accessor(), () -> produced));
    }
}
