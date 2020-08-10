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

import dev.north.fortyone.besu.ext.reflektField
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.services.BesuConfigurationImpl
import org.hyperledger.besu.services.BesuPluginContextImpl
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec
import java.nio.file.Path
import java.util.function.Supplier

@Command(
  name = "replication",
  subcommands = [
    ReplicationRestoreCommand::class,
    ReplicationExportCommand::class
  ]
)
class ReplicationSubCommand : Runnable {

  @ParentCommand
  // cannot set this type to BesuCommand as PicoCli gets confused about the parent being the mixin
  lateinit var parentCommand: Any

  // need to use a getter as the parent command is a lateinit
  val besuCommand: BesuCommand
    get() = parentCommand as BesuCommand

  val dataPath: Path
    get() = reflektField<Path>(besuCommand, "dataPath").toAbsolutePath()

  val storageService: StorageService
    get() = reflektField(besuCommand, "storageService")

  val metricsSystem: ObservableMetricsSystem
    get() = reflektField<Supplier<ObservableMetricsSystem>>(besuCommand, "metricsSystem").get()

  val keyValueStorageName: String
    get() = reflektField(besuCommand, "keyValueStorageName")

  val pluginCommonConfig: BesuConfiguration
    get() = BesuConfigurationImpl(dataPath, dataPath.resolve(BesuController.DATABASE_PATH))

  val pluginContext: BesuPluginContextImpl
    get() = reflektField<BesuPluginContextImpl>(besuCommand, "besuPluginContext")
      .apply {
        addService(BesuConfiguration::class.java, pluginCommonConfig)
        addService(MetricsSystem::class.java, metricsSystem)
      }

  val storageSegments = listOf(
    KeyValueSegmentIdentifier.BLOCKCHAIN,
    KeyValueSegmentIdentifier.WORLD_STATE
  )

  @Spec
  private lateinit var spec: CommandSpec

  override fun run() {
    // TODO usage message
  }
}
