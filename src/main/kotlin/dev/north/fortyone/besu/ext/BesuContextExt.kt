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

import dev.north.fortyone.besu.services.ReplicationManager
import org.hyperledger.besu.plugin.BesuContext
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.PicoCLIOptions
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.services.BesuPluginContextImpl
import java.lang.IllegalStateException

inline fun <reified T : Any> reflektField(entity: Any, fieldName: String): T {
  val field = checkNotNull(entity::class.java.declaredFields.find { it.name == fieldName })
  if (!field.canAccess(entity)) field.isAccessible = true
  return field.get(entity) as T
}

fun BesuContext.asPluginContext(): BesuPluginContextImpl =
  this as BesuPluginContextImpl

fun BesuContext.storageService(): StorageService =
  this
    .getService(StorageService::class.java)
    .orElseThrow { IllegalStateException("Could not find StorageService") }

fun BesuContext.besuConfiguration(): BesuConfiguration =
  this
    .getService(BesuConfiguration::class.java)
    .orElseThrow { IllegalStateException("Could not find BesuConfiguration") }

fun BesuContext.metricsSystem(): MetricsSystem =
  this
    .getService(MetricsSystem::class.java)
    .orElseThrow { IllegalStateException("Could not find MetricsSystem") }

fun BesuContext.cliOptions(): PicoCLIOptions =
  this
    .getService(PicoCLIOptions::class.java)
    .orElseThrow { IllegalStateException("Could not find PicoCLIOptions") }

fun BesuContext.replicationManager(): ReplicationManager =
  this
    .getService(ReplicationManager::class.java)
    .orElseThrow { IllegalStateException("Could not find ReplicationManager") }
