# Red Media Mobile

Android download companion for Red Media. It accepts a pasted page URL or a
link shared from a browser, inspects the available media with yt-dlp, and manages
background downloads in a persistent queue.

## Features

- Paste a page or media URL.
- Receive links from the Android Share menu.
- Inspect available video qualities before downloading.
- Download video with all available audio tracks when supported.
- Download audio-only media as MP3.
- Keep downloads running in the background with Android notifications.
- Pause, resume, retry, and delete downloads.
- Save completed files to `Download/Red Media`.

## Build

Open the `android-helper` directory in Android Studio, or use:

```powershell
cd android-helper
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The GitHub workflow `Red Media Mobile` also builds and uploads a debug APK artifact.

## Technology

- Kotlin and Jetpack Compose
- Room for the persistent download queue
- WorkManager for background downloads
- [youtubedl-android](https://github.com/yausername/youtubedl-android) for yt-dlp, Python, and FFmpeg on Android

Only download media you are authorized to save. DRM-protected media is not supported.
