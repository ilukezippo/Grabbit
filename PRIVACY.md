# Grabbit privacy policy

Grabbit has no account, no ads, no analytics and no crash reporting. It has no server of its own.

## What goes over the internet

Only what you ask for:

- **Links you grab.** Grabbit connects to the site the link points to (YouTube, Instagram, X…) to read the video or photo and download it, the same way a browser would.
- **X (Twitter) photo posts** are read through the public `api.fxtwitter.com` service, which receives the post's link.
- **App update check.** When Grabbit starts (at most every 6 hours) it asks GitHub for the latest release. If you tap *Update*, your browser downloads the new APK from GitHub.
- **Engine update.** When you tap *Update engine*, Grabbit downloads the newest yt-dlp from its official GitHub releases.

## What stays on your phone

- **Instagram sign-in.** If you sign in, Instagram's login cookies are kept in Grabbit's private app storage and are only sent to instagram.com. *Sign out* in Settings deletes them.
- **Downloads** are saved to `Download/Grabbit`. The download history is a small file in the app's private storage.
- **Screen recordings** never leave your phone unless you share them.

## Permissions

| Permission | Why |
|---|---|
| Internet | Download what you ask for |
| Notifications | Show download and recording progress |
| Record audio | Only for the screen recorder's *Record app sound* option (Android 10+); it captures the sound apps play, not the microphone |
| Display over other apps | Only for the floating stop bubble while recording |
| Storage (Android 9 and older) | Save files to the Download folder |

Questions: [open an issue](https://github.com/ilukezippo/Grabbit/issues).
