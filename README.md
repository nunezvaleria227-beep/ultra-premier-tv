# Ultra Premier TV — app para Fire TV y Android

App nativa de Android (Kotlin + Jetpack Compose + Media3/ExoPlayer) para Fire TV, Fire TV Stick y celulares Android.
Inicia sesión con **usuario y contraseña** contra un portal **Xtream Codes** y ofrece:

| Pantalla | Qué hace |
|---|---|
| **Inicio** | Reloj, fecha, *Búsqueda maestra*, íconos de radio, avisos, cuenta, grabaciones, ajustes y salir; tres mosaicos (TV en directo, Cine, Series) con su "última actualización" y botón de actualizar; barras de EPG, Pantalla múltiple y Catch up; fecha de expiración de la cuenta. |
| **TV en directo** | Tres paneles: categorías (con buscador y cantidades), lista de canales con el programa actual y su barra de avance, y vista previa. **OK** una vez = vista previa; **OK** otra vez = pantalla completa. |
| **Cine / Series** | Barra lateral de categorías (TODO, FAVORITOS, RECIENTEMENTE MIRADA, RECIENTEMENTE AÑADIDO y las del servidor, cada una con su cantidad) y cuadrícula de pósters con calificación. La **X** oculta las categorías. |
| **EPG** | Cuadrícula de categorías → canales con lo que dan ahora y lo que sigue. |
| **Catch up** | Canales con archivo en el servidor → día → programa ya emitido. |
| **Pantalla múltiple** | Elige entre 6 diseños y pon un canal en cada recuadro; solo uno suena a la vez. |
| **Búsqueda maestra** | Canales, películas y series a la vez (sin importar acentos ni mayúsculas). |
| **Grabaciones** | Desde la guía se puede grabar lo que queda de un programa o programar su grabación (máximo **2 horas**). Al cumplirse (o al detenerla) se guarda sola en la memoria del dispositivo, en **Películas / Ultra Premier TV**, y se puede ver desde la propia app. |
| **Favoritos e historial** | Mantén pulsado un canal/póster (o la tecla **Menú** del control) para marcarlo como favorito. |
| **Continuar viendo** | La app recuerda el minuto de cada película y episodio. Botón en el inicio con todo lo que dejaste a medias; al abrir una película aparece "Continuar (mm:ss)" y "Desde el inicio". |
| **Guía en cuadrícula** | En EPG → una categoría → "Ver en cuadrícula": canales a la izquierda y programas por horario (con "Ahora", ◀ 3 h / 3 h ▶). OK en un programa: ver el canal o **programar su grabación**. |
| **Grabaciones programadas** | Se programan desde la guía, se ven (y cancelan) en Grabaciones y se graban solas a la hora, también con la app cerrada (la sesión queda guardada hasta que pulses "Cerrar sesión"). Máximo 2 h por grabación. |
| **Grabaciones a MP4** | En Grabaciones, el botón **A MP4** convierte una grabación (.ts) a MP4 para abrirla en cualquier celular o programa. |
| **Control parental** | En Ajustes se pone un PIN de 4 números: las categorías para adultos (XXX, +18, adultos…) se esconden en listas, buscador, guía y "Continuar viendo" hasta escribir el PIN. |
| **Detalle de película/serie** | Fondo grande, sinopsis, reparto, director, botón de **tráiler** (YouTube), minuto en que vas y episodios en curso. |
| **Guía completa** | Si un canal no trae programación, toma la de su "hermano" (el mismo canal en HD / FHD / 4K del mismo país). Si ninguno tiene, la guía muestra **● En vivo** en lugar de quedar vacía (no se inventan programas). |
| **Subtítulos en español o inglés** | En cualquier película o episodio: tecla **Menú** (o botón **CC Subtítulos** arriba) → Subtítulos. Se ven los que trae el video; si el idioma elegido no viene, la app lo **busca en internet (OpenSubtitles)**, lo guarda y la próxima vez ya está. Se recuerda el idioma para los siguientes videos. Hace falta una clave gratuita de OpenSubtitles (Ajustes → Subtítulos en internet). |
| **Tamaño de imagen** | Canal, película y serie tienen tres opciones: **Normal** (completa), **Llenar la pantalla** y **Acercar** (recorta bordes). En vivo: ◀ ▶ o Menú; en películas y series: Menú → Tamaño. Se recuerda por tipo (canal / película / episodio). |
| **Lupa en el detalle** | Película y serie tienen la lupa de búsqueda arriba a la derecha. |
| **Actualizaciones** | Al abrir la app avisa si hay una versión nueva (de GitHub Releases); se descarga e instala desde la propia app. También en Ajustes → "Buscar actualización de la app". |

Funciona con Fire OS 6 en adelante (Android 7.1+). Se maneja con el control remoto (D-pad) y también con el dedo.

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

