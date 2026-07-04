# BT LED Controller

An Android app for controlling Bluetooth-operated LEDs — RGB LEDs or LED strips
driven by an Arduino (or similar microcontroller) with a classic Bluetooth
serial module such as the **HC-05** or **HC-06**.

## Features

- Lists paired Bluetooth devices and scans for new ones
- Connects over the standard Serial Port Profile (SPP/RFCOMM)
- LED power on/off switch
- Brightness slider (0–255)
- Color buttons: red, green, blue, white
- Blink/flash effect toggle
- Free-form custom command box for extending the protocol
- Activity log showing everything sent and received
- Handles both legacy and Android 12+ Bluetooth runtime permissions

## Project layout

```
app/                          Android app (Kotlin, Material 3, view binding)
  src/main/java/com/example/btledcontroller/
    MainActivity.kt           UI, permissions, command sending
    BluetoothLedService.kt    RFCOMM connect + I/O threads
    DevicePickerDialog.kt     Paired-device list + discovery bottom sheet
    DeviceAdapter.kt          RecyclerView adapter for devices
arduino/BluetoothLED/         Companion Arduino sketch for HC-05/HC-06 + RGB LED
```

## Serial protocol

The app sends plain ASCII over the Bluetooth serial link:

| Command      | Action                          |
|--------------|---------------------------------|
| `1`          | All LEDs on                     |
| `0`          | All LEDs off                    |
| `r` `g` `b`  | Set color red / green / blue    |
| `w`          | White (all channels)            |
| `f`          | Toggle blink mode               |
| `V<0-255>\n` | Set brightness, e.g. `V128`     |

Anything typed into the app's **Custom command** box is sent verbatim
(with a trailing newline), so the protocol is easy to extend on the
microcontroller side.

## Building the app

Open the project in Android Studio (Hedgehog or newer) and press **Run**, or
build from the command line with the Android SDK installed:

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Requires JDK 17+. `minSdk` is 26 (Android 8.0), `targetSdk` 34.

## Hardware setup (Arduino example)

Flash `arduino/BluetoothLED/BluetoothLED.ino` to an Uno/Nano and wire:

| Signal        | Arduino pin                                   |
|---------------|-----------------------------------------------|
| HC-05 TXD     | 10 (SoftwareSerial RX)                        |
| HC-05 RXD     | 11 (SoftwareSerial TX — use a voltage divider)|
| LED red       | 9 (PWM)                                       |
| LED green     | 6 (PWM)                                       |
| LED blue      | 5 (PWM)                                       |

Then:

1. Power the Arduino; the HC-05 LED blinks fast (discoverable).
2. Pair the module in your phone's Bluetooth settings (default PIN `1234` or `0000`),
   or use the app's **Scan** button.
3. Open the app, tap **Connect**, and pick the module.
4. Control your LEDs.
