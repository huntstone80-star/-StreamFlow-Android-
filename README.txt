═══════════════════════════════════════════
 NovaFame Android TV APK v2.0
═══════════════════════════════════════════
 Features:
 - Native ExoPlayer (no intent:// jump, seamless playback)
 - TV remote d-pad controls (seek, volume, play/pause)
 - Auto-play next episode when current one finishes
 - WebView for browsing

 Controls in ExoPlayer:
   ← →  Seek 10s
   ↑ ↓  Volume
   OK   Play/Pause
   ◀ Back to browsing

 Your server URL: http://192.168.100.23:3001

 If this IP changes, edit MainActivity.java line 142 and rebuild.

═══════════════════════════════════════════
 AUTO BUILD (GitHub Actions)
═══════════════════════════════════════════
 Push to main branch → auto-builds signed APK
 APK released at: https://github.com/huntstone80-star/-StreamFlow-Android-/releases/tag/novafame-latest

═══════════════════════════════════════════
 BUILD LOCALLY
═══════════════════════════════════════════
 1. cd app && keytool -genkey -v -keystore novafame.keystore -alias novafame -keyalg RSA -keysize 2048 -validity 10000 -storepass novafame123 -keypass novafame123 -dname "CN=NovaFame, OU=App, O=NovaFame, L=Unknown, ST=Unknown, C=US"
 2. ./gradlew assembleRelease
 3. APK at app/build/outputs/apk/release/NovaFame-Android.apk
