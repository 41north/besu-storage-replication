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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.hyperledger.besu.plugin.BesuContext
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import java.io.Closeable
import java.nio.file.Path
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

abstract class StorageEvent

class PutEvent(val key: ByteArray, val value: ByteArray) : StorageEvent()
class RemoveEvent(val key: ByteArray) : StorageEvent()
class ClearEvent : StorageEvent()

interface StorageEventsListener {
  fun onEvents(factoryName: String, segment: SegmentIdentifier, events: List<StorageEvent>)
}

interface ReplicationManager : StorageEventsListener, Closeable {

  fun initialise(context: BesuContext)

  suspend fun run()
}

class DefaultReplicationManager(
  private val transactionLog: TransactionLog
) : ReplicationManager {

  private val log = LogManager.getLogger(DefaultReplicationManager::class.java)

  private lateinit var replicationBuffer: ReplicationBuffer

  private var startupBuffer = emptyList<Triple<String, SegmentIdentifier, List<StorageEvent>>>()

  @Volatile
  private var initialised = false

  @Suppress("ThrowableNotThrown")
  override fun initialise(context: BesuContext) {

    val rocksdbStorageFactory = context
      .storageService()
      .getByName("rocksdb")
      .orElseThrow { IllegalStateException("Could not find rocksdb storage factory") }

    val besuConfig = context.besuConfiguration()

    val replicationBesuConfig = object : BesuConfiguration {
      override fun getStoragePath(): Path =
        besuConfig.storagePath.resolve("replication")

      override fun getDataPath(): Path =
        besuConfig.dataPath.resolve("replication")
    }

    val replicationStorage = rocksdbStorageFactory.create(
      ReplicationSegmentIdentifier.DEFAULT,
      replicationBesuConfig,
      context.metricsSystem()
    )

    replicationBuffer = ReplicationBuffer(replicationStorage)

    // drain the startup buffer

    startupBuffer
      .forEach { (factoryName, segment, events) ->
        replicationBuffer.onEvents(factoryName, segment, events)
      }

    startupBuffer = emptyList()

    // mark as initialised

    initialised = true
  }

  override fun onEvents(factoryName: String, segment: SegmentIdentifier, events: List<StorageEvent>) =
    if (initialised)
      replicationBuffer.onEvents(factoryName, segment, events)
    else
      startupBuffer = startupBuffer + Triple(factoryName, segment, events)


  @ExperimentalTime
  @InternalCoroutinesApi
  override suspend fun run() {

    while (isActive) {

      val entries = replicationBuffer.read(1024)

      if (entries.isNotEmpty()) {

        withContext(Dispatchers.IO) {

          // TODO error handling

          launch { transactionLog.write(entries) }.join()
          replicationBuffer.remove(entries.map { it.first })

        }
      }

      if (entries.isEmpty()) {
        log.trace("Waiting 1 second before attempting replication")
        delay(1.seconds)
      }
    }
  }

  override fun close() {
    transactionLog.close()
  }
}