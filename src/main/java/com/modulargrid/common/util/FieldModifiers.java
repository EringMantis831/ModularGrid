package com.modulargrid.common.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Field access for the CodeChickenLib compatibility patch, done the way modern Java requires.
 *
 * <p>Java 17+ does two things that break the reflection patterns CodeChickenLib was written against:
 * it removed {@code Field.modifiers}, and it refuses to build a reflective accessor for a
 * {@code static final} field - even for a plain <b>read</b>, because creating the override accessor
 * tries to create a setter as well and the setter is rejected. {@code Unsafe} has neither
 * restriction, which is why every modern mod loader uses it for exactly this.</p>
 *
 * <p>{@code Unsafe} is fetched reflectively: calling {@code Unsafe.getUnsafe()} directly throws for
 * any caller outside the JDK.</p>
 */
public final class FieldModifiers {

    private static final Unsafe UNSAFE;

    /** Offset of {@code Field.modifiers}, or -1 when this JDK no longer has that slot. */
    private static final long MODIFIERS_OFFSET;

    static {
        Unsafe unsafe = null;
        long offset = -1L;
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Throwable unavailable) {
            unsafe = null;
        }
        if (unsafe != null) {
            try {
                Field modifiers = Field.class.getDeclaredField("modifiers");
                offset = unsafe.objectFieldOffset(modifiers);
            } catch (Throwable noModifiersSlot) {
                offset = -1L;
            }
        }
        UNSAFE = unsafe;
        MODIFIERS_OFFSET = offset;
    }

    private FieldModifiers() {
    }

    /**
     * Reads a field without going through the reflective accessor machinery.
     *
     * @return the field value, or {@code null} when the value cannot be read at all
     */
    public static Object get(Field field, Object instance) {
        if (UNSAFE != null && !field.getType().isPrimitive()) {
            try {
                long offset = java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        ? UNSAFE.staticFieldOffset(field)
                        : UNSAFE.objectFieldOffset(field);
                Object base = java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        ? UNSAFE.staticFieldBase(field)
                        : instance;
                return UNSAFE.getObject(base, offset);
            } catch (Throwable unsupported) {
                // fall through to plain reflection below
            }
        }
        try {
            return field.get(instance);
        } catch (Throwable unreadable) {
            throw new IllegalStateException("Could not read " + field, unreadable);
        }
    }

    /**
     * Writes a field without going through the reflective accessor machinery, so {@code final}
     * fields can still be replaced on a modern JVM.
     */
    public static void set(Field field, Object instance, Object value) {
        if (UNSAFE != null && !field.getType().isPrimitive()) {
            try {
                boolean isStatic = java.lang.reflect.Modifier.isStatic(field.getModifiers());
                long offset = isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
                Object base = isStatic ? UNSAFE.staticFieldBase(field) : instance;
                UNSAFE.putObject(base, offset, value);
                return;
            } catch (Throwable unsupported) {
                // fall through to plain reflection below
            }
        }
        try {
            field.set(instance, value);
        } catch (Throwable unwritable) {
            throw new IllegalStateException("Could not write " + field, unwritable);
        }
    }

    /** Replaces the modifier bitmask of {@code field}, bypassing the {@code final} write check. */
    public static void setFieldModifiers(Field field, int modifiers) {
        if (UNSAFE != null && MODIFIERS_OFFSET >= 0L) {
            UNSAFE.putInt(field, MODIFIERS_OFFSET, modifiers);
        }
    }
}
