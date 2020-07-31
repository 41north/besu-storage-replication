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

import dev.north.fortyone.besu.ext.replicationManager
import dev.north.fortyone.besu.ext.toByteArray
import dev.north.fortyone.besu.ext.toReplicationEvent
import dev.north.fortyone.besu.replication.fb.ReplicationEventType
import dev.north.fortyone.besu.replication.fb.TransactionEventType
import kotlinx.coroutines.runBlocking
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec
import java.lang.IllegalArgumentException
import java.time.Duration

@Command(
  name = "restore"
)
class ReplicationRestoreCommand : Runnable {

  companion object {
    private val logger = LoggerFactory.getLogger(ReplicationRestoreCommand::class.java)
  }

  @ParentCommand
  private lateinit var parentCommand: ReplicationSubCommand

  @Spec
  private lateinit var spec: CommandSpec

  override fun run() {

    with(parentCommand) {

      // initialise plugins
      pluginContext.startPlugins()

      // initialise and clear storage
      val storageBySegment = initialiseStorage(this)

      // perform the restore
      restore(this, storageBySegment)
    }

    logger.info("Finished")
  }

  private fun initialiseStorage(parentCommand: ReplicationSubCommand): Map<SegmentIdentifier, KeyValueStorage> =
    with(parentCommand) {

      val storageFactory = storageService
        .getByName(keyValueStorageName)
        .orElseThrow { throw IllegalArgumentException("Invalid key value storage name: $keyValueStorageName") }

      storageSegments
        .zip(
          storageSegments.map { segment ->
            storageFactory.create(segment, pluginCommonConfig, metricsSystem)
              // clear the storage in preparation for restoration
              .also { storage -> storage.clear() }
          }
        )
        .toMap()
    }

  private fun restore(
    parentCommand: ReplicationSubCommand,
    storageBySegment: Map<SegmentIdentifier, KeyValueStorage>
  ) = with(parentCommand) {

    val transactionLog = parentCommand.pluginContext.replicationManager().transactionLog

    var entries: List<Pair<Long, ByteArray>>

    do {

      entries = runBlocking { transactionLog.read(Duration.ofSeconds(10)) }

      entries
        .map { (_, bytes) -> bytes.toReplicationEvent() }
        .forEach { event ->

          val segmentIdentifierBytes = event.segmentIdAsByteBuffer().toByteArray()

          val segmentIdentifier = KeyValueSegmentIdentifier.values()
            .find { it.id!!.contentEquals(segmentIdentifierBytes) }
            ?: throw Error("Could not determine segment identifier")

          val storage = storageBySegment[segmentIdentifier]
            ?: throw Error("Could not find storage for segment identifier: $segmentIdentifier")

          when (event.type()) {

            ReplicationEventType.CLEAR_ALL -> storage.clear()

            ReplicationEventType.TRANSACTION -> event.transaction()
              .also { eventTx ->

                storage.startTransaction().run {

                  for (eventIdx in 0.until(eventTx.eventsLength())) {

                    val txEvent = eventTx.events(eventIdx)

                    when (txEvent.type()) {

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

        }

      logger.info("Processed {} entries", entries.size)

    } while (entries.isNotEmpty())

  }
}
