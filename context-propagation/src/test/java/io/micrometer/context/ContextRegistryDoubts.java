package io.micrometer.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextRegistryDoubts {

    private final ContextRegistry registry = new ContextRegistry();

    @Test
    void different_contexts_should_be_accessed_by_different_accessors() {
        Lib1ContextAccessor lib1ContextAccessor = new Lib1ContextAccessor();
        Lib2ContextAccessor lib2ContextAccessor = new Lib2ContextAccessor();

        this.registry.registerContextAccessor(lib2ContextAccessor);
        assertThat(this.registry.getContextAccessors()).containsExactly(lib2ContextAccessor);

        this.registry.registerContextAccessor(lib1ContextAccessor);
        assertThat(this.registry.getContextAccessors()).containsExactlyInAnyOrder(lib1ContextAccessor, lib2ContextAccessor);

        // accidentally we get lib2ContextAccessor as it was registered first so it's
        // first in the list and it says it can operate on a type assignable to
        // MapContext, of which Lib1MapContext is a child type.
        ContextAccessor<?, ?> contextAccessor =
                this.registry.getContextAccessorForRead(new Lib1MapContext());

        assertThat(contextAccessor).isSameAs(lib1ContextAccessor);
    }

    @Test
    void different_accessors_should_not_replace_each_other() {
        ContextAccessor<? extends MapContext, ? extends MapContext> lib1Accessor
                = new GenericAccessor<Lib1MapContext, Lib1MapContext>();

        ContextAccessor<? extends MapContext, ? extends MapContext> maliciousAccessor
                = new GenericAccessor<Lib2MapContext, Lib2MapContext>();

        this.registry.registerContextAccessor(lib1Accessor);
        assertThat(this.registry.getContextAccessors()).containsExactly(lib1Accessor);

        this.registry.registerContextAccessor(maliciousAccessor);

        // the following fails, as maliciousAccessor replaced the lib1Accessor
        assertThat(this.registry.getContextAccessors()).containsExactlyInAnyOrder(lib1Accessor, maliciousAccessor);

        // this assertion also fails, as the maliciousAccessor does not support reading
        // from Lib1MapContext
        assertThat(this.registry.getContextAccessorForRead(new Lib1MapContext())).isSameAs(lib1Accessor);
    }

    // common context for a family of libraries dealing with their own contexts
    private interface MapContext {
        void put(Object key, Object value);
        <V> V get(Object key);
    }

    private static class Lib1MapContext implements MapContext {
        private final Map<Object, Object> items = new ConcurrentHashMap<>();

        @Override
        public void put(Object key, Object value) {
            items.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> V get(Object key) {
            return (V) items.get(key);
        }
    }

    private static class Lib2MapContext implements MapContext {

        private final Map<Object, Object> items = new ConcurrentHashMap<>();

        @Override
        public void put(Object key, Object value) {
            System.out.println("adding [key=" + key + ", value=" + value + "]");
            items.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> V get(Object key) {
            V value = (V) items.get(key);
            System.out.println("retrieved [key=" + key + ", value=" + value + "]");
            return value;
        }
    }

    private static class Lib1ContextAccessor implements ContextAccessor<Lib1MapContext,
            Lib1MapContext> {

        @Override
        public boolean canReadFrom(Class<?> contextType) {
            return Lib1MapContext.class.isAssignableFrom(contextType);
        }

        @Override
        public void readValues(Lib1MapContext sourceContext,
                Predicate<Object> keyPredicate,
                Map<Object, Object> readValues) {
            // shortcut
            readValues.putAll(sourceContext.items);
        }

        @Override
        public <T> T readValue(Lib1MapContext sourceContext, Object key) {
            return sourceContext.get(key);
        }

        @Override
        public boolean canWriteTo(Class<?> contextType) {
            return Lib1MapContext.class.isAssignableFrom(contextType);
        }

        @Override
        public Lib1MapContext writeValues(Map<Object, Object> valuesToWrite,
                Lib1MapContext targetContext) {
            // shortcut
            targetContext.items.putAll(valuesToWrite);
            return targetContext;
        }
    }

    private static class Lib2ContextAccessor implements ContextAccessor<Lib2MapContext,
            Lib2MapContext> {

        @Override
        public boolean canReadFrom(Class<?> contextType) {
            // intentional mistake - implementor assumes no other implementations for
            // MapContext
            return MapContext.class.isAssignableFrom(contextType);
        }

        @Override
        public void readValues(Lib2MapContext sourceContext,
                Predicate<Object> keyPredicate,
                Map<Object, Object> readValues) {
            // shortcut
            readValues.putAll(sourceContext.items);
        }

        @Override
        public <T> T readValue(Lib2MapContext sourceContext, Object key) {
            return sourceContext.get(key);
        }

        @Override
        public boolean canWriteTo(Class<?> contextType) {
            // intentional mistake - implementor assumes no other implementations for
            // MapContext
            return MapContext.class.isAssignableFrom(contextType);
        }

        @Override
        public Lib2MapContext writeValues(Map<Object, Object> valuesToWrite,
                Lib2MapContext targetContext) {
            // shortcut
            targetContext.items.putAll(valuesToWrite);
            return targetContext;
        }
    }

    private static class GenericAccessor<R extends MapContext, W extends MapContext> implements ContextAccessor<R, W> {
        @Override
        public boolean canReadFrom(Class<?> contextType) {
            return Lib2MapContext.class.isAssignableFrom(contextType);
        }

        @Override
        public void readValues(R sourceContext,
                Predicate<Object> keyPredicate,
                Map<Object, Object> readValues) {
            // skipped
        }

        @Override
        public <T> T readValue(R sourceContext, Object key) {
            // skipped
            return null;
        }

        @Override
        public boolean canWriteTo(Class<?> contextType) {
            return MapContext.class.isAssignableFrom(contextType);
        }

        @Override
        public W writeValues(Map<Object, Object> valuesToWrite,
                W targetContext) {
            // skipped
            return null;
        }
    }
}
