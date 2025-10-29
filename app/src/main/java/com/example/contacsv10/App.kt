package com.example.contacsv10

import android.app.Application
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.auth

class App : Application() {

    companion object {
        // ⚠️ Credenciales (anon key es pública por naturaleza, OK en cliente)
        private const val SUPABASE_URL = "https://cjjdocpiwdqzglnixbyf.supabase.co"
        private const val SUPABASE_ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNqamRvY3Bpd2Rxemdsbml4YnlmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE2OTkyODgsImV4cCI6MjA3NzI3NTI4OH0.TofZIRttPikPpw2sKfMwvWPICZQ9HAJZuMdJ_32zDPg"

        /** Cliente global (nullable si la init falló) */
        var supabase: SupabaseClient? = null
            private set

        /** Para checks rápidos desde las Activities */
        val isSupabaseConfigured: Boolean
            get() = SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()

        /** Helper: UID actual o null si no hay sesión */
        fun currentUserIdOrNull(): String? =
            supabase?.auth?.currentUserOrNull()?.id

        /** Logout centralizado (no crashea si falla red) */
        suspend fun signOutSafe() {
            try {
                supabase?.auth?.signOut()
            } catch (_: Exception) {
                // Ignorar; preferimos no crashear en logout
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!isSupabaseConfigured) {
            Log.e("AppInit", "❌ Configura SUPABASE_URL y SUPABASE_ANON_KEY")
            return
        }

        try {
            // Cliente con Auth + Postgrest
            supabase = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                install(Auth) {
                    // Mantiene el token fresco automáticamente
                    alwaysAutoRefresh = true
                    // Nota: si en tu versión de la lib hay soporte de persistencia,
                    // podrías habilitarla aquí (p. ej. storage basado en SharedPreferences).
                }
                install(Postgrest)
            }

            Log.i("AppInit", "✅ Supabase inicializado")
        } catch (e: Exception) {
            Log.e("AppInit", "Error al iniciar Supabase: ${e.message}", e)
            supabase = null
        }
    }
}
