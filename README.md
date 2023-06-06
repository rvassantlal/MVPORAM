# myPathORAM

# How to run
**Note:** Use ``.\smartrun.sh`` instead of ``.\smartrun.cmd`` in Unix-based operating system.
#### With BFT-SMaRt
1. Start servers.
    ```
    .\smartrun.cmd oram.server.ORAMServer <process id>
    ```
2. Start client.
    ```
    .\smartrun.cmd oram.testers.ClientTester <client id> <oram password> <oram name> <test size>
    ```


#### With COBRA
1. Start servers.
    ```
   .\smartrun.cmd oram.server.ORAMServer <process id>
    ```
2. Start client.
    ```
   .\smartrun.cmd oram.testers.ORAMClient
    ```