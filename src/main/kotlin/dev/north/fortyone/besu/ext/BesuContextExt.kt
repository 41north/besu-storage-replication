/*
 * Copyright (c) 2020 41North.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.north.fortyone.besu.ext

import org.hyperledger.besu.plugin.BesuContext
import org.hyperledger.besu.services.BesuPluginContextImpl
import java.lang.IllegalStateException

inline fun <reified T : Any> reflektField(entity: Any, fieldName: String): T {
  val field = checkNotNull(entity::class.java.declaredFields.find { it.name == fieldName })
  if (!field.canAccess(entity)) field.isAccessible = true
  return field.get(entity) as T
}

inline fun <reified T> BesuContext.getService(): T =
  this
    .getService(T::class.java)
    .orElseThrow { IllegalStateException("Service ${T::class.java.simpleName} not found or null!") }

fun BesuContext.asPluginContext(): BesuPluginContextImpl =
  this as BesuPluginContextImpl
