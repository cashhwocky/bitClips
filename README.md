# bitClips

Endless YouTube Shorts (called Clips here) for bitDroid, laid out like TikTok and dressed in
Bloom / Material You: tonal Lilac colors, scalloped cookie shapes, squishy springs, and the floating
tab bar from bitMessages. Kotlin + Jetpack Compose, NewPipe Extractor + Media3.

## Features

- **For You / Following** switch on Home, full-screen vertical swiping, edge-to-edge clips
- **Instant playback**: the next clips are fetched and buffered before you swipe
- **Your own feed**: signed in, For You is your personal YouTube Shorts sequence and Following is Shorts from channels you're subscribed to
- **TikTok rail**: creator cookie with a follow +, like, comments, save, share, spinning cookie disc
- **Double-tap to like** with a heart burst
- **Comments** in a sheet: pinned and creator-hearted comments, replies, endless scrolling
- **Discover**: hashtag chips and search (clover button), results in a 3-column grid
- **Creator pages** with their clips in a grid, **Profile** with Liked / Saved / History
- **Setup** you can't skip: notifications, install updates, YouTube sign-in
- **In-app updates**: a full-screen update page every time you open the app while a newer release is out
- **Themes** (Lilac default, Material You, Mint, Peach, Ocean, Rose, Midnight) and **app icons** (Lilac default, White, Cookie, Mint, Peach, Ocean, Night)

## Releases

A tag like `v0.1.0` builds and publishes `bitClips-0.1.0-release.apk` plus `update.json`. The tag must match
`versionName` in `app/build.gradle.kts`; bump `versionCode` every release.

Signing secrets: `BITCLIPS_KEYSTORE_B64`, `BITCLIPS_KEYSTORE_PASSWORD`, `BITCLIPS_KEY_ALIAS`, `BITCLIPS_KEY_PASSWORD`.

## License

GPL-3.0 (NewPipe Extractor is GPL-3.0).

Made by Alex Desrosier.
