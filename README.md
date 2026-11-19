# android_bluetooth

A minimal Bluetooth SPP (Serial Port Profile) terminal for Android. It connects to a
paired HC-05 module and turns the phone into a serial console: type a line, send it, and
watch incoming bytes appear in a text view.

## How it works

- Looks through the bonded devices for one whose name contains `HC-05`
- Opens an RFCOMM socket to the standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`
- A background thread reads the socket and posts the data to the UI through a `Handler`
- Reconnects up to three times if the link drops

## Requirements

- Android 5.0 (API 21) or newer
- On Android 12+ the `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` runtime permissions are
  requested at runtime
- The HC-05 module must already be paired in the system Bluetooth settings

The UI labels are in Chinese: 连接 connects, 发送 sends.

## Build

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17 and an Android SDK with `build-tools 33.0.2` and `platforms;android-33`.
Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## License

BSD 3-Clause, see [LICENSE](LICENSE).
