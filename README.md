![camari_logo_wordmark_combined](https://github.com/user-attachments/assets/b1366f8d-d68c-4a85-ab72-5a2fd717d11f)

Camari lets you use your Android phone as a webcam for OBS.

Works over WiFi or mobile hotspot from your phone! 

There is a debug version of this available in the [releases section](https://github.com/alexjyong/Camari/releases/tag/v1.0.0)

## OBS setup

You'll need [OBS Studio](https://obsproject.com/) installed on your computer first.

1. Install Camari on your Android phone and open it
2. Tap **Start Streaming** — you'll see a URL like `http://192.168.1.x:8080/`
3. In OBS, add a **Browser Source**
4. Paste that URL into the URL field, set width to **1280** and height to **720**
5. Click OK

That's it. Video should appear within a couple seconds.

**Your phone and computer need to be on the same network.** If you're not on WiFi, enable your phone's hotspot and connect your computer to it. Camari will detect this and show the right URL automatically.

## Discord / Teams / Zoom / Slack

Set up the Browser Source above, then start **Virtual Camera** from the OBS Tools menu. Select "OBS Virtual Camera" as your camera in whatever app you're using.

## Troubleshooting

- **Stream frozen in OBS**: Right-click the Browser Source -> Refresh
- **Can't connect**: Make sure your phone and computer are on the same network
- **Stream stops when I lock my phone**: Camari should run as foreground server and prevent this. Buuuut, if it does, check your phone's battery optimization settings and exempt Camari.

## Building it yourself

**Prerequisites:**
- Node.js 20+
- Java 17 (`JAVA_HOME` set)
- Android SDK with `platform-tools`, `platforms;android-34`, and `build-tools;34.0.0` installed (`ANDROID_HOME` set)
- `adb` on your PATH (comes with platform-tools)

Works on Linux and Mac. Once the prerequisites are installed, run:

```bash
./build.sh
```

APK ends up at `mobile/android/app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install mobile/android/app/build/outputs/apk/debug/app-debug.apk
```

**Or fork and use GitHub Actions** — go to Actions -> "Build Android APK" -> Run workflow. It'll build the APK and attach it as an artifact you can download and sideload.
