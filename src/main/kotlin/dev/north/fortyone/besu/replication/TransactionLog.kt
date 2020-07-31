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

import dev.north.fortyone.besu.commands.BesuCommandMixin
import kotlinx.coroutines.Job
import java.io.Closeable
import java.lang.IllegalArgumentException
import java.time.Duration

enum class TransactionLogProvider {
  KAFKA;

  companion object {

    fun instanceFor(command: BesuCommandMixin): TransactionLog {

      val enum = values()
        .find { it.name == command.txLogProvider.toUpperCase() }
        ?: throw IllegalArgumentException("Invalid transaction log provider name: ${command.txLogProvider}")

      return when (enum) {
        KAFKA -> KafkaTransactionLog(command)
      }
    }
  }
}

interface TransactionLog : Closeable {

  val provider: TransactionLogProvider

  suspend fun write(entries: List<Pair<Long, ByteArray>>): List<Job>

  suspend fun read(timeout: Duration): List<Pair<Long, ByteArray>>

  suspend fun commitRead(keys: List<Long>)
}
