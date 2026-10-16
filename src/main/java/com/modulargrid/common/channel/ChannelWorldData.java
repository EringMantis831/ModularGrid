package com.modulargrid.common.channel;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global persistence for all {@link Channel channels}.
 */
public class ChannelWorldData extends WorldSavedData {

    public static final String DATA_NAME = "modulargrid_channels";

    private final Map<UUID, Map<String, Channel>> channels = new ConcurrentHashMap<>();

    // MapStorage instantiates saved data through a (String) constructor
    @SuppressWarnings("unused")
    public ChannelWorldData(String name) {
        super(name);
    }

    public ChannelWorldData() {
        this(DATA_NAME);
    }

    /**
     * @return the mutable channel map of the given owner, creating it when absent
     */
    public Map<String, Channel> getOrCreateChannels(UUID owner) {
        return channels.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>());
    }

    /**
     * @return the channel map of the given owner, {@code null} when they own nothing
     */
    public Map<String, Channel> getChannels(UUID owner) {
        return channels.get(owner);
    }

    /**
     * @return every owner with at least one channel, useful for global lookups
     */
    public Map<UUID, Map<String, Channel>> getAllChannels() {
        return channels;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        channels.clear();
        for (String ownerKey : nbt.getKeySet()) {
            UUID owner;
            try {
                owner = UUID.fromString(ownerKey);
            } catch (IllegalArgumentException ex) {
                // Not one of ours, skip instead of failing the whole save data
                continue;
            }
            NBTTagCompound ownerTag = nbt.getCompoundTag(ownerKey);
            Map<String, Channel> ownerChannels = new ConcurrentHashMap<>();
            for (String name : ownerTag.getKeySet()) {
                ownerChannels.put(name, Channel.readFromNBT(name, owner, ownerTag.getCompoundTag(name)));
            }
            channels.put(owner, ownerChannels);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        channels.forEach((owner, ownerChannels) -> {
            NBTTagCompound ownerTag = new NBTTagCompound();
            ownerChannels.forEach((name, channel) -> ownerTag.setTag(name, channel.writeToNBT()));
            compound.setTag(owner.toString(), ownerTag);
        });
        return compound;
    }

}
