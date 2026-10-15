package com.modulargrid.common.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.OperatorType;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;
import stanhebben.zenscript.annotations.ZenOperator;

import javax.annotation.Nullable;

/** A lazy arithmetic tree evaluated against the machine's channel values when its recipe starts. */
@ZenRegister
@ZenClass("mods.modulargrid.ChannelExpression")
public final class CtChannelExpression {

    @FunctionalInterface
    interface ChannelValueResolver {
        double read(String channelId);
    }

    private interface Node {
        double evaluate(ChannelValueResolver resolver);
        String key();
    }

    private final Node node;

    private CtChannelExpression(Node node) {
        this.node = node;
    }

    static CtChannelExpression channel(String channelId) {
        return new CtChannelExpression(new Node() {
            @Override
            public double evaluate(ChannelValueResolver resolver) {
                return resolver.read(channelId);
            }

            @Override
            public String key() {
                return "channel(" + channelId + ')';
            }
        });
    }

    static CtChannelExpression constant(double value) {
        return new CtChannelExpression(new Node() {
            @Override
            public double evaluate(ChannelValueResolver resolver) {
                return value;
            }

            @Override
            public String key() {
                return "constant(" + Double.toString(value) + ')';
            }
        });
    }

    double evaluate(ChannelValueResolver resolver) {
        return node.evaluate(resolver);
    }

    String serialize() {
        return node.key();
    }

    private static double apply(String operation, double left, double right) {
        return switch (operation) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> left / right;
            case "%" -> left % right;
            default -> throw new IllegalArgumentException("Unknown channel expression operation " + operation);
        };
    }

    private CtChannelExpression binary(String operation, CtChannelExpression other) {
        CtChannelExpression left = this;
        return new CtChannelExpression(new Node() {
            @Override
            public double evaluate(ChannelValueResolver resolver) {
                return apply(operation, left.evaluate(resolver), other.evaluate(resolver));
            }

            @Override
            public String key() {
                return '(' + left.serialize() + operation + other.serialize() + ')';
            }
        });
    }

    private CtChannelExpression unaryWithValue(String operation, double value) {
        CtChannelExpression source = this;
        return new CtChannelExpression(new Node() {
            @Override
            public double evaluate(ChannelValueResolver resolver) {
                double current = source.evaluate(resolver);
                return switch (operation) {
                    case "max" -> Math.max(current, value);
                    case "min" -> Math.min(current, value);
                    default -> throw new IllegalArgumentException("Unknown channel expression operation " + operation);
                };
            }

            @Override
            public String key() {
                return operation + '(' + source.serialize() + ',' + Double.toString(value) + ')';
            }
        });
    }

    private CtChannelExpression unary(String operation) {
        CtChannelExpression source = this;
        return new CtChannelExpression(new Node() {
            @Override
            public double evaluate(ChannelValueResolver resolver) {
                double value = source.evaluate(resolver);
                return switch (operation) {
                    case "neg" -> -value;
                    case "inverse" -> 1.0D / value;
                    case "abs" -> Math.abs(value);
                    default -> throw new IllegalArgumentException("Unknown channel expression operation " + operation);
                };
            }

            @Override
            public String key() {
                return operation + '(' + source.serialize() + ')';
            }
        });
    }

    private CtChannelExpression power(double exponent) {
        CtChannelExpression source = this;
        return new CtChannelExpression(new Node() {
            @Override
            public double evaluate(ChannelValueResolver resolver) {
                return Math.pow(source.evaluate(resolver), exponent);
            }

            @Override
            public String key() {
                return "pow(" + source.serialize() + ',' + Double.toString(exponent) + ')';
            }
        });
    }

    private static CtChannelExpression from(Object value) {
        if (value instanceof CtChannelExpression expression) {
            return expression;
        }
        if (value instanceof CtChannelValue channel) {
            return channel.toExpression();
        }
        if (value instanceof CtEnergyChannelValue channel) {
            return channel.toExpression();
        }
        if (value instanceof Number number) {
            return constant(number.doubleValue());
        }
        throw new IllegalArgumentException("Unsupported channel expression operand: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    @ZenOperator(OperatorType.ADD)
    public CtChannelExpression add(Object other) { return binary("+", from(other)); }
    @ZenOperator(OperatorType.SUB)
    public CtChannelExpression sub(Object other) { return binary("-", from(other)); }
    @ZenOperator(OperatorType.MUL)
    public CtChannelExpression mul(Object other) { return binary("*", from(other)); }
    @ZenOperator(OperatorType.DIV)
    public CtChannelExpression div(Object other) { return binary("/", from(other)); }
    @ZenOperator(OperatorType.MOD)
    public CtChannelExpression mod(Object other) { return binary("%", from(other)); }
    @ZenOperator(OperatorType.NEG)
    public CtChannelExpression neg() { return unary("neg"); }

    @ZenMethod
    public CtChannelExpression inverse() { return unary("inverse"); }
    @ZenMethod
    public CtChannelExpression reciprocal() { return inverse(); }
    @ZenMethod
    public CtChannelExpression pow(int exponent) { return power(exponent); }
    @ZenMethod
    public CtChannelExpression pow(long exponent) { return power(exponent); }
    @ZenMethod
    public CtChannelExpression pow(float exponent) { return power(exponent); }
    @ZenMethod
    public CtChannelExpression pow(double exponent) { return power(exponent); }
    @ZenMethod
    public CtChannelExpression abs() { return unary("abs"); }
    @ZenMethod
    public CtChannelExpression max(int value) { return unaryWithValue("max", value); }
    @ZenMethod
    public CtChannelExpression max(long value) { return unaryWithValue("max", value); }
    @ZenMethod
    public CtChannelExpression max(float value) { return unaryWithValue("max", value); }
    @ZenMethod
    public CtChannelExpression max(double value) { return unaryWithValue("max", value); }
    @ZenMethod
    public CtChannelExpression min(int value) { return unaryWithValue("min", value); }
    @ZenMethod
    public CtChannelExpression min(long value) { return unaryWithValue("min", value); }
    @ZenMethod
    public CtChannelExpression min(float value) { return unaryWithValue("min", value); }
    @ZenMethod
    public CtChannelExpression min(double value) { return unaryWithValue("min", value); }

    @Override
    public String toString() {
        return "ChannelExpression{" + serialize() + '}';
    }
}
