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

**Wi-Cache-Controller configuration**

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
