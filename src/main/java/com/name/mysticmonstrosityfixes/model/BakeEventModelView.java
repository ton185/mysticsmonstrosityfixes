package com.name.mysticmonstrosityfixes.model;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class BakeEventModelView {
    private BakeEventModelView() {
    }

    public static Set<ResourceLocation> keySet(Map<ResourceLocation, BakedModel> registry, Set<ResourceLocation> locations) {
        return new AbstractSet<>() {
            @Override
            public Iterator<ResourceLocation> iterator() {
                return locations.iterator();
            }

            @Override
            public int size() {
                return locations.size();
            }

            @Override
            public boolean isEmpty() {
                return locations.isEmpty();
            }

            @Override
            public boolean contains(Object object) {
                return registry.containsKey(object);
            }
        };
    }

    public static Set<Map.Entry<ResourceLocation, BakedModel>> entrySet(
            Map<ResourceLocation, BakedModel> registry, Set<ResourceLocation> locations
    ) {
        return new AbstractSet<>() {
            @Override
            public Iterator<Map.Entry<ResourceLocation, BakedModel>> iterator() {
                Iterator<ResourceLocation> iterator = locations.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Map.Entry<ResourceLocation, BakedModel> next() {
                        return new ViewEntry(registry, iterator.next());
                    }
                };
            }

            @Override
            public int size() {
                return locations.size();
            }

            @Override
            public boolean isEmpty() {
                return locations.isEmpty();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Map.Entry<?, ?> entry && registry.containsKey(entry.getKey());
            }

            @Override
            public boolean removeAll(Collection<?> collection) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class ViewEntry implements Map.Entry<ResourceLocation, BakedModel> {
        private final Map<ResourceLocation, BakedModel> registry;
        private final ResourceLocation location;

        private ViewEntry(Map<ResourceLocation, BakedModel> registry, ResourceLocation location) {
            this.registry = registry;
            this.location = location;
        }

        @Override
        public ResourceLocation getKey() {
            return this.location;
        }

        @Override
        public BakedModel getValue() {
            return this.registry.get(this.location);
        }

        @Override
        public BakedModel setValue(BakedModel value) {
            return this.registry.put(this.location, value);
        }
    }
}
