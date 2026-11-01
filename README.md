# Free IPTV – Android TV APK

A simple Android TV (and phone) app that plays **free, public IPTV channels** from the
open-source [iptv-org](https://github.com/iptv-org/iptv) playlists. No account or API key needed.

## Features
- Categories: Arabic, News, beIN Sports (free-to-air beIN channels found in the free lists), Sports, Kids, Movies, Music, Documentary, Religious, English,
  Saudi Arabia, Egypt, UAE, Jordan
- Channel grid with logos, search box
- **+ Add playlist URL**: load your own M3U link (e.g. from a TV service you subscribe to); it is saved as *My Playlist*
- Full-screen player (Media3 / ExoPlayer, HLS supported)
- TV remote friendly: D-pad navigation, **Up / Down or CH+ / CH−** switches channel while watching
- Shows in the Android TV launcher (banner) and in the normal phone launcher

## Get the APK
Every push runs the **Build APK** GitHub Action. Open the repo's **Actions** tab → latest run →
download the `FreeIPTV-apk` artifact, unzip, and install `app-release.apk`.

Install on a TV box: copy the APK with a USB stick or an app like *Send Files to TV* / *Downloader*,
then allow "Install unknown apps" for that app.

## Build locally
Requires JDK 17 and the Android SDK:
```
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Add or change channel lists
Edit `Playlists.all` in `app/src/main/java/com/freetv/iptv/Channel.kt`. Any M3U URL works.

> Streams are third-party public links listed by iptv-org; some may be offline or geo-blocked.
