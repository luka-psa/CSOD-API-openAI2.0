# kotlinx.serialization: keep generated serializers for tool args/results.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ONNX Runtime (smarter listening) is reached through JNI: keep its classes whole.
-keep class ai.onnxruntime.** { *; }
