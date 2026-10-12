package com.example.mitvplayer

import android.app.Application
import com.example.mitvplayer.data.Repository
import com.example.mitvplayer.data.SessionStore
import com.example.mitvplayer.data.XtreamApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MiTvApp : Application() {

    val repository: Repository by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        Repository(XtreamApi(client), SessionStore(this))
    }
}
