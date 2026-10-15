package com.modulargrid.common.integration.crafttweaker;

import crafttweaker.annotations.BracketHandler;
import crafttweaker.zenscript.IBracketHandler;
import stanhebben.zenscript.compiler.IEnvironmentGlobal;
import stanhebben.zenscript.parser.Token;
import stanhebben.zenscript.symbols.IZenSymbol;

import java.util.List;

/**
 * Resolves the bracket syntax {@code <channel:channelId>} to the same value a bare channel id gives, for scripts that
 * prefer to be explicit about where a value comes from.
 * <p>The priority is the one CraftTweaker assumes for a handler without the annotation, it is only stated here to keep
 * the startup log quiet.</p>
 */
@BracketHandler(priority = 10)
final class CtChannelBracketHandler implements IBracketHandler {

    private static final String PREFIX = "channel";

    @Override
    public IZenSymbol resolve(IEnvironmentGlobal environment, List<Token> tokens) {
        if (tokens.size() < 3) {
            return null;
        }
        if (!PREFIX.equals(tokens.get(0).getValue()) || !":".equals(tokens.get(1).getValue())) {
            return null;
        }
        return new CtChannelSymbol(tokens.get(2).getValue());
    }

    @Override
    public String getRegexMatchingString() {
        return PREFIX + ":.*";
    }

    @Override
    public Class<?> getReturnedClass() {
        return CtChannelValue.class;
    }
}
