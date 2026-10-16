package com.modulargrid.common.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Astral Sorcery is not on the test class path, so this covers the path a game without Astral Sorcery takes: the
 * lookup has to report "not compiled" instead of throwing, and it has to survive being asked more than once.
 */
class AstralRecipesTest {

    @Test
    void reportsNotCompiledWhenAstralSorceryIsAbsent() {
        assertFalse(AstralRecipes.ensureCompiled());
        assertFalse(AstralRecipes.ensureCompiled());
    }
}
