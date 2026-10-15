package com.modulargrid.common.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;

/** Computes the modifier value from a firm channel's amount sampled when a recipe starts. */
@FunctionalInterface
@ZenRegister
@ZenClass("mods.modulargrid.ChannelAmountFunction")
public interface ChannelAmountFunction {

    /**
     * @param amount current channel amount at recipe start (energy channels are converted from {@code long} to double)
     * @return the modifier operand to snapshot into this recipe's context
     */
    double apply(double amount);

}
