package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.common.channel.FirmChannelDefinition;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The recipe modifiers firm channels provide, as declared by scripts through {@code ChannelModifierBuilder}.
 *
 * <p>A declaration is not bound to a machine: it says "while this channel is enabled for a machine, that machine gets
 * these modifiers". Static declarations are kept on the controller by the access plug; sampled declarations are copied
 * into a recipe's context once when its start event fires.</p>
 *
 * <p>Declaring the same modifier twice changes nothing. CraftTweaker compiles every script again on a reload, and the
 * builders run again then, so a declaration has to be idempotent instead of being collected in a list that is cleared
 * somewhere. Two declarations that only differ in a value are two modifiers, which is what makes editing a declaration
 * take effect after a restart.</p>
 */
public final class ChannelModifierRegistry {

    private static final Object LOCK = new Object();

    /** Channel id to its static modifiers, in declaration order, keyed by the declaration itself. */
    private static final Map<String, Map<String, Declaration>> MODIFIERS = new LinkedHashMap<>();

    /** Sampled modifiers are copied into a recipe context once at RecipeStartEvent. */
    private static final Map<String, Map<String, SampledDeclaration>> SAMPLED_MODIFIERS = new LinkedHashMap<>();

    /** Bumped whenever a declaration was added, so an access plug can notice without comparing modifier lists. */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private ChannelModifierRegistry() {
    }

    /**
     * Declares one modifier for a channel.
     *
     * @param channelId the firm channel whose enable state decides whether the modifier applies
     * @param machineId the machine the declaration is limited to, {@code null} for every machine the channel reaches
     */
    public static void add(String channelId, @Nullable String machineId, RecipeModifier modifier) {
        if (channelId == null || modifier == null) {
            return;
        }
        Declaration declaration = new Declaration(machineId, modifier);
        synchronized (LOCK) {
            Map<String, Declaration> declared = MODIFIERS.computeIfAbsent(channelId,
                    ignored -> new LinkedHashMap<>());
            if (declared.putIfAbsent(declaration.key(), declaration) == null) {
                GENERATION.incrementAndGet();
            }
        }
    }

    /**
     * @param channelId the firm channel
     * @param machineName registry name of the machine that would receive the modifiers, {@code null} without a machine
     * @return the modifiers that channel declares for that machine, empty when it declares none for it
     */
    public static List<RecipeModifier> getFor(String channelId, @Nullable String machineName) {
        if (channelId == null) {
            return Collections.emptyList();
        }
        synchronized (LOCK) {
            Map<String, Declaration> declared = MODIFIERS.get(channelId);
            if (declared == null || declared.isEmpty()) {
                return Collections.emptyList();
            }
            List<RecipeModifier> applicable = new ArrayList<>(declared.size());
            for (Declaration declaration : declared.values()) {
                if (declaresFor(declaration.machineId(), machineName)) {
                    applicable.add(declaration.modifier());
                }
            }
            return applicable;
        }
    }

    /** Register a modifier whose operand is computed once when a recipe starts. */
    public static void addSampled(String channelId, @Nullable String machineId, RecipeModifier template,
                                  CtChannelExpression expression) {
        if (channelId == null || template == null || expression == null) {
            return;
        }
        SampledDeclaration declaration = new SampledDeclaration(channelId, machineId, template, null, expression);
        synchronized (LOCK) {
            Map<String, SampledDeclaration> declared = SAMPLED_MODIFIERS.computeIfAbsent(channelId,
                    ignored -> new LinkedHashMap<>());
            if (declared.putIfAbsent(declaration.key(), declaration) == null) {
                GENERATION.incrementAndGet();
            }
        }
    }

    /** Compatibility registration for callback-based declarations. */
    public static void addSampled(String channelId, @Nullable String machineId, RecipeModifier template,
                                  ChannelAmountFunction amountFunction) {
        if (channelId == null || template == null || amountFunction == null) {
            return;
        }
        SampledDeclaration declaration = new SampledDeclaration(channelId, machineId, template, amountFunction, null);
        synchronized (LOCK) {
            Map<String, SampledDeclaration> declared = SAMPLED_MODIFIERS.computeIfAbsent(channelId,
                    ignored -> new LinkedHashMap<>());
            if (declared.putIfAbsent(declaration.key(), declaration) == null) {
                GENERATION.incrementAndGet();
            }
        }
    }

