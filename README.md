<div align="center">

<img src="docs/banner.png" alt="Nova Player Plus by Tasin" width="560">

# Nova Player Plus

**A premium Android TV video player, built on NOVA Video Player.**
Stream any link, browse server folders, bookmark what you love, and update in one click.

[![Latest release](https://img.shields.io/github/v/release/tasin-py/NovaPlayer-Prime?label=latest&logo=github&color=0b6aa8)](https://github.com/tasin-py/NovaPlayer-Prime/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/tasin-py/NovaPlayer-Prime/prime-release.yml?branch=prime&label=build&logo=githubactions&logoColor=white)](https://github.com/tasin-py/NovaPlayer-Prime/actions/workflows/prime-release.yml)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Android-3ddc84?logo=android&logoColor=white)](#install)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE.txt)
[![Based on NOVA](https://img.shields.io/badge/based%20on-NOVA%20Video%20Player-1565c0)](https://github.com/nova-video-player/aos-AVP)

[**Download**](https://github.com/tasin-py/NovaPlayer-Prime/releases/latest) ·
[What's new](#whats-new-in-this-fork) ·
[Features](#features) ·
[Themes](#themes) ·
[Install](#install) ·
[Build](#building) ·
[Credits](#credits)

</div>

---

## What's new in this fork

| | Feature | What it does |
|:-:|---|---|
| 🎨 | **Six premium themes** | Noir Gold, Royal Burgundy, Emerald Velvet, Midnight Silver, Mahogany Copper and Imperial Purple, alongside NOVA's Blue and Black |
| 🔗 | **Stream any link** | Paste a video link and play it: direct files, HLS, DASH, RTSP/RTMP, Google Drive, Dropbox, OneDrive |
| 📂 | **Browse folder links** | Server folder listings (h5ai, nginx, Apache) open as a browsable list of folders and videos |
| 🎬 | **Details before playing** | Every video shows its title, size and server first, so nothing starts by surprise |
| 🔖 | **Bookmarks** | Save folders and videos until you remove them, from the Stream screen or the home screen |
| 🏠 | **Bookmarked on the home screen** | A *Bookmarked* folder in **Network & Files**, now the 2nd row for quick access |
| ⬆️ | **One-click updates** | The app checks GitHub, shows a red dot on Settings, and installs the new version itself |
| ✨ | **Nova Player Plus branding** | New name, home-screen banner and title logo, in NOVA's original font and design |

> Nothing from NOVA was removed or changed. Every original feature and setting works exactly as before.

## Features

<table>
<tr>
<td width="50%" valign="top">

### 🔗 Stream

**Network › Stream** on Android TV.

- Type or **Paste** a link, then **Open**
- Direct files (mkv, mp4…), HLS `.m3u8`, DASH, RTSP, RTMP, UDP
- Google Drive, Dropbox and OneDrive share links are turned into direct links
- **Recent streams** are kept for 30 days

</td>
<td width="50%" valign="top">

### 📂 Folders and 🔖 bookmarks

- Folder links open as a list, with sub-folders first
- Each video shows its name, size and server, with **Play** and **Bookmark**
- **Bookmark this folder** sits at the top of every folder
- Bookmarks never expire. Find them on the Stream screen and in **Network & Files › Bookmarked**

</td>
</tr>
<tr>
<td width="50%" valign="top">

### ⬆️ Updates

- **Settings › Nova Player Plus › Check for updates**
- Checks automatically at most every 6 hours, through github.com links rather than the rate-limited API
- Downloads the right APK for your device and installs it
- Downloads only from GitHub, over HTTPS

</td>
<td width="50%" valign="top">

### 💛 Credits and support

- **Settings › Nova Player Plus** shows the credits
- **Support tasin-py**: socials and a *Buy me a coffee* QR code
- **Support the original NOVA developers**: a link to their donation page

</td>
</tr>
</table>

## Themes

**Preferences › Theme.** Each theme recolors the background, menu, tiles, highlights, progress bars and settings screens.

<div align="center">
<img src="docs/themes.png" alt="The six premium themes" width="900">
</div>

| Theme | Mood |
|---|---|
| **Noir Gold** | Deep black with gold, like a classic cinema |
| **Royal Burgundy** | Wine red with champagne gold |
| **Emerald Velvet** | Dark emerald green with soft gold |
| **Midnight Silver** | Midnight navy with silver |
| **Mahogany Copper** | Dark wood with copper |
| **Imperial Purple** | Royal purple with gold |

## Install

1. Open the [**latest release**](https://github.com/tasin-py/NovaPlayer-Prime/releases/latest).
2. Download the APK for your device:

   | Device | File |
   |---|---|
   | Most Android TVs (32-bit) | `prime-armeabi-v7a.apk` |
   | Newer TVs, phones and tablets (64-bit) | `prime-arm64-v8a.apk` |
   | Emulators and PCs | `prime-x86_64.apk` / `prime-x86.apk` |

3. Install it. After that, updates arrive through **Check for updates**.

> **Replacing NOVA:** Nova Player Plus uses the same app ID as NOVA (`org.courville.nova`), but it is signed with a different key.
> Uninstall NOVA first. Your NOVA library and settings won't carry over.

## How the code is organised

| Where | What |
|---|---|
| branch `prime` (this one) | Build setup: the `prime.xml` manifest, the release workflow and this README |
| branch `prime-video` | The app itself (NOVA's `aos-Video`). Fork code lives in `src/main/java/com/archos/mediacenter/video/prime/` |
| branch `nova` | An untouched copy of NOVA's original repository |
| everything else | Pulled unchanged from [nova-video-player](https://github.com/nova-video-player) |

Fork code lives in its own `prime` package and its own resource files (`strings_prime.xml`, `colors_prime_themes.xml`, `styles_prime_themes.xml`).
NOVA's files only get a few small hooks marked `// NovaPlayer Prime`, so merging NOVA updates stays easy.

## Building

The native code needs Linux, so releases are built on **GitHub Actions**:

- Every push to `prime` builds signed APKs and publishes a release tagged `prime-<versionCode>`. You can also use *Actions › NovaPlayer Prime release › Run workflow*.
- The workflow waits for a new version number, so two builds close together never share a release.
- Signing uses four repository secrets: `PRIME_KEYSTORE_BASE64`, `PRIME_KEYSTORE_PASSWORD`, `PRIME_KEY_ALIAS` and `PRIME_KEY_PASSWORD`.
  Always use the same keystore. Android refuses updates signed with a different key.

To build locally, follow NOVA's instructions in [README-NOVA.md](README-NOVA.md#building) and use `-m prime.xml` with `repo init`.

### Pulling in NOVA updates

```bash
cd Video
git fetch https://github.com/nova-video-player/aos-Video.git v6.6
git merge FETCH_HEAD
git push https://github.com/tasin-py/NovaPlayer-Prime.git HEAD:prime-video
```

When NOVA moves to a new branch (for example v6.7), update the revisions in `prime.xml` to match their newest manifest.

## Credits

Nova Player Plus is a fork of **[NOVA Video Player](https://github.com/nova-video-player/aos-AVP)** by the NOVA developers
(Courville Software and contributors), which is itself derived from the Archos Video Player Community Edition.
All credit for the player, the media engine and every original feature belongs to them.
NOVA's original README is kept in [README-NOVA.md](README-NOVA.md).

Forked and customized by **[tasin-py](https://github.com/tasin-py)**.

<div align="center">

[![Support tasin-py](https://img.shields.io/badge/support-tasin--py-ffdd00?logo=buymeacoffee&logoColor=black)](https://www.supportkori.com/tasinpy)
[![Support NOVA](https://img.shields.io/badge/support-NOVA%20developers-f6c915?logo=liberapay&logoColor=black)](https://liberapay.com/NovaVideoPlayer/donate)

Licensed under the [Apache License 2.0](LICENSE.txt), like NOVA.

</div>
