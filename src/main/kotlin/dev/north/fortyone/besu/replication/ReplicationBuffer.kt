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

package dev.north.fortyone.besu.replication

import com.google.flatbuffers.FlatBufferBuilder
import dev.north.fortyone.besu.ext.toBytes
import dev.north.fortyone.besu.ext.toLong
import dev.north.fortyone.besu.replication.fb.ReplicationEvent
import dev.north.fortyone.besu.replication.fb.ReplicationEventType
import dev.north.fortyone.besu.replication.fb.Transaction
import dev.north.fortyone.besu.replication.fb.TransactionEvent
import dev.north.fortyone.besu.replication.fb.TransactionEventType
import dev.north.fortyone.besu.services.PutEvent
import dev.north.fortyone.besu.services.RemoveEvent
import dev.north.fortyone.besu.services.StorageEventsListener
import dev.north.fortyone.besu.services.StorageTransaction
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.apache.logging.log4j.LogManager
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class ReplicationBuffer(
  private val storage: KeyValueStorage
) : StorageEventsListener, Closeable {

  private val log = LogManager.getLogger(ReplicationBuffer::class.java)

  private val readPointerKey = 1L.toBytes()
  private val writePointerKey = 2L.toBytes()

  private var writeJobs = ConcurrentHashMap<Long, CompletableJob>()

  private fun readPointer() = storage
    .get(readPointerKey)
    .map { it.toLong() }
    .orElse(2L)

  private fun writePointer() = storage
    .get(writePointerKey)
    .map { it.toLong() }
    .orElse(3L)

  override fun onTransaction(transaction: StorageTransaction): Job =
    with(storage.startTransaction()) {

      val sequenceId = writePointer()
      val nextSequenceId = sequenceId + 1

      // write to buffer storage
      put(sequenceId.toBytes(), serialize(transaction))
      put(writePointerKey, nextSequenceId.toBytes())
      commit()

      // add a write job for indicating successful replication later
      val job = Job()
      writeJobs[sequenceId] = job

      // return job
      job
    }

  fun read(size: Int): List<Pair<Long, ByteArray>> {

    val readPointer = readPointer() + 1L
    val writePointer = writePointer()

    val range = readPointer.until(readPointer + size)
      .let { initial ->
        if (initial.last >= writePointer)
          readPointer.until(writePointer)
        else
          initial
      }

    log.trace("Read attempt. Range = {}", range)

    return range
      .map { key ->
        storage
          .get(key.toBytes())
          .get()
          .let { Pair(key, it) }
      }
  }

  fun markAsReplicated(keys: List<Long>): Unit =
    with(storage.startTransaction()) {

      // remove from storage
      keys.forEach { key -> remove(key.toBytes()) }
      put(readPointerKey, keys.last().toBytes())
      commit()

      // mark the jobs as complete
      keys.forEach { key ->

        // it's possible after a restart that the jobs are no longer in memory so this is conditional
        writeJobs[key]?.complete()

        // remove from the map
        writeJobs - key
      }
    }

  private fun serialize(transaction: StorageTransaction): ByteArray =
    with(transaction) {
      FlatBufferBuilder()
        .let { fb ->
          val eventsOffset = storageEvents
            .map { event ->
              when (event) {
                is PutEvent -> Pair(
                  fb.createByteVector(event.key),
                  fb.createByteVector(event.value)
                ).let { (keyOffset, valueOffset) ->
                  TransactionEvent.startTransactionEvent(fb)
                  TransactionEvent.addType(fb, TransactionEventType.PUT)
                  TransactionEvent.addKey(fb, keyOffset)
                  TransactionEvent.addValue(fb, valueOffset)
                  TransactionEvent.endTransactionEvent(fb)
                }
                is RemoveEvent -> fb
                  .createByteVector(event.key)
                  .let { keyOffset ->
                    TransactionEvent.startTransactionEvent(fb)
                    TransactionEvent.addType(fb, TransactionEventType.REMOVE)
                    TransactionEvent.addKey(fb, keyOffset)
                    TransactionEvent.endTransactionEvent(fb)
                  }
                else -> throw IllegalStateException()
              }
            }.let { eventIndices -> Transaction.createEventsVector(fb, eventIndices.toIntArray()) }

          Transaction.startTransaction(fb)
          Transaction.addEvents(fb, eventsOffset)
          val txOffset = Transaction.endTransaction(fb)

          val factoryNameOffset = fb.createString(factoryName)
          val segmentOffset = fb.createByteVector(segment.id)

          ReplicationEvent.startReplicationEvent(fb)
          ReplicationEvent.addType(fb, ReplicationEventType.TRANSACTION)
          ReplicationEvent.addFactoryName(fb, factoryNameOffset)
          ReplicationEvent.addSegmentId(fb, segmentOffset)
          ReplicationEvent.addTransaction(fb, txOffset)
          val rootOffset = ReplicationEvent.endReplicationEvent(fb)

          fb.finish(rootOffset)
          fb.sizedByteArray()
        }
    }

  override fun close() {
    storage.close()
  }
}
