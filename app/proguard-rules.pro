# DNSSD
-keep class com.github.druk.dnssd.** { *; }

# Moshi
# Keep name of @JsonClass type to lookup generated adapter
-keepnames @com.squareup.moshi.JsonClass class *

-keepclassmembers,allowobfuscation class * {
  @com.squareup.moshi.Json <fields>;
}
-if @com.squareup.moshi.JsonClass class *
-keepclassmembers,allowobfuscation class <1> {
    <fields>;
}

# Keep generated JsonAdapter for @JsonClass type
-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter {
    <init>();
    <init>(...);
}