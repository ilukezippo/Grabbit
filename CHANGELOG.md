# Changelog

## 1.1 — 2026-11-23

- **Faster downloads:** several connections per file (aria2c) and 8 pieces at once for streamed videos. YouTube and others limit each connection's speed, so this is much faster. If a site doesn't like it, Grabbit retries the normal way by itself. Turn it off in Settings → Download speed
- **Downloads at the same time:** choose 1 to 4
- **Convert pictures:** JPG, PNG, WebP, HEIC, AVIF… to JPG, PNG, WebP or PDF, in Tools and from Downloads (Edit → Convert)
- **Walkthrough on first launch:** a short animated tour of sharing, quality, Downloads, recording and settings. Replay it from Settings → About → *How to use Grabbit*
- Also since 1.0: record from the notification shade and a Quick Settings tile with a countdown, edit mode in Downloads, ask-or-download-right-away, download-complete notifications, Instagram stories, in-app update check

## 1.0.7 — 2026-11-23

- **Record from the notification shade:** a quiet *Screen recorder* notification stays there with a **Record** button (turn it off in Tools)
- **Quick Settings tile:** add *Grabbit Record* to your pull-down tiles; tap it to start or stop recording
- **Countdown before recording:** Off, 3, 5 or 10 seconds, shown as a big number on screen; Stop during the countdown cancels

## 1.0.6 — 2026-11-23

- **Edit downloads:** tap **Edit** (or long-press an item), select several or **Select all**, then **Convert**, **Clear** or **Delete**. *Clear* only takes items off the list; *Delete* also removes the files from your phone, after asking
- Finished items show just **Share**; tap the card to open the file
- **Ask every time or download right away:** Settings → Default download → *When I grab a link*. With *Download right away*, sharing a link starts the download with your saved quality and format (live streams still ask)
- **Download complete** notification: tap it to open the file
- **Later** on the update card hides it until the next version
- **Fixed:** the clipboard link card sometimes didn't appear on Android 10+; it also no longer offers a link you already grabbed
- **Fixed:** Paste with no link in the clipboard now says so
- **Fixed:** on Android 10, deleting files from before a reinstall said *Deleted* when it couldn't

## 1.0.5 — 2026-11-23

- **Instagram stories:** a link to one story is now fetched directly by its id; for a whole story, Grabbit finds the account from its profile or its story page
- If Instagram still refuses, the message says which step failed and what Instagram answered

## 1.0.4 — 2026-11-23

- **Fixed:** Instagram stories said *You need to log in* even when signed in. Instagram refuses story requests that don't come from a real browser, so Grabbit now asks through the same built-in browser you signed in with
- Clearer Instagram messages: no story right now, sign in again, Instagram busy, post not available
- Sign-in waits a moment for Instagram to finish before closing, so the saved login is complete

## 1.0.3 — 2026-11-23

- **Fixed:** 1.0.2 couldn't be installed on some phones ("App not installed"). Grabbit no longer asks for the *install other apps* permission: **Update** now downloads the new version through your browser, and you tap the download to install it

## 1.0.2 — 2026-11-23

- **App updates:** Grabbit checks GitHub for a new version when it starts and shows a card with an **Update** button that downloads it and opens the installer. *Check for updates* is in Settings too
- **Tap a finished download to open it**, anywhere on its card
- **Video previews:** screen recordings and other videos without a thumbnail show a frame from the video
- **Fixed:** the big gradient buttons (Sign in to Instagram, Start recording, Grab, Download, Convert) only reacted when you tapped the words in the middle. Now the whole button works

## 1.0.0 — 2026-11-23

First release.

- Paste a link or **Share → Grabbit** from any app: YouTube, Instagram, TikTok, X, Facebook, Snapchat, Reddit, Twitch, Vimeo, Pinterest, SoundCloud and 1,000+ more sites
- Pick the **quality** (144p to 4K) and the **format** (MP4, MKV, WEBM), or **audio only** as MP3, M4A or OPUS
- **Photos:** Instagram posts and carousels, X photo posts, direct image links and any page with a preview picture
- **Instagram stories and private posts** you can see, after signing in once inside the app
- **Record live streams** at full quality, stop and save whenever you like
- **Screen recorder** with app sound (Android 10+) and a floating stop bubble
- **Converter:** any video or audio on your phone to MP3, M4A, OPUS, MP4, MKV, WEBM or GIF
- Downloads queue with progress, cancel, retry, open and share; files go to `Download/Grabbit`
- Arabic and English, dark design, **Update engine** button for when a site changes
