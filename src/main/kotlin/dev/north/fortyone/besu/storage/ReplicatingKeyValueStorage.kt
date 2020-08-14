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

package dev.north.fortyone.besu.storage

import dev.north.fortyone.besu.services.ClearEvent
import dev.north.fortyone.besu.services.PutEvent
import dev.north.fortyone.besu.services.RemoveEvent
import dev.north.fortyone.besu.services.StorageEvent
import dev.north.fortyone.besu.services.StorageEventsListener
import dev.north.fortyone.besu.services.StorageTransaction
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import java.util.Optional
import java.util.function.Predicate
import java.util.stream.Stream

class ReplicatingKeyValueStorageTransaction(
  private val factoryName: String,
  private val segment: SegmentIdentifier,
  private val delegate: KeyValueStorageTransaction,
  private val listener: StorageEventsListener
) : KeyValueStorageTransaction {

  private var events = mutableListOf<StorageEvent>()

  override fun put(key: ByteArray, value: ByteArray) {
    delegate.put(key, value)
    events.add(PutEvent(key, value))
  }

  override fun remove(key: ByteArray) {
    delegate.remove(key)
    events.add(RemoveEvent(key))
  }

  override fun commit() =
    with(events) {
      listener.onTransaction(StorageTransaction(factoryName, segment, this))
      delegate.commit()
    }

  override fun rollback() {
    delegate.rollback()
  }
}

class InterceptingKeyValueStorage(
  private val factoryName: String,
  private val segment: SegmentIdentifier,
  private val underlyingStorage: KeyValueStorage,
  private val listener: StorageEventsListener
) : KeyValueStorage {

  override fun containsKey(key: ByteArray): Boolean =
    underlyingStorage.containsKey(key)

  override fun getAllKeysThat(returnCondition: Predicate<ByteArray>): MutableSet<ByteArray> =
    underlyingStorage.getAllKeysThat(returnCondition)

  override fun get(key: ByteArray): Optional<ByteArray> =
    underlyingStorage.get(key)

  override fun clear() {
    listener.onTransaction(
      StorageTransaction(factoryName, segment, listOf(ClearEvent))
    ).also {
      underlyingStorage.clear()
    }
  }

  override fun startTransaction(): KeyValueStorageTransaction =
    ReplicatingKeyValueStorageTransaction(
      factoryName,
      segment,
      underlyingStorage.startTransaction(),
      listener
    )

  override fun streamKeys(): Stream<ByteArray> =
    underlyingStorage.streamKeys()

  override fun tryDelete(key: ByteArray): Boolean =
    // TODO determine scope of edge case here
    with(underlyingStorage) {
      tryDelete(key)
    }.also { deleted ->
      if (deleted) listener.onTransaction(
        StorageTransaction(factoryName, segment, listOf(RemoveEvent(key)))
      )
    }

  override fun close() {
    underlyingStorage.close()
  }
}
