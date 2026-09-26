# Study Lockdown

A strict-allowlist focus app for Android 14 / Nothing OS. See the project
structure under `app/src/main/` for the Accessibility Service blocker, the
Quick Settings tile, the emergency-pass system, and the UI.

## Get an installable APK (no local Android SDK needed)

This repo includes `.github/workflows/build-apk.yml`, which builds a debug
APK on GitHub's servers every time you push.

1. Create a new **public or private** GitHub repository.
2. Push this entire folder to it:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. On GitHub, open the **Actions** tab. The "Build APK" workflow runs
   automatically on push (or click **Run workflow** to trigger it manually).
4. When the run finishes (green check, a few minutes), open it and scroll to
   **Artifacts** at the bottom. Download `study-lockdown-debug-apk.zip`.
5. Unzip it to get `app-debug.apk`, transfer it to your phone (email,
   Google Drive, `adb push`, etc.), and install it. You'll need to allow
   "Install unknown apps" for whichever app you use to open the file.

This produces a **debug-signed** APK — perfectly fine for installing on your
own device, but not for Play Store distribution (that needs a release
signing key, which is out of scope here).

## Build locally instead

If you'd rather use Android Studio:

1. Unzip the project and open the folder in Android Studio (Giraffe or
   newer).
2. Let Gradle sync — it will generate the wrapper and pull dependencies
   automatically.
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or just press
   **Run** with your phone connected via USB debugging.

## After installing on the phone

1. Open Study Lockdown and tap **ENABLE ACCESSIBILITY SERVICE** — this is
   required for the app blocker to function.
2. In Nothing OS battery settings, disable battery optimization for Study
   Lockdown so the Accessibility Service isn't killed in the background.
3. Set a study timer or tap **START LOCKDOWN**, and add the Quick Settings
   tile from the shade's edit screen if you want one-tap access.
