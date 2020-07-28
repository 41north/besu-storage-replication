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

package dev.north.fortyone.besu.commands

import dev.north.fortyone.besu.ext.reflektField
import dev.north.fortyone.besu.ext.replicationManager
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.services.BesuConfigurationImpl
import org.hyperledger.besu.services.BesuPluginContextImpl
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec
import java.lang.IllegalArgumentException
import java.nio.file.Path
import java.util.function.Supplier

@Command(
  name = "replication"
)
class ReplicationExportCommand : Runnable {

  @ParentCommand
  // cannot set this type to BesuCommand as PicoCli gets confused about the parent being the mixin
  private lateinit var parentCommand: Any

  @Spec
  private lateinit var spec: CommandSpec

  override fun run() {

    val besuCommand = parentCommand as BesuCommand

    val dataPath = reflektField<Path>(besuCommand, "dataPath")
    val pluginContext = reflektField<BesuPluginContextImpl>(besuCommand, "besuPluginContext")
    val storageService = reflektField<StorageService>(besuCommand, "storageService")
    val metricsSystem = reflektField<Supplier<ObservableMetricsSystem>>(besuCommand, "metricsSystem")
    val keyValueStorageName = reflektField<String>(besuCommand, "keyValueStorageName")

    val dataDir: Path = dataPath.toAbsolutePath()
    val pluginCommonConfig = BesuConfigurationImpl(dataDir, dataDir.resolve(BesuController.DATABASE_PATH))
    pluginContext.addService(BesuConfiguration::class.java, pluginCommonConfig)

    pluginContext.addService(MetricsSystem::class.java, metricsSystem.get())
    pluginContext.startPlugins()

    val replicationManager = pluginContext.replicationManager()

    val segments = listOf(
      KeyValueSegmentIdentifier.BLOCKCHAIN,
      KeyValueSegmentIdentifier.WORLD_STATE
    )

    val underlyingStorageFactory = storageService
      .getByName(keyValueStorageName)
      .orElseThrow { throw IllegalArgumentException("Invalid key value storage name: $keyValueStorageName") }

    for (segment in segments) {

      val storage = underlyingStorageFactory.create(segment, pluginCommonConfig, metricsSystem.get())

      val keysStream = storage.streamKeys()
      val iterator = keysStream.spliterator()

      val flushBatch = { batch: List<ByteArray> ->
            batch.map {

            }
      }

      val batchSize = 1024
      var batch = mutableListOf<ByteArray>()

      while(iterator.tryAdvance { bytes -> batch.add(bytes)}) {
        if (batch.size == batchSize) {

        }


      }

    }
  }
}