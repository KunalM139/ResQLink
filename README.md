# ResQLink
huuo
An Android emergency app that sends your **SOS message + GPS location** to contacts — even with **no internet or cell signal** — by relaying through nearby phones via Bluetooth.

## Status

- Work in progress. Core flows are implemented, but some features and polish are still pending.
- See the current backlog in [TODO.md](TODO.md).

---

## What You Need

- **Android Studio** (Ladybug 2024.2 or newer)
- A **Firebase** account (free)
- A **physical Android phone** (Android 8.0+) — BLE doesn't work on emulators

---

## Build in 4 Steps

### Step 1 — Get the code

```bash
git clone <repo-url>
cd ResQLink
```

### Step 2 — Add Firebase

1. Go to [console.firebase.google.com](https://console.firebase.google.com/)
2. Create a project → Add Android app → package name: **`com.resqlink.app`**
3. Download **`google-services.json`** → put it in the **`app/`** folder
4. In the Firebase console, enable:
   - Authentication → **Anonymous** sign-in
   - **Cloud Firestore** (start in test mode)

```
ResQLink/
└── app/
    └── google-services.json   ← put it here
```

### Step 3 — Open & sync

1. Open Android Studio → **File → Open** → pick the `ResQLink` folder
2. Click **Sync Now** when prompted
3. Wait for dependencies to download

> If asked about a Gradle wrapper, let Android Studio generate it automatically.

### Step 4 — Build & run

**Option A — Android Studio:**
- Plug in your phone (USB debugging on)
- Click the green **Run ▶** button

**Option B — Command line:**
```bash
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Permissions the App Asks For

| Permission | Why |
|---|---|
| Location | To include GPS coordinates in the SOS |
| Bluetooth | To relay messages via BLE mesh when offline |
| SMS | To send a backup text message to contacts |
| Notifications | To show incoming emergency alerts |

Grant all of them for the app to work fully.

---

## How It Works

```
You press SOS
     │
     ├── Have internet?
     │      YES → sends to server → push notification + SMS to contacts
     │
     └── No internet?
            → broadcasts via Bluetooth
            → nearby phones with ResQLink pick it up
            → if THEY have internet → upload it (gateway)
            → if not → store it and keep relaying
            → when ANY phone gets internet → message gets delivered

Emergency message includes a Google Maps link:
https://maps.google.com/?q=LATITUDE,LONGITUDE
→ Tapping the link opens the location in the phone's default maps app.
```

**Key idea:** your message hops from phone to phone until one finds internet, then it gets delivered. Messages are **encrypted** so relay phones can't read them.

---

## App Screens

| Screen | What it does |
|---|---|
| **Home** | Big SOS button, connection status, mesh service toggle |
| **Contacts** | Add/remove emergency contacts who receive your SOS |
| **Alerts** | List of received emergencies — tap to open location in maps |
| **Settings** | Toggle shake/fall/power-button auto-SOS triggers |

---

## Project Structure (Quick Overview)

```
app/src/main/java/com/resqlink/app/
├── crypto/        Encryption (AES-256-GCM)
├── data/          Database, Firebase, SMS, repositories
├── di/            Dependency injection (Hilt)
├── domain/        Business logic (send SOS, relay packets)
├── mesh/          Bluetooth mesh networking (advertise, scan, relay)
├── service/       Background mesh service
├── trigger/       Auto-SOS detectors (shake, fall, power button)
├── ui/            Screens, ViewModels, theme (Jetpack Compose)
└── util/          Helpers and constants
```

---

## Common Issues

| Problem | Fix |
|---|---|
| Build fails | Make sure JDK 17 is set: Android Studio → Settings → Build → Gradle → JDK |
| No `google-services.json` | Download from Firebase Console → place in `app/` |
| Bluetooth not working | Must use a real phone, not an emulator |
| Permissions not working | Settings → Apps → ResQLink → Permissions → enable all |

---

## License

MIT
