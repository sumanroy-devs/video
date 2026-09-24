# Video

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/sumanroy-devs/video.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/sumanroy-devs/video/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/sumanroy-devs/video/total?logo=github&cacheSeconds=3600)](https://github.com/sumanroy-devs/video/releases/latest)


**Video is an Android video player built on the libmpv library, forked from
[mpv-android](https://github.com/mpv-android/mpv-android). It aims
to combine the powerful features of mpv with an easy to use interface and additional
features.**

- Simpler and Easier to Use UI
- Material3 Expressive Design
- Advanced Configuration and Scripting
- Enhanced Playback Features
- Picture-in-Picture (PiP)
- Background Playback
- High-Quality Rendering
- Network Streaming
- File Management
- Completely free and open source and without any ads or excessive permissions
- Media picker with tree and folder view modes
- External Subtitle support
- Zoom gesture
- External Audio support
- Search Functionality
- SMB/FTP/WebDAV support
- Custom Playlist management support

**This project is still in development and is expected to have bugs. Please report any bugs you find in
the [Issues](https://github.com/sumanroy-devs/video/issues) section.**

---

## Building

### Prerequisites

- JDK 17
- Android SDK with build tools 34.0.0+
- Git (for version information in builds)

### Standard Build

The build is flavor-less — use the plain Gradle tasks:

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK (requires signing setup, see below)
```

### Local Release Signing

Release builds need a `keystore.properties` file at the project root (gitignored):

```properties
storeFile=video.keystore
storePassword=<store password>
keyAlias=<key alias>
keyPassword=<key password>
```

Local `assembleRelease` / `bundleRelease` fail fast with a clear message if this file
is missing. CI runners are exempt — they sign with repository secrets instead (below).

### APK Variants

The app generates multiple APK variants for different CPU architectures:

- **universal**: Works on all devices (larger size)
- **arm64-v8a**: Modern 64-bit ARM devices (recommended for most users)
- **armeabi-v7a**: Older 32-bit ARM devices
- **x86**: Intel/AMD 32-bit devices
- **x86_64**: Intel/AMD 64-bit devices

---

## Releases

### Setting Up Release Signing

To enable automatic signing for release builds in GitHub Actions, you need to configure the
following secrets in your GitHub repository:

1. Navigate to your repository on GitHub
2. Go to **Settings** → **Secrets and variables** → **Actions**
3. Add the following repository secrets:

| Secret Name              | Description                                          |
| ------------------------ | ---------------------------------------------------- |
| `SIGNING_KEYSTORE`       | Base64-encoded keystore file (`.jks` or `.keystore`) |
| `SIGNING_KEY_ALIAS`      | The alias name used when creating the keystore       |
| `SIGNING_STORE_PASSWORD` | Password for the keystore file                       |
| `KEY_PASSWORD`           | Password for the key (can be same as store password) |

#### Encoding Your Keystore

To encode your keystore file to base64:

**Linux/macOS:**

```bash
base64 -i your-keystore.jks | tr -d '\n' > keystore.txt
```

**Windows (PowerShell):**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-keystore.jks")) | Out-File -FilePath keystore.txt -NoNewline
```

Copy the contents of `keystore.txt` and paste it as the value for the `SIGNING_KEYSTORE` secret.

### Creating a Release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit the changes
3. Create and push a tag:
   ```bash
   git tag -a v1.4.0 -m "Release version 1.4.0"
   git push origin v1.4.0
   ```
4. GitHub Actions will build all APK variants, sign them, verify the pinned signer
   certificate, and publish the release automatically as **Video \<version\>**

### Creating a Preview Release

1. Create and push a preview tag:
   ```bash
   git tag -a v1.4.0-preview.1 -m "Preview release"
   git push origin v1.4.0-preview.1
   ```
2. GitHub Actions will publish it as a pre-release automatically

---

## Acknowledgments

- [mpvEx](https://github.com/marlboro-advance/mpvEx)
- [mpv-android](https://github.com/mpv-android)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [Next player](https://github.com/anilbeesetti/nextplayer)
- [Gramophone](https://github.com/FoedusProgramme/Gramophone)