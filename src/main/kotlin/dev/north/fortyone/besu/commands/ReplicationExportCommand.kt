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

import com.google.common.collect.Iterators
import dev.north.fortyone.besu.ext.getService
import dev.north.fortyone.besu.services.PutEvent
import dev.north.fortyone.besu.services.ReplicationManager
import dev.north.fortyone.besu.services.StorageTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(
  name = "export",
  description = ["Export your Besu key/value storage to supported external services."]
)
class ReplicationExportCommand : Runnable {

  companion object {
    private val logger = LoggerFactory.getLogger(ReplicationExportCommand::class.java)
  }

  @ParentCommand
  private lateinit var parentCommand: ReplicationSubCommand

  @Spec
  private lateinit var spec: CommandSpec

  private val keyBatchSize = 128

  private val maxInFlightTransactions = 1024 * 10

  override fun run() {

    with(parentCommand) {

      // initialise plugins
      pluginContext.startPlugins()

      // initialise and clear storage
      val storageBySegment = initialiseStorage(this)

      // perform the export
      runBlocking { export(this@with, storageBySegment) }
    }
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
          }
        )
        .toMap()
    }

  private suspend fun export(
    parentCommand: ReplicationSubCommand,
    storageBySegment: Map<SegmentIdentifier, KeyValueStorage>
  ) = with(parentCommand) {

    val replicationManager = pluginContext.getService<ReplicationManager>()

    var replicationJobs = emptyList<Job>()

    val tryWaitOnReplicationJobs: suspend (Boolean) -> Unit = { force ->
      if (force || replicationJobs.size == maxInFlightTransactions) {

        replicationJobs
          .also { logger.info("Waiting on {} replication jobs to complete", replicationJobs.size) }
          .forEach { job -> job.join() }
          .run {
            logger.info("{} replication jobs completed", replicationJobs.size)
            replicationJobs = emptyList()
          }
      }
    }

    storageBySegment
      .forEach { (segment, storage) ->

        val keysStream = storage.streamKeys()

        // TODO ensure each batch of events stays under 1 mb to prevent problems with transaction log implementations

        val batchIterator = Iterators.partition(keysStream.iterator(), keyBatchSize)

        do {

          batchIterator.next()
            .also { keys -> logger.debug("Processing {} keys", keys.size) }
            .let { keys -> keys.map { key -> PutEvent(key, storage.get(key).get()) } }
            .let { storageEvents ->
              StorageTransaction(
                keyValueStorageName, segment, storageEvents
              )
            }
            .let { txEvent -> replicationManager.onTransaction(txEvent) }
            .also { replicationJob ->
              replicationJobs = replicationJobs + replicationJob
              tryWaitOnReplicationJobs(false)
            }
        } while (batchIterator.hasNext())
      }

    // wait on any remaining jobs that didn't meet the batch threshold
    tryWaitOnReplicationJobs(true)

    logger.info("Finished export")
  }
}
