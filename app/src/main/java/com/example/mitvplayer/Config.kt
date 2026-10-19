package com.example.mitvplayer

object Config {
    /**
     * URL del portal (ej. "http://mi-servidor.com:8080").
     *
     * - Si la rellenas, la app solo pedirá usuario y contraseña.
     * - Si la dejas vacía, la pantalla de inicio de sesión también pedirá la URL.
     */
    const val PORTAL_URL: String = "http://gzytv.click:8880"

    /** User-Agent que usan las peticiones a la API y el reproductor. */
    const val USER_AGENT: String = "UltraPremierTV/1.0 (Linux; Android)"
}
