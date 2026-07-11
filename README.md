# Red Media Helper

Red Media Helper is a media control and download toolkit for Chrome/Brave,
Windows, and Android. It combines an extension for playback controls with local
companion applications for media inspection and downloads.

This project is based on the original
[Video Playback Extension](https://github.com/sunnyw1212/video-playback-extension).

## Components

- **Red Media Helper**: Manifest V3 browser extension and Windows companion.
- **Red Media Helper**: standalone Windows companion.
- **Red Media Mobile**: Android download manager with an interactive browser.

## Features

- Adjustable playback speed and persistent settings.
- Volume boost above 100%.
- Play, pause, restart, loop, skip, and theater controls.
- HTML5, HLS, DASH, and yt-dlp compatible media detection.
- Quality selection and multi-audio downloads when supported by the source.
- Audio-language selection, MP4/MKV output, embedded subtitles, and audio-only downloads.
- Parallel download queue with progress and cancellation.
- Separate progress for source files, merging, finalization, and saving.
- Interactive Android browser for pages that require user actions before media appears.

DRM-protected media is not supported. Only download media you are authorized to
save.

## Browser Extension

```powershell
npm install
npm run build
```

The extension uses Vite and Preact. `npm start` keeps the unpacked `build`
directory updated while developing.

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

Debug APKs are generated under `android-helper/app/build/outputs/apk/debug`.
Release builds are minified and split by CPU architecture to avoid bundling three
copies of the native media tools in every download.
See [android-helper/README.md](android-helper/README.md) for Android-specific details.

## Releases

Tags matching `v*` trigger the GitHub release workflow. A release contains:

- `red-media-extension-unpacked.zip`
- `Red Media Helper.exe`
- `Red-Media-Mobile-arm64.apk` for most current Android phones
- `Red-Media-Mobile-arm32.apk` for older 32-bit phones
- `Red-Media-Mobile-x86_64.apk` for x86-64 devices and emulators
- `Red-Media-Mobile-universal.apk` as the larger compatibility fallback

Local extension and Windows assets can be produced with:

```powershell
npm run package:release
```

Run the contract tests with `npm test`.

Android releases use a stable signing key provided through the
`RED_MEDIA_KEYSTORE_*` GitHub secrets. Keep an offline backup of that key; losing
it prevents compatible updates to previously installed APKs.

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
