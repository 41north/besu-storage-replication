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

import dev.north.fortyone.besu.ReplicationPlugin.Companion.cliPrefix
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

@Command(
  subcommands = [ReplicationSubCommand::class]
)
class BesuCommandMixin {

  @CommandLine.Option(
    names = ["$cliPrefix-enabled"],
    paramLabel = "<BOOLEAN>",
    defaultValue = "false",
    description = ["Enable replication"]
  )
  var replicationEnabled: Boolean = false

  @CommandLine.Option(
    names = ["$cliPrefix-tx-log-provider"],
    paramLabel = "<STRING>",
    defaultValue = "kafka",
    description = ["The transaction log implementation to use"]
  )
  var txLogProvider: String = "kafka"

  @CommandLine.Option(
    names = ["$cliPrefix-storage-name"],
    paramLabel = "<STRING>",
    defaultValue = "rocksdb",
    description = ["The underlying storage factory that should be replicated"]
  )
  var storageName: String = "rocksdb"

  @CommandLine.Option(
    names = ["$cliPrefix-buffer-storage-name"],
    paramLabel = "<STRING>",
    defaultValue = "rocksdb",
    description = ["The storage factory to be used for the replication buffer"]
  )
  var bufferStorageName: String = "rocksdb"

  // kafka

  @CommandLine.Option(
    names = ["$cliPrefix-kafka-bootstrap-servers"],
    paramLabel = "<STRING>",
    defaultValue = "kafka:9092",
    description = ["Bootstrap servers for connecting to the kafka cluster"]
  )
  var kafkaBootstrapServers: String = "kafka:9092"

  @CommandLine.Option(
    names = ["$cliPrefix-kafka-client-id"],
    paramLabel = "<STRING>",
    defaultValue = "besu",
    description = ["Unique client id for the kafka connection"]
  )
  var kafkaClientId: String = "besu"

  @CommandLine.Option(
    names = ["$cliPrefix-kafka-group-id"],
    paramLabel = "<STRING>",
    defaultValue = "besu",
    description = ["Name of the kafka consumer group when consuming from the replication topic"]
  )
  var kafkaGroupId: String = "besu"

  @CommandLine.Option(
    names = ["$cliPrefix-kafka-replication-topic"],
    paramLabel = "<STRING>",
    defaultValue = "besu_transaction_log",
    description = ["Name of the kafka topic to use for the transaction log"]
  )
  var kafkaReplicationTopic: String = "besu_transaction_log"

  @Spec
  private lateinit var spec: CommandSpec
}