    /** @return sampled declarations matching this machine, in declaration order */
    public static List<SampledDeclaration> getSampledFor(@Nullable String machineName) {
        synchronized (LOCK) {
            List<SampledDeclaration> applicable = new ArrayList<>();
            for (Map<String, SampledDeclaration> declarations : SAMPLED_MODIFIERS.values()) {
                for (SampledDeclaration declaration : declarations.values()) {
                    if (declaresFor(declaration.machineId(), machineName)) {
                        applicable.add(declaration);
                    }
                }
            }
            return applicable;
        }
    }

    /**
     * @param declaredMachine the machine a declaration is limited to, {@code null} for every machine
     * @param machineName the machine asking, {@code null} while there is no machine
     * @return whether the declaration reaches that machine
     */
    public static boolean declaresFor(@Nullable String declaredMachine, @Nullable String machineName) {
        return declaredMachine == null || declaredMachine.equals(machineName);
    }

    /** @return the ids of the channels that declare at least one modifier */
    public static Set<String> getDeclaringChannels() {
        synchronized (LOCK) {
            return new LinkedHashSet<>(MODIFIERS.keySet());
        }
    }

    /** @return whether nothing is declared at all, the common case and the cheap way out of a per tick check */
    public static boolean isEmpty() {
        synchronized (LOCK) {
            return MODIFIERS.isEmpty();
        }
    }

    public static boolean hasSampledModifiers() {
        synchronized (LOCK) {
            return !SAMPLED_MODIFIERS.isEmpty();
        }
    }

    public static int getGeneration() {
        return GENERATION.get();
    }

    /**
     * Whether a channel provides its modifiers to a machine right now: it has to be declared, switched on for that
     * machine, and declared for it. This is the rule the access plug follows, kept here so it can be read in one place.
     *
     * @param definition the channel's declaration, {@code null} when the configuration does not declare it any more
     * @param machineName registry name of the machine, {@code null} while the plug is not part of a formed one
     * @param channelEnabled whether the machine's plug has the channel switched on
     */
    public static boolean appliesTo(@Nullable FirmChannelDefinition definition, @Nullable String machineName,
                                    boolean channelEnabled) {
        return definition != null && channelEnabled && definition.allowsMachine(machineName);
    }

    /** One declared static modifier and what it is limited to. */
    private record Declaration(@Nullable String machineId, RecipeModifier modifier) {

        private String key() {
            return machineId + "#" + modifier.serialize();
        }

    }

    /** One modifier template and its value source to snapshot into a recipe at its start. */
    public record SampledDeclaration(String channelId, @Nullable String machineId, RecipeModifier template,
                                    @Nullable ChannelAmountFunction amountFunction,
                                    @Nullable CtChannelExpression expression) {

        public SampledDeclaration(String channelId, @Nullable String machineId, RecipeModifier template,
                                  ChannelAmountFunction amountFunction) {
            this(channelId, machineId, template, amountFunction, null);
        }

        private String key() {
            String source = expression != null ? expression.serialize()
                    : amountFunction == null ? "<missing>" : amountFunction.getClass().getName();
            return machineId + "#" + template.serialize() + "#" + source;
        }

        public boolean hasExpression() {
            return expression != null;
        }

        public RecipeModifier sample(double channelAmount) {
            if (amountFunction == null) {
                throw new IllegalStateException("This sampled modifier has no amount callback");
            }
            return createModifier(amountFunction.apply(channelAmount));
        }

        public RecipeModifier sample(CtChannelExpression.ChannelValueResolver resolver) {
            if (expression == null) {
                throw new IllegalStateException("This sampled modifier has no channel expression");
            }
            return createModifier(expression.evaluate(resolver));
        }

        private RecipeModifier createModifier(double sampledValue) {
            if (!Double.isFinite(sampledValue)) {
                throw new IllegalArgumentException("Sampled channel modifier is not finite: " + sampledValue);
            }
            float value = (float) sampledValue;
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Sampled channel modifier exceeds float range: " + sampledValue);
            }
            return new RecipeModifier(template.getTarget(), template.getIOTarget(), value,
                    template.getOperation(), template.affectsChance());
        }

    }

}
