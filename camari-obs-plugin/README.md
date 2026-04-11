# Camari OBS Plugin

A native OBS Studio source plugin that streams your Android phone camera directly into OBS — no Browser Source required.

## Requirements

- OBS Studio 28 or later (Windows or macOS)
- [Camari](https://github.com/alexjyong/Camari) app running on your Android phone

## Installation

### Windows

1. Download `camari-obs-plugin-windows-x64.zip` from [Releases](../../releases)
2. Extract and copy `camari-obs-plugin.dll` to:
   ```
   C:\Program Files\obs-studio\obs-plugins\64bit\
   ```
3. Restart OBS

### macOS

1. Download `camari-obs-plugin-macos.pkg` from [Releases](../../releases)
2. Double-click to install
3. Restart OBS

Manual macOS path:
```
~/Library/Application Support/obs-studio/plugins/camari-obs-plugin/
```

## Usage

1. Open Camari on your Android phone and tap **Start Streaming**
2. In OBS, click **+** in the Sources panel and select **Camari**
3. Enter your phone's IP address (shown in the Camari app) and port (default: 8080)
4. Click **Test Connection** to verify OBS can reach your phone
5. Click **OK** — the camera feed appears immediately

The source reconnects automatically if the stream drops.

## Building from Source

**Requirements:** CMake 3.28+, C17 compiler (MSVC on Windows, clang on macOS)

```bash
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

To run unit tests:
```bash
cmake -B build -S . -DBUILD_TESTING=ON
cmake --build build
ctest --test-dir build
```

## License

GNU GPL v3 — same as the Camari Android app.
