# myPathORAM

# How to run
**Note:** Use ``.\smartrun.sh`` instead of ``.\smartrun.cmd`` in Unix-based operating system.
1. Start servers.
    ```
   .\smartrun.cmd oram.server.ORAMServer <process id>
    ```
2. Start client.
    ```
   .\smartrun.cmd oram.testers.RandomParallelTester <number of clients> <oram id> <number of ops> <PM type>
    ```