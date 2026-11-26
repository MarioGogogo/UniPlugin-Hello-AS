# Keep KeystoreModule
-keep class io.dcloud.uniplugin.KeystoreModule { *; }

# Keep Android Keystore related classes
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# Keep fastjson
-keep class com.alibaba.fastjson.** { *; }
-dontwarn com.alibaba.fastjson.**
