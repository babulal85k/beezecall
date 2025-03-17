# Bluetooth-to-Bluetooth Calling App

A Kotlin-based Android application that allows users to make **Bluetooth-to-Bluetooth voice calls** without an internet connection. The app connects directly to paired devices and streams real-time audio using Bluetooth.

---

## Features 🚀
- 📞 **Bluetooth Voice Calling**: Establish a direct connection and talk over Bluetooth.
- 🔎 **Scan & Connect**: Discover nearby Bluetooth devices and connect easily.
- 🎤 **Real-Time Audio Streaming**: Uses `AudioRecord` and `AudioTrack` for seamless communication.
- 📜 **Call History Log**: Stores call history with timestamps and durations.
- 🔔 **Foreground Service Support**: Keeps the app running during a call.

---

## Permissions 📜
Ensure you have the following permissions in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
```
For **Android 13+**, add:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

---

## Installation & Setup ⚙️
1. Clone the repository:
   ```sh
   git clone https://github.com/babulal85k/BluetoothCallApp.git
   ```
2. Open the project in **Android Studio**.
3. Connect an **Android device** (or use an emulator with Bluetooth support).
4. Build & Run the app.

---

## Usage 📱
### **1. Start Bluetooth Server**
- Open the **server device**.
- Tap `Start Listening for Calls`.

### **2. Connect from Client Device**
- Open the app on the **calling device**.
- Tap `Scan Bluetooth Devices`.
- Select a paired Bluetooth device.
- Tap `Start Bluetooth Call`.

### **3. End the Call**
- The call will automatically stop after 10 seconds.
- Modify this in `MainActivity.kt`.

---

## Code Structure 🏗
### **Main Files**
📂 **`BluetoothHelper.kt`** → Manages Bluetooth audio streaming.
📂 **`BluetoothServerService.kt`** → Handles Bluetooth server operations.
📂 **`MainActivity.kt`** → UI and Bluetooth connection logic.
📂 **`CallHistoryAdapter.kt`** → Displays call history in RecyclerView.

---

## Troubleshooting ❌
- **App Crashes on Android 8+** → Use `startForegroundService(intent)` instead of `startService(intent)`.
- **Bluetooth Not Connecting?** → Ensure devices are **paired** in Bluetooth settings.
- **No Sound?** → Check `AudioManager` settings and permissions.

---

## Contributions 🤝
Feel free to **fork** and submit PRs. Contact me via [GitHub](https://github.com/babulal85k)!

---

## License 📜
MIT License © 2025 Babu Lal Mandal

