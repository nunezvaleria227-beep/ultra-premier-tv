# Ultra Premier TV — app para Fire TV

App nativa de Android (Kotlin + Jetpack Compose + Media3/ExoPlayer) para Fire TV y Fire TV Stick.
Inicia sesión con **usuario y contraseña** contra un portal **Xtream Codes** y reproduce:

- **TV en vivo** por categorías, con cambio de canal y guía "Ahora / Después" (si el servidor la tiene).
- **Películas** con póster, descripción y reproducción.
- **Series** con temporadas, episodios y paso automático al siguiente episodio.

Funciona con Fire OS 6 en adelante (Android 7.1+). Está pensada para el control remoto (D-pad), sin pantalla táctil.

> Es solo un reproductor: úsala con servicios y contenido que tengas derecho a ver.

---

## 1. Configurar la URL del portal

Abre `app/src/main/java/com/example/mitvplayer/Config.kt`:

```kotlin
const val PORTAL_URL: String = "http://gazytv.vip:8880"
```

- **Con URL puesta (como viene ahora):** la app solo pide usuario y contraseña.
- **Sin URL (vacío):** la pantalla de inicio de sesión también pide la URL del servidor, una sola vez (la app la recuerda).

Si el portal cambia de dirección, se cambia solo esa línea y se vuelve a compilar.

**Ojo:** la URL queda dentro del APK. Quien tenga el APK o su código de Downloader puede verla (y si subes el código a un repositorio público, también ahí). Tu usuario y contraseña nunca van en el código: se escriben en pantalla.

## 2. Obtener el APK (dos caminos)

### A) En la nube con GitHub (no necesitas Android Studio)

1. Crea un repositorio **público** en GitHub y sube esta carpeta.
2. Crea una etiqueta, por ejemplo `v1.0` (en GitHub: *Releases → Draft a new release → Choose a tag → crear `v1.0` → Publish*).
3. La pestaña **Actions** compila el proyecto (`.github/workflows/build-apk.yml`) y adjunta `UltraPremierTV.apk` a ese release. Tarda unos minutos.
4. El enlace directo y estable al APK es:

```
https://github.com/TU_USUARIO/TU_REPOSITORIO/releases/latest/download/UltraPremierTV.apk
```

### B) En tu computadora con Android Studio

1. Instala **Android Studio** (Ladybug o más reciente) y abre esta carpeta; deja que Gradle sincronice.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**, o en terminal: `./gradlew assembleDebug`
3. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Código de Downloader

Los códigos de Downloader (los números que se escriben en la app) **los genera AFTVnews**, no la app ni yo: se crean a partir de una URL en su acortador.

1. Ten el APK en una **URL directa y pública** (por ejemplo, el enlace de GitHub de arriba).
2. Entra a **https://go.aftvnews.com**, pega esa URL, resuelve el captcha y envía. Te da un **código numérico** (y un enlace corto `aftv.news/código`).
3. En el Fire TV abre **Downloader**, escribe el código en el campo de URL, pulsa **Go** y, al terminar la descarga, **Instalar**.

Importante:
- Toda URL enviada a ese acortador es **pública**: cualquiera con el código puede descargar el APK, y el APK lleva dentro la URL del portal (ver punto 1).
- Si prefieres no usar código, en Downloader también puedes escribir la URL completa.
- Cada compilación del APK final (release) se firma con una llave nueva, creada en GitHub y que nunca se guarda, así que **para actualizar la app hay que desinstalar la versión anterior** antes de instalar la nueva.

## 4. Preparar el Fire TV

En el Fire TV: **Ajustes → Mi Fire TV → Opciones para desarrolladores** y permite instalar apps de orígenes desconocidos para **Downloader**
(en versiones antiguas es un solo interruptor: *Apps de orígenes desconocidos*).
Si no ves "Opciones para desarrolladores": Ajustes → Mi Fire TV → Acerca de → pulsa 7 veces sobre el nombre del dispositivo.

Alternativa con ADB (misma red Wi‑Fi, con *Depuración ADB* activada):

