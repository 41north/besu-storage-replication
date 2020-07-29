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

import dev.north.fortyone.besu.services.StorageEventsListener
import org.apache.logging.log4j.LogManager
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier

enum class ReplicationSegmentIdentifier(
  private val id: ByteArray
) : SegmentIdentifier {

  DEFAULT(ByteArray(1).apply { this[0] = 0x1 });

  override fun getName(): String = name.toLowerCase()
  override fun getId(): ByteArray = id
}

class InterceptingKeyValueStorageFactory(
  private val delegate: KeyValueStorageFactory,
  private val listener: StorageEventsListener
) : KeyValueStorageFactory {

  private val log = LogManager.getLogger(InterceptingKeyValueStorageFactory::class.java)

  override fun getName(): String =
    "intercepting-storage"

  override fun isSegmentIsolationSupported(): Boolean =
    delegate.isSegmentIsolationSupported

  override fun create(
    segment: SegmentIdentifier,
    configuration: BesuConfiguration,
    metricsSystem: MetricsSystem
  ): KeyValueStorage =
    InterceptingKeyValueStorage(
      delegate.name,
      segment,
      delegate.create(segment, configuration, metricsSystem),
      listener
    ).also {
      log.info("Created key / value storage. Factory name = {}, segment = {}", delegate.name, segment)
    }

  override fun close() {
    delegate.close()
  }
}
