# Reglas de R8 (compilación release).
# Las librerías (Media3, Coil, OkHttp, Compose, Tink) traen sus propias reglas; aquí solo lo que falta.

# Tink (usado por security-crypto) menciona anotaciones que no se incluyen en la app.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Errores con número de línea legible.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
