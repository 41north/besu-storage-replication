<h1 align="center">⚡ Besu Storage Replication Plugin ⚡</h1>

<p align="center">Replication of your Besu key/value storage for incremental backup and read only replicas.</p>

## 💡 Introduction

This plugin allows you to export/restore your Besu key/value storage to other supported systems. This approach would enable us to achieve the following:

- Creation of incremental backups for data archiving.
- Restoration of backups with a known good state that avoids resynchronization and validation from the network for the same data.
- Creation of a Besu cluster where one node acts as a master (writing/updating) and other nodes serve as read-only replicas.

## 🙈 Usage

Build the plugin:

```sh
./gradlew assemble
```

The compiled `jar` can be found inside the following `.build/distributions` folder.

Follow these [recommendations on how to run a Besu plugin](https://besu.hyperledger.org/en/stable/Concepts/Plugins/) if you need more information.

## 🧑‍💻 Development

Prerequisites:

- [IntelliJ](https://www.jetbrains.com/idea/)
- [Java 11](https://jdk.java.net/11/)

First, clone this repository:

```sh
git clone git@github.com:41north/besu-storage-replication.git
```

Open IntelliJ, load the project and type the following in the terminal:

```sh
./gradlew generateIntellijRunConfigs
```

That will generate [Intellij's Run Configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html) from the [`intellij-run-configs.yaml`](./intellij-run-configs.yaml) file with several useful commands (feel free to customize it as necessary).

After the run configs are generated, next type the following in the terminal:

```sh
docker-compose up
```

That will start Kafka as a backup mechanism. Next is to start Besu with one network by launching one of the following run config:

- `BESU | Dev > Run`
- `BESU | Ropsten > Run`
- `BESU | Mainnet > Run`

Leave the client running as much as you want. It will auto backup it's storage to Kafka.

Stop the client and remove the storage folder where Besu stores the data. Next, execute the following run config: `Besu | Replication ${network} Restore > Run` (where `${network}` is the network you decided to execute).

Voilá! You have restored the state of your node.

## 💻 Contribute

We welcome any kind of contribution or support to this project but before to do so:

* Make sure you have read the [contribution guide](/.github/CONTRIBUTING.md) for more details on how to submit a good PR (pull request).

Also, we are not only limited to technical contributions. Things that make us happy are:

* Add a [GitHub Star](https://github.com/41north/besu-storage-replication/stargazers) to the project.
* Tweet about this project.
* Write a review or tutorial.

## Other Gradle plugins

We have published other Besu plugins:

- [Besu Exflo](https://github.com/41north/besu-exflo).
- [Besu Plugin Starter](https://github.com/41north/besu-plugin-starter).

Also, have a look at our [Awesome Besu](https://github.com/41north/awesome-besu) list to find more useful stuff!

## 📬 Get in touch

`Besu Storage Replication Plugin` has been developed initially by [°41North](https://41north.dev). 

If you think this project would be useful for your use case and want to talk more about it, you can reach out to us via our contact form or by sending an email to `hello@41north.dev`. We try to respond within 48 hours and look forward to hearing from you.

## ✍️ License

`Besu Plugin Starter` is free and open-source software licensed under the [Apache 2.0 License](./LICENSE).
