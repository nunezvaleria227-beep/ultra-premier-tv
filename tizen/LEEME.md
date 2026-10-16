# Ultra Premier TV para Samsung (Tizen)

Esta es la versión para TV Samsung de la app de Fire TV: las mismas pantallas (entrar, inicio, TV en vivo con lo que pasa ahora y después, Cine, Series, Continuar viendo, Buscar, Mi cuenta, Ajustes y PIN de control parental) y el mismo servidor.

> **Estado honesto:** la app pasó una prueba automática de 61 revisiones en un navegador, con un control remoto simulado y un reproductor de mentira. **Todavía no se ha probado en una TV Samsung real.** El video real (AVPlay), el teclado en pantalla de la TV y la instalación solo se pueden comprobar en tu TV.

## Qué necesitas

- Una TV Samsung con Tizen. La app usa variables de estilo (CSS), que las TV traen desde 2017 más o menos; en una TV más vieja puede verse sin colores o sin acomodo. Si es tu caso, avísame y se adapta.
- Una computadora (Windows, Mac o Linux) y la TV en la **misma red wifi**.
- Una cuenta de Samsung (es gratis): <https://account.samsung.com>.
- Tizen Studio, el programa gratuito de Samsung: <https://developer.samsung.com/smarttv> (ahí está la descarga de Tizen Studio)

## Paso 1. Instalar Tizen Studio y sus extensiones

1. Instala Tizen Studio.
2. Ábrelo y entra a **Tools → Package Manager**.
3. En la pestaña **Extension SDK** instala: **TV Extensions**, **TV Extensions Tools** y **Samsung Certificate Extension**. En **Main SDK** revisa que esté **Certificate Manager**.

## Paso 2. Poner la TV en modo desarrollador

1. Pulsa **Inicio** en el control y entra a **Apps**.
2. Con el control, escribe **12345**. Se abre una ventana de *Developer mode*.
3. Pon **Developer mode** en **On** y escribe la **dirección IP de tu computadora** (la ves en la configuración de red de tu computadora).
4. Reinicia la TV (apágala y préndela).

Los menús cambian un poco según el modelo y el año de la TV. Si no ves esa ventana, busca en la guía de Samsung "TV Device Setup" para tu modelo.

## Paso 3. Conectar la TV con Tizen Studio

1. En Tizen Studio: **Tools → Device Manager**.
2. Pulsa **Remote Device Manager** y luego **Scan** (o agrega la TV a mano con su dirección IP, que se ve en la configuración de red de la TV).
3. Pon el interruptor **Connection** en encendido.
4. Clic derecho sobre la TV → **Permit to install applications**.
5. Clic derecho sobre la TV → **DUID**. Apunta ese número: lo pide el certificado.

## Paso 4. Crear el certificado de Samsung

Las apps solo se instalan en la TV si están firmadas con un certificado que incluya el DUID de esa TV.

1. **Tools → Certificate Manager** → **+** (nuevo perfil).
2. Elige **Samsung** y el tipo **TV**.
3. Crea el certificado de **Author** (nombre y contraseña a tu gusto) e inicia sesión con tu cuenta de Samsung cuando lo pida.
4. En el certificado de **Distributor**, deja el nivel **Public** y agrega el **DUID** de tu TV.
5. Termina. Queda un perfil con el nombre que tú le pusiste.

**La contraseña del certificado es solo tuya. No se la des a nadie, tampoco a Claude.**

## Paso 5. Instalar la app

El proyecto no trae los archivos internos de Tizen Studio (`.project`), así que se instala de una de estas dos formas.

**Forma A, con Tizen Studio (con ventanas):**

