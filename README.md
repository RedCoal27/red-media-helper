# Red Media Helper

Red Media Helper is a media control and download toolkit for Chrome/Brave,
Windows, and Android. It combines an extension for playback controls with local
companion applications for media inspection and downloads.

This project is based on the original
[Video Playback Extension](https://github.com/sunnyw1212/video-playback-extension).

## Components

- **Red Media**: Manifest V3 browser extension.
- **Red Media Helper**: standalone Windows companion.
- **Red Media Mobile**: Android download manager with an interactive browser.

## Features

- Adjustable playback speed and persistent settings.
- Volume boost above 100%.
- Play, pause, restart, loop, skip, and theater controls.
- HTML5, HLS, DASH, and yt-dlp compatible media detection.
- Quality selection and multi-audio downloads when supported by the source.
- Parallel download queue with progress and cancellation.
- Interactive Android browser for pages that require user actions before media appears.

DRM-protected media is not supported. Only download media you are authorized to
save.

## Browser Extension

```powershell
npm install
npm run build
```

Open `chrome://extensions/`, enable developer mode, choose **Load unpacked**, and
select the generated `build` directory.

## Windows Helper

Build the standalone launcher:

```powershell
npm run build:helper
```

Launch `Red Media Helper.exe`. On first use it installs its local yt-dlp, FFmpeg,
aria2, and portable Node.js tools when required.

## Android App

```powershell
cd android-helper
.\gradlew.bat assembleDebug
```

The APK is generated at `android-helper/app/build/outputs/apk/debug/app-debug.apk`.
See [android-helper/README.md](android-helper/README.md) for Android-specific details.

## Releases

Tags matching `v*` trigger the GitHub release workflow. A release contains:

- `red-media-extension-unpacked.zip`
- `Red Media Helper.exe`
- `Red-Media-Mobile.apk`

Local extension and Windows assets can be produced with:

```powershell
npm run package:release
```

## Project Structure

- `src`: Red Media extension source.
- `utils`: Windows helper UI, server, installers, and packaging scripts.
- `android-helper`: Red Media Mobile Android project.
- `tools`: locally installed yt-dlp, FFmpeg, aria2, and Node.js binaries.
- `build`: unpacked extension output.

## Credits

Original projects:

- [sunnyw1212/video-playback-extension](https://github.com/sunnyw1212/video-playback-extension)
- [Video Playback Extension on the Chrome Web Store](https://chromewebstore.google.com/detail/video-playback-extension/dilncfnkialpgbnpcjmghnepnankdibk)
- [chrome-extension-boilerplate-react](https://github.com/lxieyang/chrome-extension-boilerplate-react)

Project direction and testing by **RedCoal**. Implementation assistance by
**OpenAI Codex**.

## License

MIT. Original Video Playback Extension copyright (c) 2021 Sunny Wong.
