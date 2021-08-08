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

