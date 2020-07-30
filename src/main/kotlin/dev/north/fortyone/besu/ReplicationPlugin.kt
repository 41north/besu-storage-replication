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

package dev.north.fortyone.besu

import dev.north.fortyone.besu.commands.BesuCommandMixin
import dev.north.fortyone.besu.ext.asPluginContext
import dev.north.fortyone.besu.ext.cliOptions
import dev.north.fortyone.besu.ext.replicationManager
import dev.north.fortyone.besu.ext.storageService
import dev.north.fortyone.besu.replication.TransactionLogProvider
import dev.north.fortyone.besu.services.DefaultReplicationManager
import dev.north.fortyone.besu.services.ReplicationManager
import dev.north.fortyone.besu.storage.InterceptingKeyValueStorageFactory
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import org.hyperledger.besu.plugin.BesuContext
import org.hyperledger.besu.plugin.BesuPlugin
import kotlin.coroutines.CoroutineContext

class ReplicationPlugin(
  override val coroutineContext: CoroutineContext = Dispatchers.IO
) : BesuPlugin, CoroutineScope {

  companion object {
    const val name = "replication"
    const val cliPrefix = "--plugin-$name-"

    private val log = LogManager.getLogger(ReplicationPlugin::class.java)
  }

  private val command = BesuCommandMixin()

  private lateinit var context: BesuContext
  private lateinit var replicationJob: Job

  override fun register(context: BesuContext) =
    context.run {
      // capture reference to context
      this@ReplicationPlugin.context = context

      registerCli(this)
      registerStorage(this)
    }

  private fun registerCli(context: BesuContext) =
    // register our cli options and sub commands
    context.run {
      cliOptions()
        .addPicoCLIOptions(ReplicationPlugin.name, command)
    }

  private fun registerStorage(context: BesuContext) =
    context.asPluginContext()
      .run {

        // register the replication manager service

        val replicationManager = TransactionLogProvider
          .instanceFor(command)
          .let { txLog -> DefaultReplicationManager(txLog) }
          .also { replicationManager ->
            addService(ReplicationManager::class.java, replicationManager)
          }

        // register the intercepting storage factory, wrapping rocksdb
        // TODO privacy storage?

        storageService()
          .run {

            val rocksDbFactory = getByName("rocksdb")
              .orElseThrow { IllegalStateException("RocksDB storage factory not found") }

            registerKeyValueStorage(
              InterceptingKeyValueStorageFactory(
                rocksDbFactory, replicationManager
              )
            )
          }

      }

  @Suppress("ThrowableNotThrown")
  override fun start() =
    command
      .takeIf { it.replicationEnabled }
      .run {

        context.run {

          log.info("Initialising replication manager")
          replicationManager().initialise(context)

          log.info("Starting replication thread")

          replicationJob = launch {

            try {
              replicationManager().run()
            } catch (ex: Exception) {
              // TODO improve
              ex.printStackTrace()
            } finally {
              withContext(NonCancellable) {
                replicationManager().close()
              }
            }
          }
        }

      }

  override fun stop() {
    if (!command.replicationEnabled) return
    runBlocking { replicationJob.cancelAndJoin() }
  }
}
