# NovaPlayer Prime

NovaPlayer Prime is a customized build of **[NOVA Video Player](https://github.com/nova-video-player/aos-AVP)**,
the open-source video player by the NOVA developers (Courville Software and contributors), itself derived from
the Archos Video Player Community Edition.

**Forked and customized by [tasin-py](https://github.com/tasin-py).** All credit for the player, the media
engine and every existing feature goes to the original NOVA developers. This fork only adds features on top;
nothing from NOVA was removed. Licensed under the Apache License 2.0, like NOVA (see `LICENSE.txt`).

Support the original developers: <https://liberapay.com/NovaVideoPlayer/donate>
Support tasin-py: <https://www.supportkori.com/tasinpy>

## What Prime adds

- **Stream** (Android TV: *Network* > *Stream*): type or paste a link and play it. It handles direct
  http(s) files (mkv, mp4…), HLS `.m3u8`, DASH, rtsp/rtmp/udp, and Google Drive, Dropbox and
  OneDrive share links (converted to direct links). Played links stay in *Recent streams* for 30 days.
- **Settings > NovaPlayer Prime**: credits, *Support tasin-py* (socials, Buy me a coffee QR),
  *Support the original NOVA developers*, and **Check for updates**.
- **One-click updates from GitHub Releases**: the app checks this repo every 6 hours. When a newer build
  exists, the Settings icon gets a red dot and *Update now* installs it.

## How the code is organised

| Repo | What |
| --- | --- |
| `tasin-py/NovaPlayer-Prime` (this repo, branch `prime`) | build setup: `prime.xml` manifest + `.github/workflows/prime-release.yml` |
| `tasin-py/aos-Video` (branch `prime`) | the app UI, where all Prime code lives (`src/main/java/com/archos/mediacenter/video/prime/`) |
| everything else | pulled unchanged from `nova-video-player` |

Prime code is kept in its own `prime` package and its own resource files (`strings_prime.xml`).
NOVA files only get a few small, clearly marked hooks (`// NovaPlayer Prime`), so merging NOVA updates stays easy.

## Building

Native code needs Linux, so builds run on GitHub Actions: every push to `prime` (or *Actions > NovaPlayer Prime
release > Run workflow*) builds signed APKs and publishes a release tagged `prime-<versionCode>`.

Signing secrets (one-time): see `signing/SECRETS.txt` on the maintainer's PC (never committed).

## Pulling in NOVA updates

```bash
cd Video
git fetch https://github.com/nova-video-player/aos-Video.git v6.6
git merge FETCH_HEAD
git push origin prime
```

When NOVA moves to a new branch (for example v6.7), update the revisions in `prime.xml` to match their newest manifest.