1. Descarga **UltraPremierTV-Samsung-proyecto.zip** (versión preliminar `samsung-latest` en GitHub) y descomprímelo.
2. En Tizen Studio: **File → New → Tizen Project → Template → TV → Web Application → Basic Project**. Ponle el nombre `UltraPremierTV`.
3. Cuando se cree, borra los archivos que trae de ejemplo y copia dentro del proyecto todo lo que hay en la carpeta `UltraPremierTV` que descomprimiste (`config.xml`, `index.html`, `icon.png`, `css`, `js`, `images`). Acepta reemplazar `config.xml`.
4. Clic derecho sobre el proyecto → **Run As → Tizen Web Application**. Elige tu TV.
5. La app se instala y se abre sola.

**Forma B, con la línea de comandos** (toma el `UltraPremierTV.wgt` sin firmar y lo firma con tu perfil; la carpeta `tools/ide/bin` de Tizen Studio tiene que estar en tu PATH):

```
tizen package -t wgt -s NOMBRE_DEL_PERFIL -o salida -- UltraPremierTV.wgt
tizen install -n salida/ARCHIVO_FIRMADO.wgt -t NOMBRE_DE_LA_TV
```

El comando `package` deja el `.wgt` firmado dentro de la carpeta `salida`; ese es el archivo que se instala. El nombre de la TV es el que sale en Device Manager.

## Si algo no funciona

| Qué pasa | Qué revisar |
|---|---|
| La TV no aparece en Device Manager | Misma red wifi; en la TV, la IP de la computadora bien escrita; reiniciar la TV. |
| "Install failed" o error de certificado | El **DUID** de la TV no está en el certificado de Distributor: crear el perfil otra vez con el DUID correcto. |
| La app abre pero los canales no cargan o se pegan | Entra a **Mi cuenta** dentro de la app y mira **Estado**, **Vence** y **Conexiones**. Si usas todas las conexiones del plan (por ejemplo 2 de 2), hay que cerrar el servicio en otro aparato. |
| Sale "no se pudo entrar" | Revisa usuario y clave con tu proveedor. La app no guarda nada fuera de la TV. |
| La app deja de abrir semanas después | Los certificados de desarrollo vencen y el modo desarrollador puede apagarse. Se crea otro certificado y se reinstala. |

## Cómo está hecha

- `config.xml`: nombre, permisos (internet y control remoto) y acceso al servidor.
- `index.html`, `css/`, `js/`, `images/`: la app. Está escrita en ES5 a propósito para que funcione en TV de años distintos.
- Video: usa **AVPlay** de Samsung (con `<video>` normal de respaldo). Formatos de canal en vivo: HLS (m3u8) o TS, se cambia con el botón amarillo del control o en Ajustes.
- Servidor: `js/config.js` → `PORTAL_URL`. Hoy apunta a `http://gazytv.vip:8880`. Si lo dejas vacío, la pantalla de entrar pide también el servidor.
- Sesión: usuario y clave se guardan solo en la memoria de la propia TV. No hay estadísticas ni anuncios.

**Lo que esta primera versión no tiene todavía** (sí está en Fire TV): guía de canales en cuadrícula, varias pantallas a la vez, retroceder en vivo, grabaciones, búsqueda de subtítulos en línea, radio y avisos.

## Probarla en el navegador (sin TV)

```
python3 tools/tizen_build.py preview /tmp/vista
python3 -m http.server 8765 --directory /tmp/vista/app
```

Abre <http://127.0.0.1:8765/index.html>. Entra con **demo / demo**; los canales, películas y el video son de ejemplo. Flechas = mover, Enter = OK, Esc = Atrás.

## Publicar en la tienda de Samsung (opcional, aún sin confirmar)

- Se hace desde **Samsung Seller Office** con cuenta de vendedor. Según la página de Samsung que consulté, los vendedores públicos eran solo de Estados Unidos; puede haber cambiado, hay que confirmarlo.
- Samsung revisa cada app antes de publicarla. **No he confirmado cómo trata las apps de IPTV.**
- La app no trae contenido propio; funciona con el servicio de cada persona. Antes de publicar, conviene tener claro que el proveedor del servicio tenga derechos sobre lo que transmite. Esto no es asesoría legal.
- La política de privacidad y los términos están en `docs/`. Habría que mencionar Samsung antes de enviarla.
