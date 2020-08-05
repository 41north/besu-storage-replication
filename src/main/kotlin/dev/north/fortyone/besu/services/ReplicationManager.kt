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

package dev.north.fortyone.besu.services

import dev.north.fortyone.besu.ext.besuConfiguration
import dev.north.fortyone.besu.ext.metricsSystem
import dev.north.fortyone.besu.ext.storageService
import dev.north.fortyone.besu.replication.ReplicationBuffer
import dev.north.fortyone.besu.replication.TransactionLog
import dev.north.fortyone.besu.storage.ReplicationSegmentIdentifier
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import org.apache.logging.log4j.LogManager
import org.hyperledger.besu.plugin.BesuContext
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import java.io.Closeable
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

sealed class StorageEvent

class PutEvent(val key: ByteArray, val value: ByteArray) : StorageEvent()
class RemoveEvent(val key: ByteArray) : StorageEvent()
object ClearEvent : StorageEvent()

data class StorageTransaction(
  val factoryName: String,
  val segment: SegmentIdentifier,
  val storageEvents: List<StorageEvent>
)

interface StorageEventsListener {
  fun onTransaction(transaction: StorageTransaction): Job
}

interface ReplicationManager : StorageEventsListener, Closeable {

  val storageFactory: KeyValueStorageFactory
  val transactionLog: TransactionLog

  fun initialise(context: BesuContext)
  suspend fun run()
}

class DefaultReplicationManager(
  override val storageFactory: KeyValueStorageFactory,
  override val transactionLog: TransactionLog
) : ReplicationManager {

  private val log = LogManager.getLogger(DefaultReplicationManager::class.java)

  private lateinit var replicationBuffer: ReplicationBuffer

  private var startupTransactionBuffer = emptyList<Pair<StorageTransaction, CompletableJob>>()

  @Volatile
  private var initialised = false

  @Suppress("ThrowableNotThrown")
  override fun initialise(context: BesuContext) =
    with(context) {

      val replicationStorage = storageFactory.create(
        ReplicationSegmentIdentifier.DEFAULT,
        besuConfiguration(),
        context.metricsSystem()
      )

      // drain the startup buffer
      replicationBuffer = ReplicationBuffer(replicationStorage)
        .apply {

          startupTransactionBuffer.forEach { (tx, job) ->
            onTransaction(tx)
              .invokeOnCompletion { exception ->
                if (exception == null)
                  job.complete()
                else
                  job.completeExceptionally(exception)
              }

          }

          // clear the startup buffer
          startupTransactionBuffer = emptyList()
        }

      // mark as initialised

      initialised = true
    }

  override fun onTransaction(transaction: StorageTransaction) =
    if (initialised)
      replicationBuffer.onTransaction(transaction)
    else
      Job().apply {
        startupTransactionBuffer = startupTransactionBuffer + Pair(transaction, this)
      }

  @ExperimentalTime
  @InternalCoroutinesApi
  override suspend fun run() {

    try {
      while (isActive) {

        replicationBuffer.run {

          val entries = read(1024)

          if (entries.isNotEmpty()) {
            // TODO error handling
            transactionLog.write(entries).forEach { it.join() }
            replicationBuffer.markAsReplicated(entries.map { it.first })
          }

          if (entries.isEmpty()) {
            log.trace("Waiting 1 second before attempting replication")
            delay(1.seconds)
          }

        }
      }
    } catch (ex: Exception) {
      log.error("Replication failure", ex)
    } finally {
      close()
    }
  }

  override fun close() {
    transactionLog.close()
  }
}
