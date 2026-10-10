package com.modulargrid.common.registry;

import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects registry entries before the forge registry event fills the registry,
 * so blocks and their item blocks can be prepared in the same pass.
 */
public class GenericRegistryPrimer {

    public static final GenericRegistryPrimer INSTANCE = new GenericRegistryPrimer();

    private final Map<Type, List<IForgeRegistryEntry<?>>> primed = new HashMap<>();

    private GenericRegistryPrimer() {
    }

    public <V extends IForgeRegistryEntry<V>> V register(V entry) {
        Class<V> type = entry.getRegistryType();
        primed.computeIfAbsent(type, k -> new ArrayList<>()).add(entry);
        return entry;
    }

    public <T extends IForgeRegistryEntry<T>> List<?> getEntries(Class<T> type) {
        return primed.get(type);
    }

    public void wipe(Type type) {
        primed.remove(type);
    }

    @SuppressWarnings("unchecked")
    public <T extends IForgeRegistryEntry<T>> void fillRegistry(Class<T> registrySuperType, IForgeRegistry<T> forgeRegistry) {
        List<?> entries = getEntries(registrySuperType);
        if (entries != null) {
            entries.forEach(entry -> forgeRegistry.register((T) entry));
        }
    }

}
