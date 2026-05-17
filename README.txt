═══════════════════════════════════════════
 Novafame Android TV APK v2.0
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

 If this IP changes, edit MainActivity.java line 127 and rebuild.

═══════════════════════════════════════════
 BUILD WITH ANDROID STUDIO (Recommended)
═══════════════════════════════════════════
 1. Open Android Studio → "Open existing project"
 2. Select this folder (Novafame-Android)
 3. Let Gradle sync (downloads dependencies)
 4. Build → Build Bundle(s) / APK → Build APK
 5. APK at app/build/outputs/apk/debug/
 6. Sideload on TV

═══════════════════════════════════════════
 ONLINE APK BUILDER
═══════════════════════════════════════════
 Zip this folder and upload to freewebtoapk.com
 Use settings:
 - URL: http://192.168.100.23:3001
 - Orientation: Landscape
 - External Links: Open in browser
