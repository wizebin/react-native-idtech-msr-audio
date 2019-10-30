# React Native ID TECH MagStripe Reader (audio jack) Integration

This is a React Native library that wraps the [ID Tech MSR audio](http://www.idtechproducts.com/products/mobile-readers/msr-only) [native library](https://atlassian.idtechproducts.com/confluence/display/KB/Shuttle+-+downloads) for communicating with the UniMag I/II/Pro and Shuttle readers.

iOS and Android are supported.

## Installation

*   `npm install https://github.com/wizebin/react-native-idtech-msr-audio --save`
*   `react-native link`

### iOS setup
*   Add _AVFoundation_, _AudioToolbox_, _MediaPlayer_ frameworks to the project (Build Phases -> Link Binary With Libraries)
*   Add the `NSMicrophoneUsageDescription` permission to the Info.plist.

### Android setup
*   Check whether `react-native-link` performed the configuration correctly...
*   Add the necessary permissions to `AndroidManifest.xml` :
```
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.INTERNET"/>
```
Note that for Android M and later, you may need to check for permissions at runtime and prompt the user with the [PermissionsAndroid API](https://facebook.github.io/react-native/docs/permissionsandroid.html).

*   Add to `android/settings.gradle` if `react-native link` didn't:
```
include ':react-native-idtech-msr-audio'
project(':react-native-idtech-msr-audio').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-idtech-msr-audio/android')
```
*   Add to `android/app/build.gradle` :
```
dependencies {
  compile project(':react-native-idtech-msr-audio')
}
```
*   Add to `MainApplication.java` :
```
  import com.oncethere.idtechmsraudio.IDTechMSRAudioPackage;
  // ...

  public class MainApplication extends Application implements ReactApplication {

    private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {

      // ...

      @Override
      protected List<ReactPackage> getPackages() {
        return Arrays.<ReactPackage>asList(
            new MainReactPackage(),
              new IDTechMSRAudioPackage(),
              // ...
        );
      }
    }
  }
```

*   The preset XML table fetched by the ID Tech library offers limited device support.  That is not robust enough for production use, so auto config is leveraged instead.  The first time detection will take some time, but subsequent connections use the cached profile which is fast.


### React Native JS usage
*   Import the module in a RN app:
`import idtech from 'react-native-idtech-msr-audio';`
*   Platform-specific dependencies are listed under the Example section.

## API

*   `activate(readerType, swipeTimeout, logging)` -- Start a connection to the card reader. Parameters:
    *   _readerType_: UniMag1 = 1, UniMagPro = 2, UniMag2 = 3, Shuttle = 4.
    *   _swipeTimeout_: Set swipe to timeout after n seconds. 0 waits indefinitely.
    *   _logging_: (bool) Enables info level NSLogs inside SDK.
    *   RETURNS Promise
*   `deactivate()` -- End connection to the card reader.
    *   RETURNS Promise
*   `swipe()` -- Begin listening for a swipe. Register for events to receive the card swipe data.
    *   RETURNS Promise
*   `parseSwipeData(data, dataFormat = 'hex')` -- the `umSwipe_receivedSwipe` event includes a `data` entry, pass this raw property to `parseSwipeData`
    *   _data_: Either a Buffer or a string
    *   _dataFormat_: The format of data if it is not a Buffer
    *   RETURNS
    ```javascript
    {
      serial: HEX_STRING, // The device serial number
      tracks: [HEX_STRING, HEX_STRING, HEX_STRING], // The unencrypted tracks, for unencrypted cards this is all
      valid: boolean, // True if the payload appears valid
      aes: boolean, // (encrypted payloads only) True if the encryption algorithm is AES
      encrypted: boolean, // True if the payload appears encrypted
      encryptedTracks: [HEX_STRING, HEX_STRING, HEX_STRING], // (encrypted payloads only)
      ksn: HEX_STRING, // (encrypted payloads only) The key serial number of the transaction
    }
    ```


#### Events
Events are emitted by NativeEventEmitter under the name `IdTechUniMagEvent`. Upon a successful swipe, the response type will be `swipe_received` and the _data_ key will be populated.

#### Example code snippet
```Javascript
import idtech from 'react-native-idtech-msr-audio';
import { NativeModules, NativeEventEmitter } from 'react-native';

componentDidMount() {
  // First subscribe to events from the card reader.
  const IdTechUniMagEvent = new NativeEventEmitter(NativeModules.IDTECH_MSR_audio);
  this.IdTechUniMagEventSub = IdTechUniMagEvent.addListener('IdTechUniMagEvent', (response) => {
    if (response.type === 'connected') {
      console.log("Card reader connected. Initiating swipe detection...");
      idtech.swipe();
    }
    if (response.type === 'swipe_received') {
      if (response.data) {
        try {
          const swipeData = idtech.parseSwipeData(response.data);
          console.log("Successfully received swipe.", swipeData);
        } catch (err) {
          console.log("Error parsing idtech swipe", err);
        }
      } else {
        console.log("Swipe data missing");
      }
      idtech.deactivate(); // Disconnect after receiving a successful swipe
    }
    console.log("IDTECH_MSR_audio event notification: " + JSON.stringify(response));
  });

  // Then connect to the card reader.
  idtech.activate(
    idtech.READERS.SHUTTLE,
    0, // wait indefinitely for swipe
  }).then((response) =>{
      console.log("idtech activation response:" + JSON.stringify(response));
  });
}

componentWillUnmount() {
  // Both of these calls are necessary to disconnect from the IDTech library.
  this.IdTechUniMagEventSub.remove();
  idtech.deactivate();
}
```

## Example
The `MSRExample/` directory has a sample project using the IDTECH_MSR_audio library.

*   Install npm dependencies ```npm install```
*   Install React Native CLI globally ```sudo npm install -g react-native-cli```
*   ```react-native link```

#### iOS dependencies
*   Xcode
*   Open `MSRExample/ios/MSRExample.xcworkspace` in Xcode.
*   Build and run on a real iOS device.


#### Android Dependencies
*   ...

## ToDo
*   Update MSRExample with Android lib
*   Tests
