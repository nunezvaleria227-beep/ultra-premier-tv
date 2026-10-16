/* Ultra Premier TV para Samsung (Tizen). Todo el código es ES5 a propósito: así corre también en las TV Samsung más viejas. */
var UP = window.UP = window.UP || {};

UP.CONFIG = {
    // Igual que en la app de Fire TV. Si se deja vacío, la pantalla de entrada pide la URL del servidor.
    PORTAL_URL: window.UP_TEST_PORTAL !== undefined ? window.UP_TEST_PORTAL : "http://gazytv.vip:8880",
    VERSION: "1.0.0",
    // Las listas se vuelven a bajar solas pasado este tiempo (12 horas, como en Fire TV).
    REFRESH_MS: 12 * 3600 * 1000
};
