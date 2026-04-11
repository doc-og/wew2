package com.wew.parent.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseClient {
    private var _client: SupabaseClient? = null
    val client: SupabaseClient get() = _client ?: error("SupabaseClient not initialized")

    fun initialize(url: String, key: String) {
        if (_client != null) return
        _client = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Postgrest)
            install(Realtime)
            install(Auth)
        }
    }
}
