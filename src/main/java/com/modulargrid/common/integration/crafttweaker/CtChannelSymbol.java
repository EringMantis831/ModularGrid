package com.modulargrid.common.integration.crafttweaker;

import stanhebben.zenscript.compiler.IEnvironmentGlobal;
import stanhebben.zenscript.compiler.IEnvironmentMethod;
import stanhebben.zenscript.expression.Expression;
import stanhebben.zenscript.expression.ExpressionCallStatic;
import stanhebben.zenscript.expression.ExpressionString;
import stanhebben.zenscript.expression.partial.IPartialExpression;
import stanhebben.zenscript.symbols.IZenSymbol;
import stanhebben.zenscript.type.ZenType;
import stanhebben.zenscript.type.natives.IJavaMethod;
import stanhebben.zenscript.type.natives.JavaMethod;
import stanhebben.zenscript.util.ZenPosition;

/**
 * Makes a bare {@code <channelId>} usable inside a script, so a channel reads like a property:
 * {@code Temp.amount}.
 *
 * <p>ZenScript resolves an unknown identifier through the global symbol table, and a symbol only has to answer with a
 * partial expression. This one answers with a call to {@link CtChannels#valueOf(String)} carrying the channel id, so
 * the compiled script asks the mod for the channel at the moment the expression runs instead of baking a value in at
 * compile time.</p>
 *
 * <p>The name is registered once per channel (the global table rejects duplicates with
 * {@code IllegalArgumentException}), which is why the symbol looks the channel up lazily: adding a channel to the
 * configuration after a script reload only needs a new symbol, not a new symbol for every existing one.</p>
 *
 * <p>An energy channel compiles into a call to {@code CtChannels.valueOfEnergy}, a data channel into
 * {@code CtChannels.valueOf}: the two return different classes because {@code amount} is a {@code long} for stored
 * energy and a {@code float} for everything else. Which one applies is decided while the script is compiled, from the
 * channel's declaration in the pack configuration.</p>
 */
final class CtChannelSymbol implements IZenSymbol {

    private final String channelId;

    CtChannelSymbol(String channelId) {
        this.channelId = channelId;
    }

    String getChannelId() {
        return channelId;
    }

    @Override
    public IPartialExpression instance(ZenPosition position) {
        return new ChannelPartial(position, channelId);
    }

    /** Partial expression that turns into {@code CtChannels.valueOf("<id>")} once the compiler asks for a value. */
    private static final class ChannelPartial implements IPartialExpression {

        private final ZenPosition position;
        private final String channelId;

        private ChannelPartial(ZenPosition position, String channelId) {
            this.position = position;
            this.channelId = channelId;
        }

        /** @return the static call this symbol stands for, picked by the type the channel is declared with */
        private Expression call(IEnvironmentGlobal environment) {
            boolean energy = CtChannels.isEnergyChannel(channelId);
            IJavaMethod method = JavaMethod.get(environment, CtChannels.class,
                    energy ? "valueOfEnergy" : "valueOf", String.class);
            return new ExpressionCallStatic(position, environment, method,
                    new ExpressionString(position, channelId));
        }

        @Override
        public Expression eval(IEnvironmentGlobal environment) {
            return call(environment);
        }

        @Override
        public Expression assign(ZenPosition position, IEnvironmentGlobal environment, Expression value) {
            return call(environment).assign(position, environment, value);
        }

        @Override
        public IPartialExpression getMember(ZenPosition position, IEnvironmentGlobal environment, String name) {
            return call(environment).getMember(position, environment, name);
        }

        @Override
        public Expression call(ZenPosition position, IEnvironmentMethod environment, Expression... arguments) {
            throw new UnsupportedOperationException("A channel value cannot be called");
        }

        @Override
        public ZenType[] predictCallTypes(int numArguments) {
            return new ZenType[numArguments];
        }

        @Override
        public IZenSymbol toSymbol() {
            return new CtChannelSymbol(channelId);
        }

        @Override
        public ZenType getType() {
            return CtChannels.isEnergyChannel(channelId)
                    ? CtChannels.energyChannelValueType() : CtChannels.channelValueType();
        }

        @Override
        public ZenType toType(IEnvironmentGlobal environment) {
            return getType();
        }
    }
}
