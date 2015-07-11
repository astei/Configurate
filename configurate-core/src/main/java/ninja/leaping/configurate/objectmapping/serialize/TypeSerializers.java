/**
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
package ninja.leaping.configurate.objectmapping.serialize;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.SimpleConfigurationNode;
import ninja.leaping.configurate.Types;
import ninja.leaping.configurate.objectmapping.InvalidTypeException;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TypeSerializers {
    private static final TypeSerializerCollection DEFAULT_SERIALIZERS = new TypeSerializerCollection(null);

    public static TypeSerializerCollection getDefaultSerializers() {
        return DEFAULT_SERIALIZERS;
    }

    static {
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(URI.class), new URISerializer());
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(URL.class), new URLSerializer());
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(UUID.class), new UUIDSerializer());
        DEFAULT_SERIALIZERS.registerPredicate(new Predicate<TypeToken<Object>>() {
            @Override
            public boolean apply(TypeToken<Object> input) {
                return input.getRawType().isAnnotationPresent(ConfigSerializable.class);
            }
        }, new AnnotatedObjectSerializer());
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(Number.class), new NumberSerializer());
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(String.class), new StringSerializer());
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(Boolean.class), new BooleanSerializer());
        DEFAULT_SERIALIZERS.registerType(new TypeToken<Map<?, ?>>() {}, new MapSerializer());
        DEFAULT_SERIALIZERS.registerType(new TypeToken<List<?>>() {
        }, new ListSerializer());
        DEFAULT_SERIALIZERS.registerType(new TypeToken<Enum<?>>() {}, new EnumValueSerializer());
        DEFAULT_SERIALIZERS.registerType(TypeToken.of(Pattern.class), new PatternSerializer());
    }


    private static class StringSerializer implements TypeSerializer<String> {
        @Override
        public String deserialize(TypeToken<?> type, ConfigurationNode value) throws InvalidTypeException {
            return value.getString();
        }

        @Override
        public void serialize(TypeToken<?> type, String obj, ConfigurationNode value) {
            value.setValue(obj);
        }
    }

    private static class NumberSerializer implements TypeSerializer<Number> {
        @Override
        public Number deserialize(TypeToken<?> type, ConfigurationNode value) throws InvalidTypeException {
            type = type.wrap();
            Class<?> clazz = type.getRawType();
            if (Integer.class.equals(clazz)) {
                return value.getInt();
            } else if (Long.class.equals(clazz)) {
                return value.getLong();
            } else if (Short.class.equals(clazz)) {
                return (short) value.getInt();
            } else if (Byte.class.equals(clazz)) {
                return (byte) value.getInt();
            } else if (Float.class.equals(clazz)) {
                return value.getFloat();
            } else if (Double.class.equals(clazz)) {
                return value.getDouble();
            }
            return null;
        }

        @Override
        public void serialize(TypeToken<?> type, Number obj, ConfigurationNode value) {
            value.setValue(obj);
        }
    }

    private static class BooleanSerializer implements TypeSerializer<Boolean> {
        @Override
        public Boolean deserialize(TypeToken<?> type, ConfigurationNode value) throws InvalidTypeException {
            return value.getBoolean();
        }

        @Override
        public void serialize(TypeToken<?> type, Boolean obj, ConfigurationNode value) {
            value.setValue(Types.asBoolean(obj));
        }
    }

    private static class EnumValueSerializer implements TypeSerializer<Enum> {

        @Override
        public Enum deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            String enumConstant = value.getString();
            if (enumConstant == null) {
                throw new ObjectMappingException("No value present in node " + value);
            }
            enumConstant = enumConstant.toUpperCase();

            Enum ret;
            try {
                ret = Enum.valueOf(type.getRawType().asSubclass(Enum.class), value.getString().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ObjectMappingException("Invalid enum constant provided for " + value.getKey() + ": Expected a value of enum " + type + ", got " + enumConstant);
            }
            return ret;
        }

        @Override
        public void serialize(TypeToken<?> type, Enum obj, ConfigurationNode value) throws ObjectMappingException {
            value.setValue(obj.name());
        }
    }

    private static class MapSerializer implements TypeSerializer<Map<?, ?>> {
        @Override
        public Map<?, ?> deserialize(TypeToken<?> type, ConfigurationNode node) throws ObjectMappingException {
            Map<Object, Object> ret = new LinkedHashMap<>();
            if (node.hasMapChildren()) {
                TypeToken<?> key = type.resolveType(Map.class.getTypeParameters()[0]);
                TypeToken<?> value = type.resolveType(Map.class.getTypeParameters()[1]);
                TypeSerializer keySerial = node.getOptions().getSerializers().get(key);
                TypeSerializer valueSerial = node.getOptions().getSerializers().get(value);

                if (keySerial == null) {
                    throw new ObjectMappingException("No type serializer available for type " + key);
                }

                if (valueSerial == null) {
                    throw new ObjectMappingException("No type serializer available for type " + value);
                }

                for (Map.Entry<Object, ? extends ConfigurationNode> ent : node.getChildrenMap().entrySet()) {
                    Object keyValue = keySerial.deserialize(key, SimpleConfigurationNode.root().setValue(ent.getKey()));
                    Object valueValue = valueSerial.deserialize(value, ent.getValue());
                    if (keyValue == null || valueValue == null) {
                        continue;
                    }

                    ret.put(keyValue, valueValue);
                }
            }
            return ret;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void serialize(TypeToken<?> type, Map<?, ?> obj, ConfigurationNode node) throws ObjectMappingException {
            TypeToken<?> key = type.resolveType(Map.class.getTypeParameters()[0]);
            TypeToken<?> value = type.resolveType(Map.class.getTypeParameters()[1]);
            TypeSerializer keySerial = node.getOptions().getSerializers().get(key);
            TypeSerializer valueSerial = node.getOptions().getSerializers().get(value);

            if (keySerial == null) {
                throw new ObjectMappingException("No type serializer available for type " + key);
            }

            if (valueSerial == null) {
                throw new ObjectMappingException("No type serializer available for type " + value);
            }

            node.setValue(null);
            for (Map.Entry<?, ?> ent : obj.entrySet()) {
                SimpleConfigurationNode keyNode = SimpleConfigurationNode.root();
                keySerial.serialize(key, ent.getKey(), keyNode);
                valueSerial.serialize(value, ent.getValue(), node.getNode(keyNode.getValue()));
            }
        }
    }


    private static class ListSerializer implements TypeSerializer<List<?>> {

        @Override
        public List<?> deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            TypeToken<?> entryType = type.resolveType(List.class.getTypeParameters()[0]);
            TypeSerializer entrySerial = value.getOptions().getSerializers().get(entryType);
            if (entrySerial == null) {
                throw new ObjectMappingException("No applicable type serializer for type " + entryType);
            }

            if (value.hasListChildren()) {
                List<? extends ConfigurationNode> values = value.getChildrenList();
                List<Object> ret = new ArrayList<>(values.size());
                for (ConfigurationNode ent : values) {
                    ret.add(entrySerial.deserialize(entryType, ent));
                }
                return ret;
            } else {
                Object unwrappedVal = value.getValue();
                if (unwrappedVal != null) {
                    return Lists.newArrayList(entrySerial.deserialize(entryType, value));
                }
            }
            return new ArrayList<>();
        }

        @Override
        public void serialize(TypeToken<?> type, List<?> obj, ConfigurationNode value) throws ObjectMappingException {
            TypeToken<?> entryType = type.resolveType(List.class.getTypeParameters()[0]);
            TypeSerializer entrySerial = value.getOptions().getSerializers().get(entryType);
            if (entrySerial == null) {
                throw new ObjectMappingException("No applicable type serializer for type " + entryType);
            }
            value.setValue(null);
            for (Object ent : obj) {
                entrySerial.serialize(entryType, ent, value.getAppendedNode());
            }
        }
    }

    private static class AnnotatedObjectSerializer implements TypeSerializer<Object> {
        @Override
        public Object deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            Class<?> clazz = getInstantiableType(type, value.getNode("__class__").getString());
            return value.getOptions().getObjectMapperFactory().getMapper(clazz).bindToNew().populate(value);
        }

        private Class<?> getInstantiableType(TypeToken<?> type, String configuredName) throws ObjectMappingException {
            Class<?> retClass;
            if (type.getRawType().isInterface() || Modifier.isAbstract(type.getRawType().getModifiers())) {
                if (configuredName == null) {
                    throw new ObjectMappingException("No available configured type for instances of " + type);
                } else {
                    try {
                        retClass = Class.forName(configuredName);
                    } catch (ClassNotFoundException e) {
                        throw new ObjectMappingException("Unknown class of object " + configuredName, e);
                    }
                }
            } else {
                retClass = type.getRawType();
            }
            return retClass;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(TypeToken<?> type, Object obj, ConfigurationNode value) throws ObjectMappingException {
            if (type.getRawType().isInterface() || Modifier.isAbstract(type.getRawType().getModifiers())) {
                value.getNode("__class__").setValue(type.getRawType().getCanonicalName());
            }
            ((ObjectMapper<Object>) value.getOptions().getObjectMapperFactory().getMapper(obj.getClass()))
                    .bind(obj).serialize(value);
        }
    }

    private static class URISerializer implements TypeSerializer<URI> {
        @Override
        public URI deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            String plainUri = value.getString();
            if (plainUri == null) {
                throw new ObjectMappingException("No value present in node " + value);
            }

            URI uri;
            try {
                uri = new URI(plainUri);
            } catch (URISyntaxException e) {
                 throw new ObjectMappingException("Invalid URI string provided for " + value.getKey() + ": got " + plainUri);
            }

            return uri;
        }

        @Override
        public void serialize(TypeToken<?> type, URI obj, ConfigurationNode value) throws ObjectMappingException {
            value.setValue(obj.toString());
        }

    }

    private static class URLSerializer implements TypeSerializer<URL> {

        @Override
        public URL deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            String plainUrl = value.getString();
            if (plainUrl == null) {
                throw new ObjectMappingException("No value present in node " + value);
            }

            URL url;
            try {
                url = new URL(plainUrl);
            } catch (MalformedURLException e) {
                throw new ObjectMappingException("Invalid URL string provided for " + value.getKey() + ": got " + plainUrl);
            }

            return url;
        }

        @Override
        public void serialize(TypeToken<?> type, URL obj, ConfigurationNode value) throws ObjectMappingException {
            value.setValue(obj.toString());
        }

    }

    private static class UUIDSerializer implements TypeSerializer<UUID> {
        @Override
        public UUID deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            try {
                return UUID.fromString(value.getString());
            } catch (IllegalArgumentException ex) {
                throw new ObjectMappingException("Value not a UUID", ex);
            }
        }

        @Override
        public void serialize(TypeToken<?> type, UUID obj, ConfigurationNode value) throws ObjectMappingException {
            value.setValue(obj.toString());
        }
    }

    private static class PatternSerializer implements TypeSerializer<Pattern> {

        @Override
        public Pattern deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
            try {
                return Pattern.compile(value.getString());
            } catch (PatternSyntaxException ex) {
                throw new ObjectMappingException(ex);
            }
        }

        @Override
        public void serialize(TypeToken<?> type, Pattern obj, ConfigurationNode value) throws ObjectMappingException {
            value.setValue(obj.pattern());
        }
    }
}