```
adb connect IP_DEL_FIRE_TV:5555
adb install -r UltraPremierTV.apk
```

La app aparece en **Tus apps y juegos**.

### Aviso de Play Protect

Play Protect (Google) existe en celulares, tablets y Android TV; **el Fire TV no lo tiene**. En un dispositivo Android, una app instalada fuera de una tienda puede mostrar "app no reconocida" o pedir analizarla. Pulsa **Más detalles → Instalar de todos modos** (o **Analizar app**). Para que no aparezca nunca habría que publicar la app en Google Play o en Amazon Appstore.

## 5. Control remoto

| Dónde | Botón | Acción |
|---|---|---|
| Menús | ◀ ▶ ▲ ▼ / OK | Moverse y elegir |
| Menús | Atrás | Volver (en Home sale de la app) |
| TV en vivo | ▲ / CH+ | Canal siguiente |
| TV en vivo | ▼ / CH− | Canal anterior |
| TV en vivo | OK | Muestra nombre del canal y la guía |
| Películas / series | OK, ◀ ▶, Play/Pausa | Controles de Media3 (adelantar, pausar, audio y subtítulos) |

Si un canal se corta o no abre, en **Cuenta** puedes cambiar el formato de TV en vivo entre HLS (m3u8) y MPEG‑TS.

## 6. Estructura del proyecto

```
.github/workflows/build-apk.yml   Compila el APK en GitHub
app/src/main/res/
├── drawable-xhdpi/banner.png     Banner del launcher y logo del login (de tu logotipo)
└── drawable-nodpi/ic_launcher.png Ícono de la app (de tu logotipo)
app/src/main/java/com/example/mitvplayer/
├── Config.kt            URL del portal y User-Agent
├── MiTvApp.kt           Application: crea el repositorio
├── MainActivity.kt      Navegación entre Login / Home / detalles
├── PlayerActivity.kt    Reproductor (ExoPlayer) y cambio de canal
├── data/
│   ├── XtreamApi.kt     Cliente de player_api.php (login, canales, VOD, series, EPG)
│   ├── Repository.kt    Sesión en memoria + caché por categoría
│   ├── SessionStore.kt  Guarda la sesión cifrada (Android Keystore)
│   └── Models.kt
└── ui/
    ├── AppViewModel.kt  Estado de la app y acciones
    ├── BrowseState.kt   Categorías + contenido (scroll y foco se conservan)
    ├── LoginScreen.kt
    ├── HomeScreen.kt    Pestañas En vivo / Películas / Series / Cuenta
    ├── DetailScreens.kt Detalle de película y de serie
    ├── Components.kt    Tarjetas y botones con foco para TV
    └── Theme.kt
```

El identificador de la app (`applicationId`) es `com.ultrapremier.tv`; el paquete interno del código sigue siendo `com.example.mitvplayer` y no afecta a los usuarios.

## 7. Notas de seguridad y límites

- **Credenciales:** si marcas "Recordar sesión", se guardan cifradas con el Android Keystore. Cerrar sesión las borra. Las copias de seguridad de Android están desactivadas.
- **HTTP sin cifrar:** muchos portales IPTV usan `http://`, por eso la app permite tráfico no cifrado (`usesCleartextTraffic`). Si tu portal tiene `https://`, úsalo. Para exigir HTTPS, quita ese atributo de `AndroidManifest.xml`.
- **Conexiones simultáneas:** la mayoría de las cuentas permite una sola. La app abre solo un stream a la vez; si ves "máximo de conexiones", cierra la reproducción en otro dispositivo.
- **Todavía no incluye:** búsqueda, favoritos, "continuar viendo", guía EPG completa en cuadrícula ni control parental.
- El proyecto se entregó **sin compilar** (el entorno donde se escribió no tiene acceso a los repositorios de Android). Si Android Studio o GitHub Actions marcan algún error, hay que corregirlo; el código está escrito contra las versiones fijadas en `app/build.gradle.kts`.
