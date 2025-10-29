package com.example.contacsv10

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.contacsv10.databinding.ActivityWelcomeBinding
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private val supabase get() = App.supabase  // Puede ser null si no se configuró

    // 🔒 Candado para evitar loops/navegación doble
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 👉 Ir a registro
        binding.tvContinue.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Si Supabase no está configurado, no crashees y avisa
        if (!App.isSupabaseConfigured || supabase == null) {
            setLoading(false, "")
            toast("Configura las claves de Supabase en App.kt")
            return
        }

        // Revisión de sesión al abrir
        lifecycleScope.launch {
            val client = supabase ?: return@launch
            try {
                val session = client.auth.currentSessionOrNull()
                if (session != null && !hasNavigated) {
                    val uid = session.user?.id.orEmpty()
                    fetchAndCacheProfile(uid)
                    goToProfile(uid)
                } else {
                    setLoading(false, "")
                }
            } catch (_: Exception) {
                setLoading(false, "")
            }
        }

        // Login
        binding.btnLogin.setOnClickListener { submitLogin() }

        // IME action "done" en password
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin(); true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        // Por si regresan del registro con sesión creada
        val client = supabase
        if (client != null && !hasNavigated) {
            lifecycleScope.launch {
                try {
                    val session = client.auth.currentSessionOrNull()
                    if (session != null && !hasNavigated) {
                        val uid = session.user?.id.orEmpty()
                        fetchAndCacheProfile(uid)
                        goToProfile(uid)
                    }
                } catch (_: Exception) { /* sin crash */ }
            }
        }
    }

    private fun submitLogin() {
        val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
        val pass = binding.etPassword.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || pass.isEmpty()) {
            toast("Completa correo y contraseña"); return
        }
        doLogin(email, pass)
    }

    private fun doLogin(email: String, password: String) {
        val client = supabase ?: run {
            toast("Configura las claves de Supabase en App.kt")
            return
        }

        setLoading(true, "Entrando…")
        lifecycleScope.launch {
            try {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val uid = client.auth.currentUserOrNull()?.id.orEmpty()
                if (uid.isBlank()) {
                    setLoading(false, "")
                    toast("No se pudo obtener el usuario actual")
                    return@launch
                }

                // Trae y cachea el perfil antes de ir al Perfil
                fetchAndCacheProfile(uid)

                setLoading(false, "")
                toast("¡Bienvenido!")
                goToProfile(uid)

            } catch (e: Exception) {
                setLoading(false, "")
                toast("Error: ${e.message}")
            }
        }
    }

    // ▶️ Ir al Perfil (limpia back stack para que "Atrás" no regrese al login)
    private fun goToProfile(userId: String) {
        if (hasNavigated) return
        hasNavigated = true
        val intent = Intent(this, ProfileActivity::class.java).apply {
            putExtra("USER_ID", userId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean, status: String = "") {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.tvContinue.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.tvStatus.text = status
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class Profile(
        val id: String,
        val username: String? = null,
        val full_name: String? = null,
        val phone: String? = null,
        val section: String? = null
    )

    // 🔄 Obtener y cachear el perfil del usuario desde Supabase
    private suspend fun fetchAndCacheProfile(userId: String) {
        if (userId.isBlank()) return
        val client = supabase ?: return
        try {
            val result = client.postgrest["profiles"]
                .select { filter { eq("id", userId) } }

            val json = result.data.toString()

            val profiles: List<Profile> = Json {
                ignoreUnknownKeys = true
            }.decodeFromString(json)

            val profile = profiles.firstOrNull() ?: return

            val prefs = getSharedPreferences("profile", MODE_PRIVATE).edit()
            prefs.putString("username", profile.username)
            prefs.putString("full_name", profile.full_name)
            prefs.putString("phone", profile.phone)
            prefs.putString("section", profile.section)
            prefs.apply()

        } catch (e: Exception) {
            // No crashea si falla red/RLS; solo informa
            toast("Error al obtener el perfil: ${e.message}")
        }
    }
}
