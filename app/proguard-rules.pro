# Reglas de R8 (compilación release).
# Las librerías (Media3, Coil, OkHttp, Compose, Tink) traen sus propias reglas; aquí solo lo que falta.

# Tink (usado por security-crypto) menciona anotaciones que no se incluyen en la app.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Errores con número de línea legible.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Decodificador de audio FFmpeg: la parte nativa (libffmpegJNI.so) busca estas clases y métodos por su nombre.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.decoder.SimpleDecoderOutputBuffer { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
