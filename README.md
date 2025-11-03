# MVP-ORAM: Multi-Version Path ORAM library
MVP-ORAM is an implementation of tree-based ORAM protocol that supports multiple clients and Byzantine servers.
This protocol is implemented as a service on top of [COBRA](https://github.com/bft-smart/cobra) and [BFT-SMaRt](https://github.com/bft-smart/library) library.

## Requirements
The MVP-ORAM library is implemented in Java and uses Gradle to compile and package the code.
The current version of the library was tested using Java 11.

## Compilation and Packaging
First, clone this repository.
Now inside ``MVPORAM`` folder, execute the following commands to compile and package the code:
``./gradlew installDist``.
The required jar files and default configurations files will be available in ``build/install/MVPORAM`` folder.

**Local testing:** Create multiple copies of the ``build/install/MVPORAM`` folder, one for each server and each client.

**Distributed testing:** Copy the ``build/install/MVPORAM`` folder into machines were the servers and clients will be executed.

## Usage
Since MVP-ORAM is implemented as a service on top of COBRA and BFT-SMaRt, first configure those libraries following instructions presented in their repositories.
For default usage, configure at least ``config/hosts.config`` with the information about the servers' IPs.

**TIP:** Reconfigure the system before compiling and packaging. This way, you don't have to configure multiple replicas.

**Note:** The following commands considers the Linux operating system.
For the Windows operating system, use script ``smartrun.cmd`` instead of ``./smartrun.sh``.

***Running the throughput and latency benchmark***

Execute the following command across all servers from within the ``MVPORAM`` folder:
```
./smartrun.sh oram.server.ORAMServer <max concurrent clients> <process id>
```
Where ``<max concurrent clients>`` is the maximum number concurrent clients that can perform ``access`` (see Optimizations section) and ``<process id>`` is a unique identifier for each server starting from 0.
Once all servers are ready, i.e., they print ``Ready to process operations``, the clients can be launched by executing the following command.

```
./smartrun.sh oram.benchmark.MultiServerBenchmarkClient <initialClientId> <nClients> <nRequests> <treeHeight> <bucketSize> <blockSize> <zipf parameter> <isMeasurementLeader>
```
Where:
- ``<initialClientId>`` is the initial client identifier, e.g., 100;
- ``<nClients>`` is the number of clients, e.g., 10;
- ``<nRequests>`` is the number of requests per client, e.g., 10000;
- ``<treeHeight>`` is the height of the ORAM tree, e.g., 15;
- ``<bucketSize>`` is the size of the ORAM tree's bucket, e.g., 4;
- ``<blockSize>`` is the size of the blocks stored in the ORAM tree, e.g., 4096;
- ``<zipf parameter>`` is the Zipfian distribution parameter, e.g., 1.0;
- ``<isMeasurementLeader>`` is a boolean value that indicates if the client should print the latency values. Use ``true`` to print the latency values and ``false`` otherwise.

***Interpreting the throughput and latency results***

When clients continuously send the requests, servers will print the throughput information
every two seconds and client will print the access latency of each request in ms.

## Benchmarking
MVP-ORAM uses a custom benchmarking tool ([BenchmarkExecutor](https://github.com/rvassantlal/BenchmarkExecutorV2)) to automate the execution and collection of measurements.
This tool allows to execute experiments on multiple machines, while controlling it from a single machine.

The experiments are configured using a configuration file store in ``config/benchmark.config``.
To run the benchmark, first execute the following command to start the ``controller``:
```
    ./smartrun.sh controller.BenchmarkControllerStartup <benchmark config file path>
```
Then start the ``workers`` on each machine that will run the experiments:
```
    ./smartrun.sh oram.benchmark.BenchmarkWorker <controller ip> <controller port>
```


## Optimizations
MVP-ORAM integrates the following optimizations:
- Optimization 1 (Consensus): instead of sending ``evict`` request as ordered requests, this optimization sends the ``evict`` data as unordered request and send reference to that data as ordered request.
- Optimization 2 (Server response): instead of all servers sending the full response to the client, only the leader server sends the full response and other servers send hash of the response.
- Optimization 3 (Bound concurrency): bounds the number of concurrent clients that can perform ``access``.


## Single server MVP-ORAM implementation
You can check performance of MVP-ORAM without the cost of replication using the single server implementation: [SingleServerORAM](https://anonymous.4open.science/r/SingleServerORAM).

***Feel free to contact us if you have any questions! :)***