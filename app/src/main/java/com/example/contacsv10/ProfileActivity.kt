package com.example.contacsv10

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.contacsv10.databinding.ActivityProfileBinding
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val supabase get() = App.supabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pinta datos cacheados (guardados por WelcomeActivity)
        fillProfileFromPrefs()

        // Menú ⋮ (solo "Ir a Zonas")
        binding.btnMenu.setOnClickListener { showZonesOnlyMenu() }

        // Botones del layout (opcional)
        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Editar perfil (próximamente)", Toast.LENGTH_SHORT).show()
        }
        binding.btnLogout.setOnClickListener { logoutAndGoBack() }
    }

    private fun showZonesOnlyMenu() {
        val popup = PopupMenu(this, binding.btnMenu)
        popup.inflate(R.menu.menu_profile_popup)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_zones -> {
                    startActivity(Intent(this, ZoneActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun fillProfileFromPrefs() {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        binding.tvFullName.text = prefs.getString("full_name", "Nombre completo")
        binding.tvUsername.text = prefs.getString("username", "@usuario")
        binding.tvPhone.text   = prefs.getString("phone", "Sin teléfono")
        binding.tvSection.text = prefs.getString("section", "Sin sección")
    }

    private fun logoutAndGoBack() {
        // Limpia caché local
        getSharedPreferences("profile", MODE_PRIVATE).edit().clear().apply()

        // Cierra sesión en Supabase (sin crashear si falla red)
        lifecycleScope.launch {
            try { supabase?.auth?.signOut() } catch (_: Exception) { }
            val i = Intent(this@ProfileActivity, WelcomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(i)
            finish()
        }
    }
}
