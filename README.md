# BrightFetch MVP

BrightFetch is an Android video-downloader MVP built with Kotlin, Jetpack Compose, WebView, Kotlin Coroutines, and WorkManager.

## Included

- Browser home inspired by the provided reference: address bar, quick actions, disclaimer, and search-engine shortcuts.
- Real multi-tab browsing with a tab switcher, independent page/navigation state, safe close behavior, and background WebView pausing.
- Persistent browsing history and bookmarks, including reopen, remove, and clear actions.
- Persistent browser settings for Google, DuckDuckGo, Bing, or Yahoo search; JavaScript; cookies; and desktop-site mode.
- Browser page actions for refresh/stop, bookmark, share, copy URL, and opening the page in another browser.
- Bottom navigation with Home, Downloading, and Videos tabs.
- Thread-safe WebView media sniffing from network requests, download callbacks, dynamic HTML `video`/`source` elements, performance entries, and embedded media JSON.
- Recognition of signed CDN media URLs without file extensions (including common public TikTok CDN URL shapes).
- Direct TikTok share-page resolution: pasting a public TikTok link extracts its signed `playAddr` and preserves the matching session cookies required by the CDN.
- TikTok candidates show the MP4 suffix, vertical resolution, duration, and exact file size from a one-byte CDN metadata probe before download.
- Google is the default address-bar search engine; the selected engine is remembered. 24h article pages resolve the real HLS manifest hidden behind their blob-based player.
- Kênh14 article pages prefer the original `data-vid` MP4 and use the article headline as the saved file name instead of internal CDN identifiers.
- Direct HTTP(S) video downloads with foreground notifications, progress, cancellation, and Range-based resume.
- VOD HLS (`.m3u8`) downloads with automatic selection of the highest-bandwidth variant, including standard AES-128 playlists with a publicly accessible key.
- Public MediaStore library at `Internal storage/Movies/BrightFetch`, with open, share, and delete actions.
- Output names are normalized to supported video extensions; non-video suffixes such as `.bin` or `.dat` are replaced from the detected MIME type.

## Safety and MVP limitations

- Only download media you own or are authorized to download.
- YouTube, DRM, paywalled content, nonstandard HLS encryption, live HLS, and byte-range HLS are intentionally unsupported.
- This MVP identifies common media URLs by extension or browser download MIME type. Sites that hide URLs behind blobs, DRM, or custom APIs need a site-specific integration.
- App-specific deep links are kept inside the browser flow so websites cannot unexpectedly switch to an installed native app.
- HLS transport streams are decrypted when needed and remuxed to `.mp4` without re-encoding; DRM systems such as Widevine and transcoding remain unsupported.

## Build

Open the folder in Android Studio or run:

```bash
./gradlew assembleDebug
```

The debug APK will be written to `app/build/outputs/apk/debug/app-debug.apk`.
