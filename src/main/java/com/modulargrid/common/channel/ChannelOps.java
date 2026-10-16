package com.modulargrid.common.channel;

/**
 * The three ways a script can change a channel, used by the {@code addDataConfig} recipe method.
 */
public final class ChannelOps {

    /** Adds the amount, which may be negative. */
    public static final int OPERATION_ADD = 0;

    /** Multiplies the current value with the amount. */
    public static final int OPERATION_MULTIPLY = 1;

    /** Replaces the value with the amount. */
    public static final int OPERATION_SET = 2;

    private ChannelOps() {
    }

    /** @return whether the operation number is one of the three defined ones */
    public static boolean isValid(int operation) {
        return operation == OPERATION_ADD || operation == OPERATION_MULTIPLY || operation == OPERATION_SET;
    }

    /** @return the operation as it is written in a script, for log messages */
    public static String nameOf(int operation) {
        return switch (operation) {
            case OPERATION_ADD -> "add";
            case OPERATION_MULTIPLY -> "multiply";
            case OPERATION_SET -> "set";
            default -> "unknown(" + operation + ')';
        };
    }

    /**
     * Applies one operation to a channel, matching the payload type: an energy channel stays a {@code long} and never
     * goes negative or overflows, a data channel stays a {@code float}.
     *
     * @param amount the operand, converted to the channel's type here so a script can pass whole numbers
     */
    public static void apply(Channel channel, double amount, int operation) {
        if (channel.isEnergy()) {
            switch (operation) {
                case OPERATION_MULTIPLY -> channel.multiplyEnergyValue(amount);
                case OPERATION_SET -> channel.setEnergyValue(clampToLong(amount));
                default -> {
                    long delta = clampToLong(amount);
                    if (delta >= 0L) {
                        channel.giveEnergy(delta);
                    } else {
                        channel.takeEnergy(-delta);
                    }
                }
            }
            return;
        }
        switch (operation) {
            case OPERATION_MULTIPLY -> channel.multiplyFloatValue((float) amount);
            case OPERATION_SET -> channel.setFloatValue((float) amount);
            default -> channel.addFloatValue((float) amount);
        }
    }

    /** @return the value as a {@code long}, saturating like a Java narrowing conversion does */
    private static long clampToLong(double amount) {
        return amount >= (double) Long.MAX_VALUE ? Long.MAX_VALUE
                : amount <= (double) Long.MIN_VALUE ? Long.MIN_VALUE : (long) amount;
    }

}
