# ProGuard rules for Meeting App
# Keep LiveKit WebRTC internals if minified
-keep class org.webrtc.** { *; }
-keep class livekit.org.webrtc.** { *; }
-keep class io.livekit.** { *; }
