/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate.objectmapping;

import static java.util.Objects.requireNonNull;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNodeIntermediary;
import org.spongepowered.configurate.ScopedConfigurationNode;
import org.spongepowered.configurate.serialize.ConfigSerializable;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is the object mapper. It handles conversion between configuration nodes
 * and fields annotated with {@link Setting} in objects.
 *
 * <p>Values in the node not used by the mapped object will be preserved.
 *
 * @param <T> The type to work with
 */
public class ObjectMapper<T> {

    private final TypeToken<T> type;
    private final Class<? super T> clazz;
    @Nullable
    private final Invokable<T, T> constructor;
    private final Map<String, FieldData> cachedFields = new LinkedHashMap<>();

    /**
     * Create a new object mapper that can work with objects of the given class
     * using the {@link DefaultObjectMapperFactory}.
     *
     * @param clazz The type of object
     * @param <T> The type
     * @return An appropriate object mapper instance. May be a shared instance.
     * @throws ObjectMappingException If invalid annotated fields are presented
     */
    public static <T> ObjectMapper<T> forClass(final @NonNull Class<T> clazz) throws ObjectMappingException {
        return DefaultObjectMapperFactory.getInstance().getMapper(clazz);
    }

    /**
     * Create a new object mapper that can work with objects of the given type
     * using the {@link DefaultObjectMapperFactory}.
     *
     * @param type The type of object
     * @param <T> The type
     * @return An appropriate object mapper instance. May be a shared instance.
     * @throws ObjectMappingException If invalid annotated fields are presented
     */
    public static <T> ObjectMapper<T> forType(final @NonNull TypeToken<T> type) throws ObjectMappingException {
        return DefaultObjectMapperFactory.getInstance().getMapper(type);
    }

    /**
     * Creates a new object mapper bound to the given object.
     *
     * <strong>CAUTION</strong> Generic type information will be lost when
     * creating a mapper this way. Provide a {@link TypeToken} to avoid this
     *
     * @param obj The object
     * @param <T> The object type
     * @return An appropriate object mapper instance.
     * @throws ObjectMappingException when an object is provided that is not
     *                              suitable for object mapping.
     *                              Reasons may include but are not limited to:
     *                              <ul>
     *                                  <li>Not annotated with {@link ConfigSerializable} annotation</li>
     *                                  <li>Invalid field types</li>
     *                              </ul>
     */
    @SuppressWarnings("unchecked")
    public static <T> ObjectMapper<T>.BoundInstance forObject(final @NonNull T obj) throws ObjectMappingException {
        return forClass((Class<T>) requireNonNull(obj).getClass()).bind(obj);
    }

    /**
     * Creates a new object mapper bound to the given object.
     *
     * @param type The generic type of the object
     * @param obj The object
     * @param <T> The object type
     * @return An appropriate object mapper instance.
     * @throws ObjectMappingException when an object is provided that is not
     *                              suitable for object mapping.
     *                              Reasons may include but are not limited to:
     *                              <ul>
     *                                  <li>Not annotated with {@link ConfigSerializable} annotation</li>
     *                                  <li>Invalid field types</li>
     *                                  <li>Specified type is an interface</li>
     *                              </ul>
     */
    @SuppressWarnings("unchecked")
    public static <T> ObjectMapper<T>.BoundInstance forObject(final TypeToken<T> type, final @NonNull T obj) throws ObjectMappingException {
        return forType(requireNonNull(type)).bind(obj);
    }

    /**
     * Holder for field-specific information.
     */
    protected static class FieldData {
        private final Field field;
        private final TypeToken<?> fieldType;
        private final @Nullable String comment;

        public FieldData(final Field field, final @Nullable String comment, final TypeToken<?> resolvedFieldType) {
            this.field = field;
            this.comment = comment;
            this.fieldType = resolvedFieldType;
        }

