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
/**
 * Object mapper to handle converting between raw nodes and POJOs.
 *
 * <p>The object mapper provides type serializers that can coerce data from
 * configuration-native types to the desired types, and back again. This removes
 * a lot of the uncertainty in trying to determine which value types are
 * supported by a specific configuration format.
 *
 * <p>Object mappers work on fields in classes, and can optionally construct new
 * instances of classes -- either if the class has an empty constructor for the
 * {@link org.spongepowered.configurate.objectmapping.DefaultObjectMapperFactory},
 * or if the class is injectable through
 * {@link org.spongepowered.configurate.objectmapping.GuiceObjectMapperFactory}.
 */
package org.spongepowered.configurate.objectmapping;
