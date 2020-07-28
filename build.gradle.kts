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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
  `java-library`
  `maven-publish`
  distribution
  id("org.jetbrains.kotlin.jvm") version "1.3.72"
  id("org.jlleitschuh.gradle.ktlint") version "9.2.1" apply false
  id("org.jlleitschuh.gradle.ktlint-idea") version "9.2.1" apply true
  id("com.github.johnrengelman.shadow") version "5.2.0" apply true
  id("io.spring.dependency-management") version "1.0.9.RELEASE"
  id("com.github.ben-manes.versions") version "0.28.0"
  id("me.qoomon.git-versioning") version "3.0.0"
  id("dev.north.fortyone.flatbuffers") version "0.1.0"
}

if (!JavaVersion.current().isJava11Compatible) {
  throw GradleException("Java 11 or later is required to build Exflo. Detected version ${JavaVersion.current()}")
}

version = "0.0.0-SNAPSHOT"
group = "dev.north.fortyone"

apply(plugin = "io.spring.dependency-management")
apply(from = "$rootDir/gradle/versions.gradle")
apply(plugin = "org.jlleitschuh.gradle.ktlint")

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

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

  implementation("com.google.flatbuffers:flatbuffers-java")

  implementation("org.hyperledger.besu.internal:besu")
  implementation("org.hyperledger.besu.internal:core")
  implementation("org.hyperledger.besu.internal:eth")
  implementation("org.hyperledger.besu.internal:api")
  implementation("org.hyperledger.besu.internal:config")
  implementation("org.hyperledger.besu.internal:metrics-core")
  implementation("org.hyperledger.besu.internal:kvstore")
  implementation("org.hyperledger.besu.internal:plugins-rocksdb")

  implementation("org.apache.tuweni:tuweni-bytes")
  implementation("org.apache.tuweni:tuweni-units")

  implementation("info.picocli:picocli")

  implementation("org.koin:koin-core")

  implementation("org.apache.kafka:kafka-clients")
  implementation("redis.clients:jedis")

  runtimeOnly("org.apache.logging.log4j:log4j-core")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

tasks {
  withType<KotlinCompile>().all {
    sourceCompatibility = "${JavaVersion.VERSION_11}"
    targetCompatibility = "${JavaVersion.VERSION_11}"
    kotlinOptions.jvmTarget = "${JavaVersion.VERSION_11}"
  }

  withType<JavaCompile> {
    sourceCompatibility = "${JavaVersion.VERSION_11}"
    targetCompatibility = "${JavaVersion.VERSION_11}"
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


val distZip: Zip by project.tasks
distZip.apply {
  dependsOn(":plugin:build")
  doFirst { delete { fileTree(Pair("build/distributions", "*.zip")) } }
}

val distTar: Tar by project.tasks
distTar.apply {
  dependsOn("plugin:build")
  doFirst { delete { fileTree(Pair("build/distributions", "*.tar.gz")) } }
  compression = Compression.GZIP
  archiveExtension.set("tar.gz")
}


distributions {
  main {
    contents {
      from("LICENSE") { into("") }
      from("README.md") { into("") }
      from("CHANGELOG.md") { into("") }
      from("ingestion/postgres/build/libs") { into("plugins") }
    }
  }
}

tasks {

  jar {
    enabled = false
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
}
