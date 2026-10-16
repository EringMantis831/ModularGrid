package com.modulargrid.common.machine;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.block.BlockAccessPlug;
import github.kasuminova.mmce.common.util.DynamicPattern;
import hellfirepvp.modularmachinery.common.block.BlockEnergyHatch;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;
import net.minecraft.block.state.IBlockState;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

/**
 * Lets the access plug stand in for a Modular Machinery energy hatch.
 */
public final class EnergyHatchPatternCompat {

    private EnergyHatchPatternCompat() {
    }

    /** Makes every energy hatch position of the given machines accept the access plug. */
    public static void apply(Collection<DynamicMachine> machines) {
        IBlockStateDescriptor plug = IBlockStateDescriptor.of(BlockAccessPlug.INSTANCE);
        int positions = 0;
        for (DynamicMachine machine : machines) {
            positions += addToPositions(machine.getPattern(), plug);
            for (DynamicPattern dynamicPattern : machine.getDynamicPatterns().values()) {
                positions += addToPositions(dynamicPattern.getPattern(), plug);
                positions += addToPositions(dynamicPattern.getPatternEnd(), plug);
            }
        }
        if (positions > 0) {
            ModularGrid.LOGGER.info("Access plug now accepted at {} energy hatch positions of {} machines",
                    positions, machines.size());
        }
    }

    /** @return the amount of positions the plug was added to */
    private static int addToPositions(@Nullable TaggedPositionBlockArray pattern, IBlockStateDescriptor plug) {
        if (pattern == null) {
            return 0;
        }
        int added = 0;
        for (BlockArray.BlockInformation information : pattern.getPattern().values()) {
            if (acceptsEnergyHatch(information) && !information.getMatchingStates().contains(plug)) {
                information.addMatchingStates(Collections.singletonList(plug));
                added++;
            }
        }
        return added;
    }

    /** @return {@code true} if the position allows any Modular Machinery energy hatch */
    private static boolean acceptsEnergyHatch(BlockArray.BlockInformation information) {
        for (IBlockStateDescriptor descriptor : information.getMatchingStates()) {
            for (IBlockState state : descriptor.getApplicable()) {
                if (state.getBlock() instanceof BlockEnergyHatch) {
                    return true;
                }
            }
        }
        return false;
    }
}
