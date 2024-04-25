# MVP-ORAM: Multi-Version Path ORAM library
MVP-ORAM is an implementation of tree-based ORAM protocol that supports multiple clients and Byzantine servers.
This protocol is implemented as a service on top of [COBRA](https://github.com/bft-smart/cobra) and [BFT-SMaRt](https://github.com/bft-smart/library) library.

## Limitations
The current implementation does not use the capabilities of COBRA to securely store encryption keys among servers.

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

# Usage
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
Where ``<process id>`` is the maximum number concurrent clients that can perform ``access`` (see Optimizations section) and ``<process id>`` is a unique identifier for each server starting from 0.
Once all servers are ready, i.e., they print ``Ready to process operations``, the clients can be launched by executing the following command.

```
./smartrun.sh oram.benchmark.ORAMBenchmarkClient <initialClientId> <nClients> <nRequests> <position map type: full | triple> <treeHeight> <bucketSize> <blockSize> <isMeasurementLeader>
```
Where:
- ``<initialClientId>`` is the initial client identifier, e.g., 100;
- ``<nClients>`` is the number of clients, e.g., 10;
- ``<nRequests>`` is the number of requests per client, e.g., 10000;
- ``<position map type>`` is the type of position map used by the ORAM protocol. Use ``full`` for full position map and ``triple`` for triple position map.
- ``<treeHeight>`` is the height of the ORAM tree, e.g., 14;
- ``<bucketSize>`` is the size of the ORAM tree's bucket, e.g., 4;
- ``<blockSize>`` is the size of the blocks stored in the ORAM tree, e.g., 4096;
- ``<isMeasurementLeader>`` is a boolean value that indicates if the client should print the latency values. Use ``true`` to print the latency values and ``false`` otherwise.

***Interpreting the throughput and latency results***

When clients continuously send the requests, servers will print the throughput information
every two seconds.
When a client finishes sending the requests, it will print a string containing space-separated
latencies of each request in nanoseconds. For example, you can use this result to compute average latency.

## Optimizations
MVP-ORAM integrates the following optimizations:
- Optimization 1 (Position map): instead of transferring a full position map, this optimizations transfers only the modified entries.
- Optimization 2 (Consensus): instead of sending ``evict`` request as ordered requests, this optimization sends the ``evict`` data as unordered request and send reference to that data as ordered request.
- Optimization 3 (Server response): instead of all servers sending the full response to the client, only the leader server sends the full response and other servers send hash of the response.
- Optimization 4 (Multiple versions): bounds the number of concurrent clients that can perform ``access``.

You can test individual optimizations using the following branches:
- [no_and_pm_optimization](https://anonymous.4open.science/r/MVPORAM-NO-AND-PM): code without any optimizations when clients are started with ``full`` position map and can be used to check the impact of Optimization 1 by starting clients giving ``triple`` as position map type parameter.
- [evict_optimization](https://anonymous.4open.science/r/MVPORAM-EVICT): code with Optimization 1.
- [response_optimization](https://anonymous.4open.science/r/MVPORAM-RESPONSE): code with Optimization 3.

In all branches Optimization 1 can be disabled by setting the ``position map type`` parameter as ``full`` and Optimization 4 can be disabled by setting the ``max concurrent clients`` parameter grater than number of clients performing operations.

Master branch can be used to try the impact of all the optimizations combined.

***Feel free to contact us if you have any questions!***