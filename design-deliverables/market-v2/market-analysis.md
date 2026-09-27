# Droply — market-first product and UI direction

Date: 2026-09-23

## Brief

This concept is intentionally independent from the existing `video-downloader-mvp` information architecture, visual language, and implementation constraints. It is based on current Google Play market patterns and Material 3 Expressive guidance.

## Market signals

- [Video Downloader — InShot Inc.](https://play.google.com/store/apps/details?id=video.downloader.videodownloader): 100M+ installs; familiar browser → auto-detect → quality → manager → offline-player flow. Reviews repeatedly criticize disruptive ads and excessive permission prompts.
- [1DM: Browser & Video Download](https://play.google.com/store/apps/details?id=idm.internet.download.manager): 50M+ installs; strong pause/resume, background queue, batch processing, and error recovery. The tradeoff is dense menus, technical options, and intrusive tips.
- [Private Browser & Downloader](https://play.google.com/store/apps/details?id=instagram.story.reels.photo.videodownloader.saver): combines browsing, detection, a private folder, and playback. Reviews value quality selection but criticize delay, ad load, and unclear original-quality handling.

The market mental model is stable:

**Capture → Detect → Choose → Track → Enjoy**

## Product concept

**Droply — a calm offline media inbox.**

Droply is not positioned as an old-style browser with a download button. It is a media-first place to bring in permitted links, see exactly what is happening, and watch saved content offline.

### Principles

1. One clear hero action per screen.
2. Paste is always user-initiated; the app never silently reads the clipboard.
3. Detection is a visible, contextual dock with source, duration, and item count.
4. Quality choices use human language: Best, Data saver, and Audio.
5. Queue states are honest: Queued, Downloading, Finishing, Verifying, Ready.
6. Every failure names the cause and offers a recovery action.
7. Library is media-first and uses a two-column grid with search, filters, and duration.
8. No social-platform logos, borrowed UI, copyrighted thumbnails, or unsupported download claims.

## Navigation

- **Home:** Paste, Share to Droply, and Browse privately.
- **Downloads:** Active queue and completed history.
- **Library:** Saved media and player entry.
- **Settings:** avatar/action in the top app bar, not a fourth navigation destination.

Browser, media picker, quality sheet, item detail, and player are nested flows.

## Primary journeys

### Paste link

Home → Paste link → Validate → Detect → Choose quality → Confirm → Downloads → Ready offline → Library/player.

### Browse privately

Home → Browser → Media detected dock → Media picker → Quality sheet → Queue.

### Android share sheet

Share to Droply → Import preview → Detect → Choose quality → Download.

### Failure recovery

- Network lost → **Try again**
- Link expired → **Open page to refresh**
- Storage low → **Free up space**
- Saved file missing → **Remove from Library**

## Visual system

- Material 3 Expressive, edge-to-edge and adaptive.
- Primary: `#6558F5`; primary container: `#E9E5FF`.
- Secondary: `#00A98F`; progress accent: `#C7F464`.
- Background: `#F7F7FC`; surface: `#FFFFFF`; surface container: `#EEF0F8`.
- Ink: `#171821`; secondary text: `#6E7180`; error: `#DC3F56`.
- Roboto Flex with a 32–36sp expressive headline and 15–16sp body copy.
- Hero radius 28–32dp, card radius 20dp, field/sheet radius 24dp, thumbnail radius 16dp.
- Floating pill navigation and restrained tonal elevation; no heavy glass or shadow stacks.

## Signature interaction

The download action morphs from a filled button into a progress orb and finally a completion check. Detection pulses once, thumbnail transitions into the player, and motion respects reduced-motion settings.

## Compliance copy

> Only save content you own or have permission to download.

The product and store listing should not claim support for “any website,” YouTube downloading, or copyright circumvention. See [Google Play intellectual-property policy](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en).

## Platform references

- [Material 3 for Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Material 3 Expressive Android announcement](https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/)
- [Android edge-to-edge guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge)
