package com.example.mitvplayer

object Config {
    /**
     * URL del portal (ej. "http://mi-servidor.com:8080").
     *
     * - Si la rellenas, la app solo pedirá usuario y contraseña.
     * - Si la dejas vacía, la pantalla de inicio de sesión también pedirá la URL.
     */
    const val PORTAL_URL: String = "http://gzytv.click:8880"

    /** Identificación propia de la app (User-Agent). */
    const val DEFAULT_USER_AGENT: String = "UltraPremierTV/1.0 (Linux; Android)"

    /**
     * User-Agent que usan las peticiones a la API y el reproductor.
     *
     * Algunos servidores solo dejan pasar a ciertos reproductores y rechazan al resto con
     * "401". Por eso, si el servidor rechaza la identificación propia, al iniciar sesión se
     * prueban las de [userAgentOptions] y se queda la primera que el servidor acepte.
     */
    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT

    /** Igual que [userAgent]; se mantiene el nombre de siempre para el resto del código. */
    val USER_AGENT: String get() = userAgent

    /** Identificaciones a probar, en orden, cuando el servidor rechaza la actual. */
    fun userAgentOptions(): List<String> {
        val system = System.getProperty("http.agent")?.takeIf { it.isNotBlank() }
        return listOfNotNull(
            DEFAULT_USER_AGENT,
            "okhttp/4.12.0",
            system,
            "VLC/3.0.20 LibVLC/3.0.20",
            "IPTVSmartersPlayer"
        ).distinct()
    }
}
