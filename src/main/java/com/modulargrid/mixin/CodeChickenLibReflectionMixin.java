package com.modulargrid.mixin;

import codechicken.lib.reflect.ReflectionManager;
import com.modulargrid.common.util.FieldModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Makes CodeChickenLib's reflection helper work on modern Java.
 *
 * <p>Two separate problems, both fatal while loading anything that uses CodeChickenLib (Thermal
 * Expansion, for example):</p>
 *
 * <ol>
 *     <li>{@code ReflectionManager.removeFinal} clears the {@code final} modifier by writing the
 *     {@code Field.modifiers} field. Java 12+ filtered that field and Java 17 removed it, so the
 *     lookup inside {@code removeFinal} calls {@code getField}, which calls
 *     {@code getField_Unsafe}, which calls {@code removeFinal} again - forever, until the stack
 *     overflows. {@link #modulargrid$breakRecursion} cuts that loop: the nested call becomes a
 *     no-op, leaving the outer call holding a valid reference to the field it wanted.</li>
 *     <li>Clearing the bit is not enough on its own. Java 17+ also refuses to build a reflective
 *     accessor for a {@code static final} field - even for a plain read, because building the override
 *     accessor creates a setter as well and that setter is rejected. CodeChickenLib reads
 *     {@code ModelLoader$VanillaLoader.INSTANCE} that way while installing its blockstate loader, and
 *     the resulting {@code InternalError} aborts model loading. {@link #modulargrid$readViaUnsafe} and
 *     {@link #modulargrid$writeViaUnsafe} route both reflective reads and writes through
 *     {@code Unsafe}, which has no such restriction.</li>
 * </ol>
 *
 * <p>Everything else in the class - name lookups, ordinary reads - is left alone.</p>
 */
@Mixin(value = ReflectionManager.class, remap = false)
public class CodeChickenLibReflectionMixin {

    /**
     * Guards against the re-entrant call, see the class comment. Per thread, so a concurrent caller on a
     * JVM that does <i>not</i> need the guard (Java 8, where the recursion cannot happen) never has its
     * own legitimate call cancelled by somebody else's.
     */
    @Unique
    private static final ThreadLocal<Boolean> modulargrid$insideRemoveFinal = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "removeFinal", at = @At("HEAD"), cancellable = true)
    private static void modulargrid$breakRecursion(Field field, CallbackInfo callbackInfo) {
        if (modulargrid$insideRemoveFinal.get()) {
            callbackInfo.cancel();
            return;
        }
        modulargrid$insideRemoveFinal.set(Boolean.TRUE);
    }

    @Inject(method = "removeFinal", at = @At("RETURN"))
    private static void modulargrid$leaveRemoveFinal(Field field, CallbackInfo callbackInfo) {
        modulargrid$insideRemoveFinal.set(Boolean.FALSE);
    }

    /**
     * Replaces {@code modifiersField.set(field, field.getModifiers() & ~Modifier.FINAL)} with the same
     * write done through {@code Unsafe}.
     */
    @Redirect(method = "removeFinal",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/reflect/Field;set(Ljava/lang/Object;Ljava/lang/Object;)V"))
    private static void modulargrid$writeViaUnsafe(Field modifiersAccessor, Object target, Object value) {
        // the only caller passes a Field as the target and an Integer as the value
        if (target instanceof Field field && value instanceof Integer modifiers) {
            FieldModifiers.setFieldModifiers(field, modifiers);
            return;
        }
        try {
            modifiersAccessor.set(target, value);
        } catch (IllegalAccessException refused) {
            throw new RuntimeException("Could not set " + target, refused);
        }
    }

    /**
     * Reads {@code ReflectionManager.getField(mapping, instance, type)} through {@code Unsafe}, because
     * {@code Field.get} cannot touch a {@code static final} field on Java 17+.
     */
    @Redirect(method = "getField(Lcodechicken/lib/reflect/ObfMapping;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/reflect/Field;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object modulargrid$readViaUnsafe(Field field, Object instance) {
        return FieldModifiers.get(field, instance);
    }

    /**
     * Mirrors {@link #modulargrid$readViaUnsafe} for {@code ReflectionManager.setField}, so replacing a
     * {@code static final} field keeps working as well.
     */
    @Redirect(method = "setField",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/reflect/Field;set(Ljava/lang/Object;Ljava/lang/Object;)V"))
    private static void modulargrid$writeFieldViaUnsafe(Field field, Object instance, Object value) {
        FieldModifiers.set(field, instance, value);
    }
}
