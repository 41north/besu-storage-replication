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
import dev.north.fortyone.besu.ext.toByteArray
import dev.north.fortyone.besu.ext.toReplicationEvent
import dev.north.fortyone.besu.replication.fb.ReplicationEventType
import dev.north.fortyone.besu.replication.fb.TransactionEventType
import kotlinx.coroutines.runBlocking
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.services.BesuConfigurationImpl
import org.hyperledger.besu.services.BesuPluginContextImpl
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec
import java.lang.IllegalArgumentException
import java.nio.file.Path
import java.time.Duration
import java.util.function.Supplier

@Command(
  name = "restore"
)
class ReplicationRestoreCommand : Runnable {

  companion object {
    private val logger = LoggerFactory.getLogger(ReplicationRestoreCommand::class.java)
  }

  @ParentCommand
  // cannot set this type to BesuCommand as PicoCli gets confused about the parent being the mixin
  private lateinit var parentCommand: Any

  @Spec
  private lateinit var spec: CommandSpec

  override fun run() {

    val besuCommand = (parentCommand as ReplicationSubCommand).parentCommand as BesuCommand

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
    val transactionLog = replicationManager.transactionLog

    val segments = listOf(
      KeyValueSegmentIdentifier.BLOCKCHAIN,
      KeyValueSegmentIdentifier.WORLD_STATE
    )

    val underlyingStorageFactory = storageService
      .getByName(keyValueStorageName)
      .orElseThrow { throw IllegalArgumentException("Invalid key value storage name: $keyValueStorageName") }

    // delete local storage

    val storageBySegment =
      segments
        .map { segment -> Pair(segment, underlyingStorageFactory.create(segment, pluginCommonConfig, metricsSystem.get())) }
        .toMap()
        .also { it.values.forEach{ storage -> storage.clear() }}

    var entries: List<Pair<Long, ByteArray>>
    do {

      entries = runBlocking { transactionLog.read(Duration.ofSeconds(10)) }

      entries
        .map { (_, bytes) -> bytes.toReplicationEvent() }
        .forEach { event ->

          val segmentIdentifierBytes = event.segmentIdAsByteBuffer().toByteArray()

          val segmentIdentifier = KeyValueSegmentIdentifier.values()
            .find { it.id!!.contentEquals(segmentIdentifierBytes) }

          val storage = storageBySegment[segmentIdentifier]!!

          when(event.type()) {
            ReplicationEventType.CLEAR_ALL -> storage.clear()

            ReplicationEventType.TRANSACTION ->
              storage.startTransaction().run {

                for (eventIdx in 0.until(event.transaction().eventsLength())) {

                  val txEvent = event.transaction().events(eventIdx)

                  when(txEvent.type()) {
                    TransactionEventType.PUT ->
                      put(txEvent.keyAsByteBuffer().toByteArray(), txEvent.valueAsByteBuffer().toByteArray())

                    TransactionEventType.REMOVE ->
                      remove(txEvent.keyAsByteBuffer().toByteArray())
                  }

                }

                commit()
              }

          }

        }

      logger.info("Processed {} entries", entries.size)

    } while (entries.isNotEmpty())

    logger.info("Finished")

  }
}
