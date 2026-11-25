# Stash

A personal Android app for saving anything: notes, checklists, voice notes, photos, files, and posts or links shared from Instagram, X, YouTube, Chrome or any other app. Everything stays on the phone.

## Install or update

1. On your phone, open this repo's **Releases** page (you need to be signed in to GitHub because the repo is private) and download the latest `Stash-1.0.N.apk`.
2. Open the file and allow installing from your browser if Android asks.
3. Updating installs over the old version and keeps everything you've saved.

Every push to `main` builds a new APK automatically (`.github/workflows/build.yml`).

## What it does

- **Save from any app**: tap Share in Instagram, X, YouTube, Chrome, Gallery, Files and so on, then pick **Save to Stash**. Add a note, tag or collection, then Save. Links get a preview card with the title, picture and post text. Selected text can be saved from the text-selection menu too.
- **Notes and checklists**: type a note; turn it into a checklist and back; ticked items collapse to the bottom.
- **Voice notes, photos, camera, files**: attach any of them to anything.
- **Organize**: collections (with an emoji), tags, pinning, archive, a trash that keeps items for 30 days, search across everything, and filters (Notes, Lists, Links, Photos, Voice, Files).
- **Reminders**: on any item. Notifications have a Snooze button and survive restarts.
- **Fingerprint lock**: uses your fingerprint or the phone's PIN. Saving from other apps still works while Stash is locked.
- **Quick capture**: home-screen widget, a Quick Settings tile, and app-icon shortcuts.
- **Backup**: Settings → Export backup makes one `.zip` with everything. On a new phone, install Stash and use Settings → Restore.

## Link previews

Instagram and X don't let other apps download posts, so Stash saves the link plus a preview (title, picture, text), and keeps a local copy of the picture. If a post is deleted later, the link stops working. Previews come from:

- X: the free [FxTwitter](https://github.com/FixTweet/FxTwitter) API, with X's own embed API as a fallback
- YouTube and TikTok: their public oEmbed APIs
- Everything else: the page's Open Graph tags

Previews can be turned off in Settings.

## Signing

Release APKs are signed with `signing/stash-release.p12`. Its password lives only in the repo secret `STASH_KEYSTORE_PASSWORD`. Without that secret the build still runs, but it doesn't publish a release (a differently signed APK can't update the installed app).

## Tech

Kotlin, Jetpack Compose (Material 3), Room, DataStore, WorkManager, Glance (widget), AndroidX Biometric, Coil, Jsoup. Min Android 8.0 (API 26), target API 35.
