package com.modulargrid.common.channel;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

/**
 * Tells script code which machine it is running for, so a per player firm channel can be resolved to the instance of
 * that machine's owner.
 */
public final class ChannelScriptContext {

    /** Guards against a stack that never unwinds, which would otherwise grow for the life of the server. */
    private static final int MAX_DEPTH = 16;

    private static final ThreadLocal<Deque<UUID>> MACHINE_OWNERS = ThreadLocal.withInitial(LinkedList::new);

    private ChannelScriptContext() {
    }

    /**
     * Remembers the owner of the machine whose tick is starting.
     *
     * @param owner the owner of the controller, may be {@code null} for a controller nobody owns
     */
    public static void push(@Nullable UUID owner) {
        Deque<UUID> owners = MACHINE_OWNERS.get();
        if (owners.size() >= MAX_DEPTH) {
            owners.clear();
        }
        owners.addLast(owner);
    }

    /** Forgets the machine whose tick ended, restoring the machine around it (or none). */
    public static void pop() {
        Deque<UUID> owners = MACHINE_OWNERS.get();
        owners.pollLast();
        if (owners.isEmpty()) {
            MACHINE_OWNERS.remove();
        }
    }

    /**
     * @return the owner of the machine currently ticking on this thread, {@code null} outside a machine tick
     */
    @Nullable
    public static UUID currentPlayer() {
        return MACHINE_OWNERS.get().peekLast();
    }

}
