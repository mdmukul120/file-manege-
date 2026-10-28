<div align="center">

# ✨ AI File Organizer

**Your phone's files, sorted automatically by Google Gemini.**

![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Gemini](https://img.shields.io/badge/Powered%20by-Gemini-4285F4?logo=google&logoColor=white)
![Build](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)

### [⬇️ Download the latest APK](../../releases/latest)

</div>

---

## 🚀 Features

| | |
|---|---|
| 🤖 **AI sorting** | Gemini decides the category of each file from its name and type |
| 🗂️ **Clean folders** | Files go to `Organized/Images`, `Videos`, `Audio`, `Documents`, `Archives`, `Apps`, `Other` |
| 🧹 **Duplicate finder** | Copies like `photo (1).jpg` are moved to `Organized/Duplicates` |
| ↩️ **One-tap undo** | Restore everything the last run moved |
| ⏰ **Auto mode** | Runs once a day in the background |
| 🔒 **Private** | Only file names and sizes are sent to Gemini, never file contents |
| 🛡️ **Safe** | Nothing is ever deleted |
| 📴 **Offline fallback** | No internet or no key? Falls back to extension-based rules |

## 📲 Install

1. Open **[Releases](../../releases/latest)** and download the `.apk`
2. Allow "Install unknown apps" for your browser when asked
3. Open the app → **Grant file access** → paste your Gemini API key → **Organize now**

Works on **Android 5.0 and newer** (all major brands: Samsung, Xiaomi, Oppo, Vivo, Realme, Google Pixel, Motorola, OnePlus, Infinix, Tecno…). iPhone is not supported.

> 🔑 Get a free API key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey). Never share it publicly or commit it to code.

## ⚙️ Automatic build & release

Every push to `main` builds the app and publishes a new GitHub Release (`v1.0.<run number>`) with the APK attached. No setup needed.

**Optional – stable signing key** (so updates install over old versions even after reinstalling CI):

```bash
keytool -genkeypair -v -keystore release.keystore -alias key -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # paste into the secret below
```

Add repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## 🛠️ Push to GitHub in one command

```bash
gh auth login
./push-to-github.sh my-file-organizer
```

## ❓ Good to know

- Android may pause background apps on some brands (Xiaomi, Oppo, Vivo). Set the app's battery mode to **Unrestricted** for reliable daily runs.
- Android requires the special **All files access** permission to move files; the app asks for it on first launch.

<div align="center">Made with ❤️ and Gemini</div>