## 2. Obtener el APK

### A) En la nube con GitHub (no necesitas Android Studio)

Cada vez que se sube código a la rama `main`, la pestaña **Actions** compila el proyecto (`.github/workflows/build-apk.yml`), lo firma y publica `UltraPremierTV.apk` en **Releases**. Tarda unos 2 minutos.

El enlace directo y estable al **último** APK es:

```
https://github.com/nunezvaleria227-beep/ultra-premier-tv/releases/latest/download/UltraPremierTV.apk
```

**Actualizar sin desinstalar:** la llave de firma se crea una sola vez y GitHub la conserva entre compilaciones, así que cada versión nueva se instala **encima** de la anterior. Cada release indica en sus notas si la llave es la misma. Si alguna vez GitHub la borra (por ejemplo, tras muchos días sin compilar), esa versión pedirá desinstalar la anterior una vez; una tarea programada la mantiene viva cada 3 días.

### B) En tu computadora con Android Studio

1. Instala **Android Studio** (Ladybug o más reciente) y abre esta carpeta; deja que Gradle sincronice.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**, o en terminal: `./gradlew assembleDebug`
3. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Descarga con Downloader

- **Enlace corto:** crea uno con el acortador que prefieras (por ejemplo `is.gd/UltraPremierTV`) que apunte al enlace de GitHub de arriba. Como ese enlace siempre sirve la última versión, el corto no hay que tocarlo nunca.
- **Código numérico de Downloader:** lo genera AFTVnews en **https://go.aftvnews.com** (pega la URL, resuelve el captcha y envía).
- En el Fire TV abre **Downloader**, escribe el enlace o el código, pulsa **Go** y al terminar **Instalar**.

Toda URL enviada a un acortador es **pública**: cualquiera con el código puede descargar el APK, y el APK lleva dentro la URL del portal (ver punto 1).

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
| Menús | Atrás | Volver (en el inicio sale de la app) |
| Canales, películas, series | Mantener OK o tecla **Menú** | Añadir / quitar de favoritos |
| TV en directo | OK en un canal | Vista previa; OK otra vez = pantalla completa |
| Pantalla completa en vivo | ▲ / CH+ | Canal siguiente |
| Pantalla completa en vivo | ▼ / CH− | Canal anterior |
| Pantalla completa en vivo | OK | Muestra nombre del canal y la guía |
| Películas / series | OK, ◀ ▶, Play/Pausa | Controles de Media3 (adelantar y pausar) |
| Películas / series | **Menú** (≡) o tecla **CC** | Subtítulos (español / inglés / apagados) y Tamaño de la imagen |
| Pantalla completa en vivo | ◀ ▶ o **Menú** | Tamaño de la imagen (Normal / Llenar / Acercar) |

En el celular: tocar elige, mantener pulsado marca favorito, y en un canal en vivo a pantalla completa deslizar hacia arriba/abajo cambia de canal.

Si un canal se corta o no abre, en **Ajustes** puedes cambiar el formato de TV en vivo entre HLS (m3u8) y MPEG‑TS.

## 6. Cómo funcionan las listas

Al iniciar sesión la app descarga **completas** las listas de canales, películas y series (por eso cada categoría muestra su cantidad y la búsqueda es instantánea) y las guarda en el dispositivo. Se vuelven a descargar solas cada 12 horas, o cuando pulsas el botón de actualizar de un mosaico (o **Ajustes → Actualizar todas las listas**). La primera vez puede tardar según el tamaño de tu lista.

Las grabaciones duran como máximo 2 horas y se guardan en la carpeta **Películas / Ultra Premier TV** del dispositivo (visible en la app de Archivos o Galería; son archivos `.ts`, que se reproducen en la propia app y en VLC o MX Player). Si la señal se cae, la app reintenta unas veces; si casi no queda espacio, se detiene y conserva lo grabado. Cada grabación usa una conexión de tu cuenta; si solo tiene una, la vista previa se pausa mientras grabas. En Android 9 o anterior, si no das el permiso de almacenamiento, se guardan en la carpeta de la app (`Android/data/com.ultrapremier.tv/files/Movies/Grabaciones`).

## 7. Imágenes y logo

Todas las imágenes están en `app/src/main/res/` y se pueden cambiar por otras **con el mismo nombre** sin tocar el código:

| Archivo | Dónde se ve |
|---|---|
| `drawable/logo_ultra.xml` | Logo en vector (nítido en cualquier pantalla, incluida 4K): inicio, encabezados y pantalla de acceso |
| `drawable-nodpi/bg_home.jpg` | Fondo general de la app y de la pantalla de acceso |
| `drawable-nodpi/art_live.jpg` | Mosaico y fondo de **TV en directo** |
| `drawable-nodpi/art_movies.jpg` | Mosaico y fondo de **Cine** |
| `drawable-nodpi/art_series.jpg` | Mosaico y fondo de **Series** |
| `drawable-nodpi/banner.png` (1280×720) y `ic_launcher.png` (1024×1024) | Banner e ícono en el Fire TV / celular |

