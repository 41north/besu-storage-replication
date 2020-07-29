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

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(
  name = "replication"
)
class ReplicationSubCommand : Runnable {

  @ParentCommand
  // cannot set this type to BesuCommand as PicoCli gets confused about the parent being the mixin
  private lateinit var parentCommand: Any

  @Spec
  private lateinit var spec: CommandSpec

  override fun run() {

    // val besuCommand = parentCommand as BesuCommand
    //
    // val dataPath = reflektField<Path>(besuCommand, "dataPath")
    // val pluginContext = reflektField<BesuPluginContextImpl>(besuCommand, "besuPluginContext")
    // val storageService = reflektField<StorageService>(besuCommand, "storageService")
    // val metricsSystem = reflektField<Supplier<ObservableMetricsSystem>>(besuCommand, "metricsSystem")
    //
    // val dataDir: Path = dataPath.toAbsolutePath()
    // val pluginCommonConfig = BesuConfigurationImpl(dataDir, dataDir.resolve(BesuController.DATABASE_PATH))
    // pluginContext.addService(BesuConfiguration::class.java, pluginCommonConfig)
    //
    // pluginContext.addService(MetricsSystem::class.java, metricsSystem.get())
    //
    // pluginContext.startPlugins()
    //
    // val rocksDbFactory = storageService.getByName("rocksdb").get()
    //
    // val storageBySegment = mutableMapOf<SegmentIdentifier, KeyValueStorage>()
    // val transactionLog = KafkaTransactionLog()
    //
    // runBlocking {
    //   do {
    //
    //     val events = transactionLog.read(Duration.ofSeconds(30))
    //
    //     events
    //       .map { (_, v) -> v.toReplicationEvent() }
    //       .forEach { event ->
    //
    //         val segmentId = KeyValueSegmentIdentifier
    //           .values()
    //           .find { it.id!!.contentEquals(event.segmentIdAsByteBuffer().toByteArray()) }
    //           ?: throw Error("Segment id not found")
    //
    //         val storage = storageBySegment.getOrPut(segmentId, {
    //           rocksDbFactory
    //             .create(segmentId, pluginCommonConfig, metricsSystem.get())
    //             .also { it.clear() }
    //         })
    //
    //         when (event.type()) {
    //
    //           ReplicationEventType.CLEAR_ALL -> storage.clear()
    //
    //           ReplicationEventType.TRANSACTION ->
    //             event.transaction()
    //               .also { tx ->
    //
    //                 storage
    //                   .startTransaction()
    //                   .also { storageTx ->
    //
    //                     0.until(tx.eventsLength())
    //                       .map { idx -> tx.events(idx) }
    //                       .forEach { txEvent ->
    //
    //                         when (txEvent.type()) {
    //                           TransactionEventType.PUT ->
    //                             storageTx.put(
    //                               txEvent.keyAsByteBuffer().toByteArray(),
    //                               txEvent.valueAsByteBuffer().toByteArray()
    //                             )
    //
    //                           TransactionEventType.REMOVE ->
    //                             storageTx.remove(
    //                               txEvent.keyAsByteBuffer().toByteArray()
    //                             )
    //                         }
    //
    //                       }
    //
    //                     storageTx.commit()
    //                   }
    //               }
    //         }
    //       }
    //
    //     if (events.isNotEmpty()) {
    //       transactionLog.commitRead(events.map{ it.first })
    //     }
    //
    //   } while (events.isNotEmpty())
    // }
  }
}
