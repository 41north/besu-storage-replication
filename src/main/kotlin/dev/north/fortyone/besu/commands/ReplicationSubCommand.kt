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

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(
  name = "replication",
  subcommands = [
    ReplicationRestoreCommand::class
  ]
)
class ReplicationSubCommand : Runnable {

  @ParentCommand
  // cannot set this type to BesuCommand as PicoCli gets confused about the parent being the mixin
  public lateinit var parentCommand: Any

  @Spec
  private lateinit var spec: CommandSpec

  override fun run() {
    // TODO usage message
  }
}
