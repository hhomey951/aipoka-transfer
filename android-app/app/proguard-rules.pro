# Most libraries here (OkHttp, ML Kit, CameraX, Compose) ship their own consumer
# ProGuard rules, so no manual keeps are needed for them.

# androidx.security-crypto is backed by Google Tink, which resolves its key
# templates and protobuf-lite message classes via reflection — R8 can't see
# those references and would strip them, breaking EncryptedSharedPreferences
# (where the pairing token and watermark live) only at runtime in release builds.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# javax.annotation references inside Tink/OkHttp are compile-time only.
-dontwarn javax.annotation.**
