package com.example.mitvplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.mitvplayer.data.Repository
import com.example.mitvplayer.data.SessionStore
import com.example.mitvplayer.data.XtreamApi
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MiTvApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Abrir el almacén cifrado de la sesión tarda (Keystore): se hace en paralelo mientras se arma la pantalla.
        Thread({
            try {
                repository.savedCredentials()
            } catch (e: Exception) {
                // Si falla aquí, volverá a intentarse cuando haga falta.
            }
        }, "warmup").start()
    }

    val repository: Repository by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        Repository(XtreamApi(client), SessionStore(this), this)
    }

    /**
     * Carga de pósters y logos más rápida:
     *  - muchas descargas a la vez (la cuadrícula pide decenas de pósters del mismo servidor),
     *  - caché en disco grande que se respeta aunque el servidor diga "no guardar" (casi todos lo dicen),
     *  - imágenes en 16 bits: ocupan la mitad de memoria y se desplazan más fluido.
     */
    override fun newImageLoader(): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .crossfade(120)
            .build()
    }
}
