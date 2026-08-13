# Root CA Toggle hardened release rules.
# R8 may freely rename, merge, inline and repackage application implementation classes.

-allowaccessmodification
-repackageclasses t
-adaptclassstrings
-renamesourcefileattribute T

# Android launches this class from AndroidManifest.xml. Keep only the entry-point name;
# implementation classes and methods remain eligible for R8 optimization/obfuscation.
-keep public class com.example.rootcatoggle.MainActivity { public <init>(); }

# NativeCrypto is an Android/ROM class loaded only by Class.forName(). It is not bundled
# with this APK; suppress missing-class diagnostics while preserving the literal lookup.
-dontwarn com.android.org.conscrypt.NativeCrypto
-dontwarn org.conscrypt.NativeCrypto

# Do not publish app/build/outputs/mapping/release/mapping.txt with public APK artifacts.
