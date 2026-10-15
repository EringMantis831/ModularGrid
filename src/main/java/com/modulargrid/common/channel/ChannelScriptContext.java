package com.modulargrid.common.channel;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

/**
 * Tells script code which machine it is running for, so a per player firm channel can be resolved to the instance of
 * that machine's owner.
 *
 * <p>A firm channel that is not public belongs to one player each ({@code channelId@player} in the channel store), so
 * {@code Temp.amount} in a machine's recipe logic is only well defined while the machine is known. Modular Machinery
 * fires every recipe event synchronously from the controller's tick
 * ({@code TileMultiblockMachineController#doRestrictedTick}), so the mixin wraps exactly that call and pushes the
 * controller's owner here.</p>
 *
 * <p>The owners form a stack because a controller tick can nest one (a factory thread ticking a recipe inside the
 * factory controller's tick), and the value is only ever read on the thread that pushed it, which is the thread the
 * handler body runs on. A shared channel ignores this state entirely, and reading a per player channel without a
 * context gives zero instead of somebody else's value.</p>
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
