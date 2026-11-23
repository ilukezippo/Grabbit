# Grabbit — design (approved in chat 2026-11-23)

## Intent
Free, ad-free Snaptube alternative for Mubarak's Android phone: download video/images from any platform, choose resolution, convert formats. Sleek, modern.

## Approaches
1. **Native Kotlin + Jetpack Compose + youtubedl-android (yt-dlp + FFmpeg bundled)** — PICKED. Runs fully on-device, 1000+ sites, in-app engine update. Cost: ~80 MB APK.
2. NewPipe extractor — small but few sites. Rejected (not "any platform").
3. App + server — needs hosting. Rejected.

## Features
- Share-to-Grabbit sheet + paste link with preview (thumb, title, duration)
- Resolution picker (144p..4K) / audio only; MP4/MKV/WEBM, MP3/M4A/OPUS
- Convert already-downloaded files (FFmpeg)
- Images incl. Instagram carousels
- Instagram stories & private posts via in-app Instagram login (WebView) → cookies.txt stored in app-private storage only
- Live: "Record stream" via yt-dlp; plus screen recorder (MediaProjection + floating stop bubble, internal audio on Android 10+)
- Downloads tab: queue, progress, cancel, open/share; saved to Download/Grabbit
- Dark Material 3, gradient accent, Arabic/English toggle
- In-app yt-dlp update

## Non-goals
DRM content (Netflix, Shahid). No ads, no tracking, no server.

## Builder calls
minSdk 24, targetSdk 34, arm64-v8a + armeabi-v7a APK split → ship arm64 (universal too big). Signed with a generated release key.
