# Morning checklist: what I need from you

All the code is written and committed locally (branch `prime`). Nothing has been pushed yet.
These steps need your GitHub account. Total time: about 10 minutes, plus about 1 hour for the first cloud build.

## 1. Fork the app UI repo (1 minute)
Open https://github.com/nova-video-player/aos-Video and click **Fork**.
- Owner: `tasin-py`, name: `aos-Video` (keep that exact name)
- **Untick** "Copy the default branch only" (we start from their `v6.6` branch)

## 2. Add the 4 signing secrets (3 minutes)
Open `signing/SECRETS.txt` in this folder. On GitHub go to
**NovaPlayer-Prime > Settings > Secrets and variables > Actions > New repository secret** and add the four
secrets listed there (`PRIME_KEYSTORE_BASE64` is the whole content of `signing/PRIME_KEYSTORE_BASE64.txt`).

**Back up the `signing` folder** (Google Drive, USB stick…). If that key is lost, the app can't be updated anymore.

## 3. Push (I can do this for you: just say "push")
```bash
cd "F:\Claude Devs\NovaPlayer Prime\Video"
git push https://github.com/tasin-py/aos-Video.git prime
cd ..
git push origin prime
```
The second push starts the GitHub Actions build (**Actions** tab). About 1 hour the first time.
When it's green, a release `prime-<number>` appears under **Releases** with the APKs.

## 4. Install on the TV (one time only)
1. **Back up first if you care about NOVA's watch history:** Settings > Media library backup in NOVA (optional).
2. Uninstall the NOVA from the Play Store (same app ID, different signature: Android won't install over it).
3. Install the APK from the release (most TVs: `armeabi-v7a`; newer ones: `arm64-v8a`). Use the Downloader app with the release URL, or `adb install`.
From then on, updates come from **Settings > NovaPlayer Prime > Update now**.

## 5. Things to tell me
- Your socials (Facebook, YouTube, Instagram…) for the Support popup: currently GitHub only.
- Whether you want the app's name on the TV launcher changed from "Nova" to "NovaPlayer Prime" (not changed for now, since you asked not to change existing things).

## About "no popup" updates (Android rules, not something an app can bypass)
- The app itself shows no popups: progress shows in the Settings line.
- **Android 12 and newer:** the first update asks once to allow installs from this app (system screen). After that, updates install silently.
- **Android 11 and older** (many TVs): Android always shows its own one-click "Update / Install" confirmation. No app can skip it without root.
- After an update the app closes; open it again.
