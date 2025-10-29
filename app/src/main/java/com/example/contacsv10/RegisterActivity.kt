package com.example.contacsv10

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.contacsv10.databinding.ActivityRegisterBinding
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val supabase get() = App.supabase  // Cliente global de Supabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón para crear la cuenta
        binding.btnCreateAccount.setOnClickListener { submitRegister() }

        // Link para volver al login
        binding.tvGoLogin.setOnClickListener {
            finish() // Regresa a la pantalla anterior (Welcome/Login)
        }
    }

    private fun submitRegister() {
        val email    = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
        val pass     = binding.etPassword.text?.toString()?.trim().orEmpty()
        val pass2    = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val fullName = binding.etFullName.text?.toString()?.trim().orEmpty()
        val phone    = binding.etPhone.text?.toString()?.trim().orEmpty()
        val section  = binding.etSection.text?.toString()?.trim().orEmpty()

        if (email.isEmpty() || pass.isEmpty()) {
            toast("Correo y contraseña son obligatorios"); return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Correo inválido"); return
        }
        if (pass.length < 6) {
            toast("La contraseña debe tener al menos 6 caracteres"); return
        }
        if (pass != pass2) {
            toast("Las contraseñas no coinciden"); return
        }
        if (phone.isNotEmpty() && phone.length != 10) {
            toast("El teléfono debe tener 10 dígitos"); return
        }

        doRegister(
            email = email,
            pass = pass,
            username = username.ifBlank { null },
            fullName = fullName.ifBlank { null },
            phone    = phone.ifBlank { null },
            section  = section.ifBlank { null }
        )
    }

    private fun doRegister(
        email: String,
        pass: String,
        username: String?,
        fullName: String?,
        phone: String?,
        section: String?
    ) {
        val client = supabase ?: run {
            toast("Configura Supabase en App.kt")
            return
        }

        setLoading(true, "Creando cuenta…")
        lifecycleScope.launch {
            try {
                // ✅ Enviamos metadatos como JsonObject (user_metadata)
                client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = pass
                    data = buildJsonObject {
                        if (username != null) put("username", username)
                        if (fullName != null) put("full_name", fullName)
                        if (phone != null) put("phone", phone)
                        if (section != null) put("section", section)
                    }
                }

                // ✅ SDK 2.x: comprobar sesión desde el cliente
                val hasSession = client.auth.currentSessionOrNull() != null
                setLoading(false, "")

                if (hasSession) {
                    toast("Cuenta creada ✅")
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finish()
                } else {
                    // Proyectos con verificación por correo
                    toast("Revisa tu correo para confirmar la cuenta")
                    binding.tvStatus.text = "Se envió verificación. Tras confirmarla, inicia sesión."
                }

            } catch (e: Exception) {
                setLoading(false, "")
                toast("Error al registrar: ${e.message}")
            }
        }
    }

    private fun setLoading(loading: Boolean, status: String = "") {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCreateAccount.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.etConfirmPassword.isEnabled = !loading
        binding.etUsername.isEnabled = !loading
        binding.etFullName.isEnabled = !loading
        binding.etPhone.isEnabled = !loading
        binding.etSection.isEnabled = !loading
        binding.tvStatus.text = status
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
