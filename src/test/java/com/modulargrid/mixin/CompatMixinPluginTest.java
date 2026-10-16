package com.modulargrid.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The compatibility mixins patch other mods' classes and are only valid while those mods are installed, so the gate has
 * to answer "no" for every mixin whose target is absent - which is the case in this test environment.
 */
class CompatMixinPluginTest {

    private static final String TARGET = "some.target.Class";

    @Test
    void novaEngStarlightMixinIsSkippedWithoutNovaEngAndAstralSorcery() {
        CompatMixinPlugin plugin = new CompatMixinPlugin();

        assertFalse(plugin.shouldApplyMixin(TARGET, "com.modulargrid.mixin.NovaEngStarlightAdapterMixin"));
    }

    @Test
    void unknownMixinsAreNeverEnabledByTheCompatibilityPlugin() {
        CompatMixinPlugin plugin = new CompatMixinPlugin();

        assertFalse(plugin.shouldApplyMixin(TARGET, "com.modulargrid.mixin.SomeOtherMixin"));
    }
}