Las imágenes de fondo (1280×720) son arte original generado por código (`tools/make_art.py`; el logo vectorial sale de `tools/logo_original.png` con `tools/vectorize_logo.py`); si prefieres otras (por ejemplo, hechas con una IA), basta con reemplazar esos archivos y volver a compilar.

## 8. Estructura del proyecto

```
.github/workflows/build-apk.yml   Compila, firma y publica el APK en GitHub
app/src/main/res/
├── drawable/logo_ultra.xml        Logo en vector (de tu logotipo)
└── drawable-nodpi/                Banner, ícono y fondos (banner.png, ic_launcher.png, bg_home/art_*.jpg)
app/src/main/java/com/example/mitvplayer/
├── Config.kt            URL del portal y User-Agent
├── MiTvApp.kt           Application: crea el repositorio
├── MainActivity.kt      Navegación entre todas las pantallas
├── PlayerActivity.kt    Reproductor a pantalla completa (ExoPlayer): subtítulos y tamaño de imagen
├── PlayerPrefs.kt       Tamaño de imagen recordado por tipo
├── RecordingService.kt  Grabaciones (desde la guía; máx. 2 h, se guardan en Películas)
├── data/
│   ├── XtreamApi.kt     Cliente de player_api.php (login, listas, EPG, Catch up)
│   ├── JsonStream.kt    Lectura de listas JSON enormes sin gastar memoria
│   ├── Repository.kt    Listas completas con caché en disco, guía y sesión
│   ├── Library.kt       Favoritos e historial
│   ├── EpgMatch.kt      Nombre base de un canal (quita HD/FHD/4K) para prestar la guía entre hermanos
│   ├── SubtitleService.kt / SubtitleSettings.kt / TitleClean.kt   Subtítulos de internet y limpieza de títulos
│   ├── SessionStore.kt  Guarda la sesión cifrada (Android Keystore)
│   └── Models.kt
└── ui/
    ├── AppViewModel.kt        Estado de la app, navegación y acciones
    ├── BrowseState.kt         Categorías, cantidades, orden y favoritos de una sección
    ├── HomeScreen.kt          Inicio
    ├── LiveScreen.kt          En vivo en tres paneles con vista previa
    ├── PosterGridScreen.kt    Cine y Series
    ├── EpgScreens.kt          EPG y Catch up
    ├── MultiScreen.kt         Pantalla múltiple
    ├── SearchScreen.kt        Búsqueda maestra
    ├── MiscScreens.kt         Cuenta, avisos, ajustes y grabaciones
    ├── DetailScreens.kt       Detalle de película y de serie
    ├── LoginScreen.kt
    ├── Components.kt / BrowseComponents.kt / UiIcons.kt / Theme.kt / Format.kt
```

El identificador de la app (`applicationId`) es `com.ultrapremier.tv`; el paquete interno del código sigue siendo `com.example.mitvplayer` y no afecta a los usuarios.

## 9. Notas de seguridad y límites

- **Credenciales:** se guardan cifradas con el Android Keystore al entrar. Cerrar sesión las borra. Las copias de seguridad de Android están desactivadas.
- **HTTP sin cifrar:** muchos portales IPTV usan `http://`, por eso la app permite tráfico no cifrado (`usesCleartextTraffic`). Si tu portal tiene `https://`, úsalo. Para exigir HTTPS, quita ese atributo de `AndroidManifest.xml`.
- **Conexiones simultáneas:** la mayoría de las cuentas permite una sola. La vista previa se corta antes de abrir pantalla completa. La **Pantalla múltiple** y las **grabaciones** necesitan más de una conexión: si tu cuenta solo permite una, solo abrirá un canal a la vez.
- **Catch up y guía:** dependen de que tu proveedor las incluya; si no, la app lo indica.
- **Subtítulos en internet:** usan la API oficial de OpenSubtitles.com con tu propia clave gratuita (Ajustes → Subtítulos en internet); el plan gratis tiene un tope diario de descargas. La app no crea subtítulos desde el audio: busca los que ya existen para esa película o serie.
- **Guía:** cuando se toma la de un canal hermano o se muestra "● En vivo", es para que no quede vacía; los horarios reales los pone tu proveedor.
- **Prueba automática:** `.github/workflows/smoke.yml` abre la app en un Android virtual con un servidor de mentira (`tools/smoke/`) y recorre las pantallas; las capturas quedan en la rama `smoke-results`. No publica nada.
