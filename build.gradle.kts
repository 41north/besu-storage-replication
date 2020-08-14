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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

if (!JavaVersion.current().isJava11Compatible) {
  throw GradleException("Java 11 or later is required to build this project. Detected version ${JavaVersion.current()}")
}

plugins {
  `java-library`
  `maven-publish`
  distribution
  id("org.jetbrains.kotlin.jvm") version "1.3.72"
  id("org.jlleitschuh.gradle.ktlint") version "9.3.0"
  id("org.jlleitschuh.gradle.ktlint-idea") version "9.3.0"
  id("com.github.johnrengelman.shadow") version "6.0.0"
  id("com.github.ben-manes.versions") version "0.29.0"
  id("me.qoomon.git-versioning") version "3.0.0"
  id("dev.north.fortyone.flatbuffers") version "0.1.0"
  id("dev.north.fortyone.intellij.run.generator") version "0.2.0"
}

version = "0.0.0-SNAPSHOT"
group = "dev.north.fortyone"

repositories {
  jcenter()
  maven(url = "https://packages.confluent.io/maven/")
  maven(url = "https://dl.bintray.com/hyperledger-org/besu-repo/")
  maven(url = "https://dl.bintray.com/consensys/pegasys-repo/")
  maven(url = "https://repo.spring.io/libs-release")
}

dependencies {

  implementation(kotlin("stdlib"))
  implementation(kotlin("reflect"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:_")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:_")

  implementation("com.google.flatbuffers:flatbuffers-java:_")

  implementation("org.hyperledger.besu.internal:besu:_")
  implementation("org.hyperledger.besu.internal:core:_")
  implementation("org.hyperledger.besu.internal:eth:_")
  implementation("org.hyperledger.besu.internal:api:_")
  implementation("org.hyperledger.besu.internal:config:_")
  implementation("org.hyperledger.besu.internal:metrics-core:_")
  implementation("org.hyperledger.besu.internal:kvstore:_")
  implementation("org.hyperledger.besu.internal:plugins-rocksdb:_")

  implementation("info.picocli:picocli:_")

  implementation("org.koin:koin-core:_")

  implementation("org.apache.kafka:kafka-clients:_")
  implementation("redis.clients:jedis:_")

  runtimeOnly("org.apache.logging.log4j:log4j-core:_")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:_")
}

tasks {
  val javaVersion = "11"

  jar {
    enabled = false
  }

  val distZip: Zip by container
  distZip.apply {
    doFirst { delete { fileTree(Pair("build/distributions", "*.zip")) } }
  }

  val distTar: Tar by container
  distTar.apply {
    doFirst { delete { fileTree(Pair("build/distributions", "*.tar.gz")) } }
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
  }

  withType<KotlinCompile>().all {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    kotlinOptions.jvmTarget = javaVersion
  }

  withType<JavaCompile> {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }

  withType<DependencyUpdatesTask> {
    fun isNonStable(version: String): Boolean {
      val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
      val regex = "^[0-9,.v-]+(-r)?$".toRegex()
      val isStable = stableKeyword || regex.matches(version)
      return isStable.not()
    }

    // Reject all non stable versions
    rejectVersionIf {
      isNonStable(candidate.version)
    }
  }

  withType<ShadowJar> {
    archiveBaseName.set("besu-storage-replication")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    minimize()

    dependencies {
      listOf(
        "besu",
        "core",
        "eth",
        "api",
        "config",
        "metrics-core"
      ).forEach { dep ->
        exclude(dependency("org.hyperledger.besu.internal:$dep"))
      }
      exclude(dependency("info.picocli:picocli"))
    }
  }
}

ktlint {
  debug.set(false)
  verbose.set(true)
  outputToConsole.set(true)
  ignoreFailures.set(false)
  reporters {
    reporter(ReporterType.PLAIN)
  }
  filter {
    exclude("**/generated/**")
    exclude("**/flatbuffers/**")
  }
}

flatbuffers {
  language.set(dev.north.fortyone.gradle.flatbuffers.Language.JAVA)
  inputSources.set(listOf("replication.fbs"))
  extraFlatcArgs.set("flatc --java -o /output/ -I /input --gen-all /input/replication.fbs")
}

intellijRunGenerator {
  tasksDefinitions.set(File("intellij-run-configs.yaml"))
  tasksDefinitionOutput.set(File(".idea/runConfigurations"))
}

distributions {
  main {
    contents {
      from("LICENSE") { into("") }
      from("README.md") { into("") }
      from("CHANGELOG.md") { into("") }
      from("build/libs") { into("plugins") }
    }
  }
}
