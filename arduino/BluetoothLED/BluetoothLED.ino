/*
 * BluetoothLED.ino — companion sketch for the BT LED Controller Android app.
 *
 * Hardware:
 *   - Arduino Uno/Nano (or similar)
 *   - HC-05 or HC-06 Bluetooth module:
 *       HC-05 TXD -> Arduino pin 10 (SoftwareSerial RX)
 *       HC-05 RXD -> Arduino pin 11 (SoftwareSerial TX, use a voltage divider!)
 *       VCC -> 5V, GND -> GND
 *   - Common-cathode RGB LED (or 3 MOSFET-driven LED strip channels):
 *       Red   -> pin 9  (PWM)
 *       Green -> pin 6  (PWM)
 *       Blue  -> pin 5  (PWM)
 *
 * Serial protocol (matches the Android app):
 *   '1'          -> all LEDs on
 *   '0'          -> all LEDs off
 *   'r','g','b'  -> set color red / green / blue
 *   'w'          -> white (all channels)
 *   'f'          -> toggle blink mode
 *   "V<0-255>\n" -> set brightness, e.g. "V128"
 *
 * Anything the app sends via its "Custom command" box arrives here verbatim,
 * so you can extend the protocol below.
 */

#include <SoftwareSerial.h>

const uint8_t PIN_RED = 9;
const uint8_t PIN_GREEN = 6;
const uint8_t PIN_BLUE = 5;

SoftwareSerial bt(10, 11);  // RX, TX

bool powerOn = false;
bool blinkMode = false;
bool blinkPhase = true;
uint8_t brightness = 255;
// Per-channel color mix, 0.0-1.0 as 0-255.
uint8_t mixRed = 255, mixGreen = 255, mixBlue = 255;

unsigned long lastBlinkToggle = 0;
const unsigned long BLINK_INTERVAL_MS = 400;

void applyOutput() {
  bool lit = powerOn && (!blinkMode || blinkPhase);
  uint8_t level = lit ? brightness : 0;
  analogWrite(PIN_RED, (uint16_t)level * mixRed / 255);
  analogWrite(PIN_GREEN, (uint16_t)level * mixGreen / 255);
  analogWrite(PIN_BLUE, (uint16_t)level * mixBlue / 255);
}

void setColor(uint8_t r, uint8_t g, uint8_t b) {
  mixRed = r;
  mixGreen = g;
  mixBlue = b;
  powerOn = true;  // picking a color implies "on"
  applyOutput();
}

void handleCommand(char c) {
  switch (c) {
    case '1': powerOn = true; break;
    case '0': powerOn = false; blinkMode = false; break;
    case 'r': setColor(255, 0, 0); break;
    case 'g': setColor(0, 255, 0); break;
    case 'b': setColor(0, 0, 255); break;
    case 'w': setColor(255, 255, 255); break;
    case 'f': blinkMode = !blinkMode; break;
    case 'V': {
      long value = bt.parseInt();  // reads digits following 'V'
      brightness = constrain(value, 0, 255);
      break;
    }
    default:
      return;  // ignore unknown bytes (e.g. line endings)
  }
  applyOutput();
  bt.print("OK ");
  bt.println(c);
}

void setup() {
  pinMode(PIN_RED, OUTPUT);
  pinMode(PIN_GREEN, OUTPUT);
  pinMode(PIN_BLUE, OUTPUT);
  bt.begin(9600);  // HC-05/HC-06 factory default baud rate
  applyOutput();
  bt.println("LED controller ready");
}

void loop() {
  if (bt.available()) {
    handleCommand(bt.read());
  }
  if (blinkMode && millis() - lastBlinkToggle >= BLINK_INTERVAL_MS) {
    lastBlinkToggle = millis();
    blinkPhase = !blinkPhase;
    applyOutput();
  }
}
