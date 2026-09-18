# Mera Thikaana — Android App

Discover Ranchi's waterfalls, viewpoints, food spots and hidden gems — Bhukkad or Ghumakkad, find your thikana.

## 🚀 Building the APK via GitHub Actions (Automated CI)

This repository includes an automated GitHub Actions workflow (`.github/workflows/build-apk.yml`) that compiles the APK automatically.

### Option 1: Trigger GitHub Actions Build
1. Push your repository to GitHub (or push changes to `main` / `master`).
2. In your GitHub repository, click on the **Actions** tab.
3. Select the **Build Android APK** workflow on the left menu.
4. Click **Run workflow** > select the branch > click the green **Run workflow** button.
5. Once the run finishes (usually 2–3 minutes), scroll to the **Artifacts** section at the bottom of the page.
6. Download **`MeraThikaana-Debug-APK`** (contains `app-debug.apk`) directly to your phone or computer.

---

## 💻 Building the APK Locally

### Prerequisites
- **Java JDK**: Version 21 (Temurin / OpenJDK)
- **Android SDK**: Build tools and platform API 36

### Build Commands:
```bash
# 1. Ensure gradlew is executable
chmod +x ./gradlew

# 2. Decode the debug keystore (if not already present)
base64 -d debug.keystore.base64 > debug.keystore

# 3. Create .env file
cp .env.example .env

# 4. Build the debug APK
./gradlew assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```
