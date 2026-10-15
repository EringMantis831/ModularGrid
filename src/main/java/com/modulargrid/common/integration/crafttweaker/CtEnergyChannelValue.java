package com.modulargrid.common.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.OperatorType;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;
import stanhebben.zenscript.annotations.ZenOperator;

/** Energy channel script value; {@code amount} remains an exact long, expressions evaluate as double. */
@ZenRegister
@ZenClass("mods.modulargrid.EnergyChannelValue")
public final class CtEnergyChannelValue {

    private final String channelId;

    CtEnergyChannelValue(String channelId) {
        this.channelId = channelId;
    }

    @ZenGetter("id")
    public String getId() {
        return channelId;
    }

    @ZenGetter("amount")
    public long getAmount() {
        return CtChannels.readEnergy(channelId);
    }

    CtChannelExpression toExpression() {
        return CtChannelExpression.channel(channelId);
    }

    @ZenOperator(OperatorType.ADD)
    public CtChannelExpression add(Object other) { return toExpression().add(other); }
    @ZenOperator(OperatorType.SUB)
    public CtChannelExpression sub(Object other) { return toExpression().sub(other); }
    @ZenOperator(OperatorType.MUL)
    public CtChannelExpression mul(Object other) { return toExpression().mul(other); }
    @ZenOperator(OperatorType.DIV)
    public CtChannelExpression div(Object other) { return toExpression().div(other); }
    @ZenOperator(OperatorType.MOD)
    public CtChannelExpression mod(Object other) { return toExpression().mod(other); }
    @ZenOperator(OperatorType.NEG)
    public CtChannelExpression neg() { return toExpression().neg(); }

    @ZenMethod
    public CtChannelExpression inverse() { return toExpression().inverse(); }
    @ZenMethod
    public CtChannelExpression reciprocal() { return inverse(); }
    @ZenMethod
    public CtChannelExpression pow(int exponent) { return toExpression().pow(exponent); }
    @ZenMethod
    public CtChannelExpression pow(long exponent) { return toExpression().pow(exponent); }
    @ZenMethod
    public CtChannelExpression pow(float exponent) { return toExpression().pow(exponent); }
    @ZenMethod
    public CtChannelExpression pow(double exponent) { return toExpression().pow(exponent); }
    @ZenMethod
    public CtChannelExpression abs() { return toExpression().abs(); }
    @ZenMethod
    public CtChannelExpression max(int value) { return toExpression().max(value); }
    @ZenMethod
    public CtChannelExpression max(long value) { return toExpression().max(value); }
    @ZenMethod
    public CtChannelExpression max(float value) { return toExpression().max(value); }
    @ZenMethod
    public CtChannelExpression max(double value) { return toExpression().max(value); }
    @ZenMethod
    public CtChannelExpression min(int value) { return toExpression().min(value); }
    @ZenMethod
    public CtChannelExpression min(long value) { return toExpression().min(value); }
    @ZenMethod
    public CtChannelExpression min(float value) { return toExpression().min(value); }
    @ZenMethod
    public CtChannelExpression min(double value) { return toExpression().min(value); }

    @Override
    public String toString() {
        return "EnergyChannelValue{" + channelId + "=" + getAmount() + '}';
    }
}