        public <N extends ScopedConfigurationNode<N>> void deserializeFrom(final Object instance, final N node) throws ObjectMappingException {
            final @Nullable TypeSerializer<?> serial = node.getOptions().getSerializers().get(this.fieldType);
            if (serial == null) {
                throw new ObjectMappingException("No TypeSerializer found for field " + this.field.getName() + " of type "
                        + this.fieldType);
            }
            final @Nullable Object newVal = node.isVirtual() ? null : serial.deserialize(this.fieldType, node);
            try {
                if (newVal == null && node.getOptions().shouldCopyDefaults()) {
                    final Object existingVal = this.field.get(instance);
                    if (existingVal != null) {
                        serializeTo(instance, node);
                    }
                } else {
                    this.field.set(instance, newVal);
                }
            } catch (final IllegalAccessException e) {
                throw new ObjectMappingException("Unable to deserialize field " + this.field.getName(), e);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void serializeTo(final Object instance, final ScopedConfigurationNode<?> node) throws ObjectMappingException {
            try {
                final Object fieldVal = this.field.get(instance);
                if (fieldVal == null) {
                    node.setValue(null);
                } else {
                    final @Nullable TypeSerializer serial = node.getOptions().getSerializers().get(this.fieldType);
                    if (serial == null) {
                        throw new ObjectMappingException("No TypeSerializer found for field " + this.field.getName() + " of type " + this.fieldType);
                    }
                    serial.serialize(this.fieldType, fieldVal, node);
                }

                if (node instanceof CommentedConfigurationNodeIntermediary<?> && this.comment != null && !this.comment.isEmpty()) {
                    final CommentedConfigurationNodeIntermediary<?> commentNode = ((CommentedConfigurationNodeIntermediary<?>) node);
                    commentNode.setCommentIfAbsent(this.comment);
                }
            } catch (final IllegalAccessException e) {
                throw new ObjectMappingException("Unable to serialize field " + this.field.getName(), e);
            }
        }
    }

    /**
     * Represents an object mapper bound to a certain instance of the object.
     */
    public class BoundInstance {
        private final T boundInstance;

        protected BoundInstance(final T boundInstance) {
            this.boundInstance = boundInstance;
        }

        /**
         * Populate the annotated fields in a pre-created object.
         *
         * @param <N> The type of node being populated
         * @param source The source to get data from
         * @return The object provided, for easier chaining
         * @throws ObjectMappingException If an error occurs while
         *                                 populating data.
         */
        public <N extends ScopedConfigurationNode<N>> T populate(final N source) throws ObjectMappingException {
            for (Map.Entry<String, FieldData> ent : ObjectMapper.this.cachedFields.entrySet()) {
                final N node = source.getNode(ent.getKey());
                ent.getValue().deserializeFrom(this.boundInstance, node);
            }

            if (source.isVirtual()) { // we didn't save anything
                source.setValue(Collections.emptyMap());
            }
            return this.boundInstance;
        }

        /**
         * Serialize the data contained in annotated fields to the
         * configuration node.
         *
         * @param target The target node to serialize to
         * @param <N> The type of node being serialized to
         * @throws ObjectMappingException if serialization was not possible due
         *                                to some error.
         */
        public <N extends ScopedConfigurationNode<N>> void serialize(final N target) throws ObjectMappingException {
            for (Map.Entry<String, FieldData> ent : ObjectMapper.this.cachedFields.entrySet()) {
                final N node = target.getNode(ent.getKey());
                ent.getValue().serializeTo(this.boundInstance, node);
            }
        }

        /**
         * Return the instance this mapper is bound to.
         *
         * @return The active instance
         */
        public T getInstance() {
            return this.boundInstance;
        }
    }

    /**
     * Create a new object mapper of a given type. The given type must not be
     * an interface.
     *
     * @param type The type this object mapper will work with
     * @throws ObjectMappingException When errors occur discovering fields in
     *                                the class
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ObjectMapper(final TypeToken<T> type) throws ObjectMappingException {
        this.type = type;
        this.clazz = type.getRawType();
        if (this.clazz.isInterface()) {
            throw new ObjectMappingException("ObjectMapper can only work with concrete types");
        }

        @Nullable Invokable<T, T> constructor = null;
        try {
            constructor = type.constructor(type.getRawType().getDeclaredConstructor());
            constructor.setAccessible(true);
        } catch (final NoSuchMethodException ignore) { }
        this.constructor = constructor;
        TypeToken<? super T> collectType = type;
        Class<? super T> collectClass = type.getRawType();
        while (true) {
            collectFields(this.cachedFields, collectType);
            collectClass = collectClass.getSuperclass();
            if (collectClass.equals(Object.class)) {
                break;
            }
            collectType = collectType.getSupertype((Class) collectClass);
        }
    }

    protected void collectFields(final Map<String, FieldData> cachedFields, final TypeToken<? super T> clazz) throws ObjectMappingException {
        for (Field field : clazz.getRawType().getDeclaredFields()) {
            if (field.isAnnotationPresent(Setting.class)) {
                final Setting setting = field.getAnnotation(Setting.class);
                String path = setting.value();
                if (path.isEmpty()) {
                    path = field.getName();
                }

                final TypeToken<?> fieldType = clazz.resolveType(field.getGenericType());
                final FieldData data = new FieldData(field, setting.comment(), fieldType);
                field.setAccessible(true);
                if (!cachedFields.containsKey(path)) {
                    cachedFields.put(path, data);
                }
            }
        }
    }

    /**
     * Create a new instance of an object of the appropriate type. This method
     * is not responsible for any field population.
     *
     * @return The new object instance
     * @throws ObjectMappingException If constructing a new instance was
     *                                not possible
     */
    protected T constructObject() throws ObjectMappingException {
        if (this.constructor == null) {
            throw new ObjectMappingException("No zero-arg constructor is available for class "
                    + this.type + " but one is required to construct new instances!");
        }
        try {
            return this.constructor.invoke(null);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            throw new ObjectMappingException("Unable to create instance of target class " + this.type, e);
        }
    }

    /**
     * Returns whether this object mapper can create new object instances. This
     * may be false if the provided class has no zero-argument constructors.
     *
     * @return Whether new object instances can be created
     */
    public boolean canCreateInstances() {
        return this.constructor != null;
    }

    /**
     * Return a view on this mapper that is bound to a single object instance.
     *
     * @param instance The instance to bind to
     * @return A view referencing this mapper and the bound instance
     */
    public BoundInstance bind(final T instance) {
        return new BoundInstance(instance);
    }

    /**
     * Returns a view on this mapper that is bound to a newly created
     * object instance.
     *
     * @see #bind(Object)
     * @return Bound mapper attached to a new object instance
     * @throws ObjectMappingException If the object could not be
     *                                constructed correctly
     */
    public BoundInstance bindToNew() throws ObjectMappingException {
        return new BoundInstance(constructObject());
    }

    public TypeToken<T> getMappedType() {
        return this.type;
    }

}
