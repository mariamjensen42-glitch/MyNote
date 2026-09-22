# R8 keep rules for the release build.
#
# This file is intentionally close to empty: Hilt, Room, Compose, Navigation and DataStore all ship
# their own consumer rules inside their AARs, and the app uses no reflection of its own — no
# serialization, no dynamic class loading, no annotation scanning at runtime. The default
# `proguard-android-optimize.txt` plus those consumer rules are therefore sufficient.
#
# Keep rules belong here only when a build actually fails or misbehaves, and each one should say
# what breaks without it, so the next person can tell whether it is still needed.

# Room generates an implementation per @Dao and looks it up by name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep the annotated Room/Hilt members' names out of the obfuscator's way when stack traces are
# read from a release crash report. R8 still shrinks and optimises them.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
