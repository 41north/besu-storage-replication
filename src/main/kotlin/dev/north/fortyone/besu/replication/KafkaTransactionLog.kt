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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.logging.log4j.LogManager
import java.time.Duration
import java.util.Properties

class KafkaTransactionLog(
  cliOptions: BesuCommandMixin
) : TransactionLog {

  private val log = LogManager.getLogger(KafkaTransactionLog::class.java)

  override val provider: TransactionLogProvider = TransactionLogProvider.KAFKA

  private val topic = cliOptions.kafkaReplicationTopic

  private val producerProps = Properties()
    .apply {
      put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cliOptions.kafkaBootstrapServers)
      put(ProducerConfig.CLIENT_ID_CONFIG, cliOptions.kafkaClientId)
      put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer::class.java)
      put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
      put(ProducerConfig.ACKS_CONFIG, "all")
      put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1024)
      put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024 * 1024 * 20)
    }

  private val producer: KafkaProducer<Long, ByteArray> by lazy {
    KafkaProducer<Long, ByteArray>(producerProps)
  }

  private val consumerProps = Properties()
    .apply {
      put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cliOptions.kafkaBootstrapServers)
      put(ConsumerConfig.CLIENT_ID_CONFIG, cliOptions.kafkaClientId)
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer::class.java)
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
      put(ConsumerConfig.GROUP_ID_CONFIG, cliOptions.kafkaGroupId)
      put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
      put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1024)
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }

  private val consumer: KafkaConsumer<Long, ByteArray> by lazy {
    KafkaConsumer<Long, ByteArray>(consumerProps)
      .apply {
        this.subscribe(listOf(topic))
      }
  }

  override suspend fun write(entries: List<Pair<Long, ByteArray>>) {
    withContext(Dispatchers.IO) {

      // fire off the batch
      val futures = entries
        .map { (key, value) -> ProducerRecord(topic, key, value) }
        .map { record -> producer.send(record) }

      // wait for completion
      futures.forEach { future -> future.get() }
    }
  }

  override suspend fun read(timeout: Duration): List<Pair<Long, ByteArray>> =
    withContext(Dispatchers.IO) {
      consumer.poll(timeout)
        .map { record -> Pair(record.key(), record.value()) }
    }

  override suspend fun commitRead(keys: List<Long>) {
    withContext(Dispatchers.IO) {
      consumer.commitSync()
      log.info("Read commit: ${keys.last()}")
    }
  }

  override fun close() {
    producer.close()
  }
}