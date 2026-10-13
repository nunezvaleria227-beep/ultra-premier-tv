# Ficha para la tienda de Roku — Ultra Premier TV

Todo lo que pide el [Panel de desarrolladores de Roku](https://developer.roku.com/dev/docs/channel-publishing-guide) está aquí, listo para copiar.
Lo que solo puede hacer el dueño de la cuenta está marcado con **(tú)**.

## 1. Antes de empezar
1. **(tú)** Cuenta de desarrollador gratuita en <https://developer.roku.com> (con verificación en dos pasos).
2. **(tú)** Un Roku en modo desarrollador (control: Inicio ×3, Arriba ×2, Derecha, Izquierda, Derecha, Izquierda, Derecha) para firmar el paquete.
3. **(tú)** Una **cuenta de prueba** de tu proveedor, con fecha de vencimiento lejana, para que los revisores de Roku puedan entrar (usuario y contraseña).
   Roku la prueba durante la certificación; si no funciona, rechazan la app.

## 2. Firmar el paquete (.pkg)
1. Baja `UltraPremierTV-Roku.zip` de <https://github.com/nunezvaleria227-beep/ultra-premier-tv/releases/tag/roku-latest>.
2. Instálalo en tu Roku (`http://IP-DEL-ROKU` → usuario `rokudev` → **Upload** → **Install**).
3. Con telnet a `IP-DEL-ROKU` puerto `8080`, escribe `genkey`. Guarda el **Dev ID** y la **contraseña** que muestra: **la misma llave se necesita para todas las versiones futuras. Si la pierdes, no podrás actualizar la app.**
4. En `http://IP-DEL-ROKU` → **Packager**: escribe el nombre de la app (`UltraPremierTV`) y la contraseña de la llave → **Package**. Baja el archivo `.pkg`.

## 3. Datos de la ficha (idioma principal: español; añade inglés)
- **Nombre** (máx. 30): `Ultra Premier TV`
- **Categoría** sugerida: Movies & TV
- **Tipo de app**: gratuita, sin compras dentro de la app, requiere iniciar sesión
- **Cartel de la tienda** (540×405): `roku/images/icon_fhd.png`
- **Capturas** (hasta 6, 1920×1080): **(tú)** tómalas de tu Roku (en `http://IP-DEL-ROKU` → **Utilities → Screenshot**; si salen de 1280×720, escálalas a 1920×1080). Deben ser capturas reales de la app: inicio, TV en directo, películas, ficha de película, series y reproducción.
- **Política de privacidad**: <https://github.com/nunezvaleria227-beep/ultra-premier-tv/blob/main/docs/PRIVACIDAD.md>
- **Términos**: <https://github.com/nunezvaleria227-beep/ultra-premier-tv/blob/main/docs/TERMINOS.md>
- **Soporte / "learn more"**: <https://github.com/nunezvaleria227-beep/ultra-premier-tv/issues>
- **Contactos administrativo y técnico**: **(tú)** tu nombre y tu correo.
- **Descripción en el dispositivo** (máx. 300), español:
  > Tu TV en directo, películas y series en un solo lugar. Entra con el usuario y la contraseña de tu proveedor, mira canales con guía de programación, retoma lo que dejaste a medias y guarda tus favoritos. Incluye control parental.
- **On-device description**, English:
  > Your live TV, movies and series in one place. Sign in with the username and password from your provider, watch channels with a program guide, pick up where you left off and save your favorites. Includes parental control.

### Descripción en línea (máx. 1500), español
```
Ultra Premier TV reúne en una sola app la televisión en directo, las películas y las series a las que ya tienes acceso con tu proveedor de televisión por internet.

Entra con el usuario y la contraseña que te dio tu proveedor y la sesión queda guardada.

• TV en directo con guía de "ahora / después" y cambio de canal con las flechas.
• Películas y series con ficha, temporadas y episodios; al terminar un episodio pasa al siguiente.
• "Continuar viendo": retoma justo donde te quedaste.
• Favoritos y búsqueda por nombre, sin importar acentos ni mayúsculas.
• Subtítulos y audio incluidos en el video: se elige español o inglés automáticamente cuando existen.
• Control parental con PIN: oculta por defecto las categorías para adultos.

Importante: la app no incluye contenido propio ni suscripciones. Necesitas una cuenta vigente con un proveedor de televisión por internet.
```

### Online description, English
```
Ultra Premier TV brings live TV, movies and series together in one app, using the access you already have with your internet-TV provider.

Sign in with the username and password from your provider and your session is remembered.

• Live TV with a "now / next" guide and channel zapping with the arrow keys.
• Movies and series with details, seasons and episodes; the next episode plays automatically.
• "Continue watching": resume exactly where you left off.
• Favorites and search by name, ignoring accents and capitalization.
• Subtitles and audio tracks included in the video: Spanish or English is picked automatically when available.
• Parental control with a PIN: adult categories are hidden by default.

Important: the app does not include any content or subscriptions of its own. You need an active account with an internet-TV provider.
```

## 4. Enlaces directos (deep linking) — obligatorios
Registra estos tres conjuntos de parámetros (la app ya los entiende):

| Tipo de contenido (mediaType) | contentId | Qué abre |
|---|---|---|
| `movie` | id de la película (`stream_id` del portal) | ficha de la película |
| `series` | id de la serie (`series_id` del portal) | temporadas y episodios |
| `live` | id del canal (`stream_id` del portal) | el canal en vivo |

Para probarlos: en `http://IP-DEL-ROKU` o con el *Deep Linking Tester* de Roku. Ejemplo (reemplaza ID por uno real de tu cuenta):
`curl -d '' "http://IP-DEL-ROKU:8060/launch/dev?contentId=ID&mediaType=movie"` (primero inicia sesión una vez en la app).

## 4b. Ya resuelto en la app
- Aviso `AppLaunchComplete` al abrir (lo exige la certificación).
- Apertura por enlace directo con la app cerrada o abierta (`supports_input_launch=1`).
- Splash, íconos (HD, SD y FHD), versión en el `manifest`.

## 5. Calendario y requisitos de revisión (según Roku)
- Roku pide pasar el **Static Analysis** del panel antes de enviar: si marca errores, mándamelos y los corrijo.
- Al enviar, **(tú)** confirmas la lista de verificación y **certificas que tienes los derechos del contenido**. Es una declaración legal tuya: revisa que tu proveedor te permita ofrecer su servicio en una app pública.
- La publicación más temprana es el segundo día hábil; no se puede programar en viernes ni fines de semana.
- Una app pública no sale hasta pasar la certificación de Roku.
