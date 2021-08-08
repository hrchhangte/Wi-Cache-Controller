**Wi-Cache-Controller**

To clone the Wi-Cache-Controller, run the following command <br />

```
$ git clone git@github.com:hrchhangte/Wi-Cache-Controller.git
```
To build the Wi-Cache-Controller, run the following commands <br />

```
$ cd Wi-Cache-Controller
```
```
$ ant
```

To run the Wi-Cache-Controller, run the following command <br />
```
java -jar ./target/floodlight.jar -cf ./src/main/resources/floodlightdefault.properties
```

Wi-Cache-Controller runs on top of the Floodlight OpenFlow controller.
./src/main/resources/floodlightdefault.properties contains the default properties/configuration of the Floodlight OpenFlow controller.

**Wi-Cache-Controller (Wi-Cache module) configuration**

The Wi-Cache-Controller is configured using an initial configuration file (initFile). This configuration file contains global configuration that consists of: <br />
1. Wi-Cache APs IP addresses and the size of cache configurated for caching files. <br />
2. Whether a file should be split in to multiple segments or not (if it is not spit, it is just a single segment). <br />
3. The paraemters that will be used while splitting the files: min-segment-size, max-segment-count. <br />
4. Whether file coding is to be used (this is currently under development). <br />
5. The initial configuration of the caches of the Wi-Cache APs. This tells you which which segments are present in each Wi-Cache APs. If this is 'none', then the Wi-Cache-Controller splits the files, and distribute the segments across the segments. <br />
6. The cache replacement policy. <br />
7. The Wi-Cache application that extends the behaviour the Wi-Cache-Controller. This can be empty. <br /> <br />

A sample configuration is shown below: <br /> 

#list of Wi-Cache agents showing their IP addresses <br />
#and storage (in bytes) allocated for caching <br />
agent-configuration <br />
192.168.1.10	1073741824 <br />
192.168.1.12	1073741824 <br />
192.168.1.14	1073741824 <br />
#other configurations <br />
#whether content should be split into multiple segments <br />
split-files	    yes <br /> 
min-segment-size	19692761 <br />
max-segment-count	5 <br />
file-coding		    none <br />
#the location of the initial cache configuration file <br />
#that contains placement of content segment across the APs <br />
cache-init-file     init.csv <br />
#the cache replacement policy used in the caches <br />
cache-replacement   LRU <br />
#The main Wi-Cache application which controls  <br />
#the control delivery applications <br />
net.floodlightcontroller.odin.applications.ContentDelivery <br />

**Other Wi-Cache-Controller (Odin) configuration**

If DHCP is not used in the network, then each client in the network is assigned an IP addresses and a BSSID (for the LVAP). This is done in the file odin_client_list (referred in ./src/main/resources/floodlightdefault.properties). An example configuration is shown below, where the format of each row is <MAC address of the client> <IP address of the client> <LVAP BSSID> <SSID of the Wi-Cache WiFi network> <br />

C0:25:E9:2F:5E:53 192.168.1.101 00:1B:B3:67:6B:01 wicache-bssid-test-1
54:35:30:D6:A4:D3 192.168.1.102 00:1B:B3:67:6B:02 wicache-bssid-test-1
18:5E:0F:A1:87:E5 192.168.1.103 00:1B:B3:67:6B:03 wicache-bssid-test-1
54:35:30:D6:93:FF 192.168.1.104 00:1B:B3:67:6B:04 wicache-bssid-test-1
84:16:F9:0E:42:68 192.168.1.105 00:1B:B3:67:6B:05 wicache-bssid-test-1
D4:6E:0E:15:56:63 192.168.1.106 00:1B:B3:67:6B:06 wicache-bssid-test-1
0C:60:76:36:20:FA 192.168.1.107 00:1B:B3:67:6B:07 wicache-bssid-test-1
18:5E:0F:A1:88:12 192.168.1.108 00:1B:B3:67:6B:08 wicache-bssid-test-1
CC:B0:DA:A8:EE:DD 192.168.1.109 00:1B:B3:67:6B:09 wicache-bssid-test-1
18:5E:0F:57:20:BA 192.168.1.110 00:1B:B3:67:6B:10 wicache-bssid-test-1
74:DF:BF:88:37:9D 192.168.1.111 00:1B:B3:67:6B:11 wicache-bssid-test-1
C0:25:E9:2F:5E:53 192.168.1.112 00:1B:B3:67:6B:12 wicache-bssid-test-1
40:88:05:69:5F:65 192.168.1.113 00:1B:B3:67:6B:13 wicache-bssid-test-1
CC:61:E5:29:25:E2 192.168.1.114 00:1B:B3:67:6B:14 wicache-bssid-test-1


CC:61:E5:29:25:E2 192.168.1.114 00:1B:B3:67:6B:14 odin-bssid-test-1

