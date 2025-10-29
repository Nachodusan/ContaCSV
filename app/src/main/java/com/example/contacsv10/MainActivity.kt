package com.example.contacsv10

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.contacsv10.data.Contacto
import com.example.contacsv10.data.SupabaseSyncRepo
import com.example.contacsv10.data.Zona
import com.example.contacsv10.databinding.ActivityMainBinding
import io.github.jan.supabase.gotrue.auth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Modelo con respuestas, fecha y sección
    data class PersonRecord(
        val name: String,
        val phone: String,
        val asambleaSi: Boolean,
        val grupoSi: Boolean,
        val fecha: String,
        val section: String
    )

    private val items = mutableListOf<PersonRecord>()

    // Adaptador personalizado con botón X
    private lateinit var personAdapter: ArrayAdapter<PersonRecord>

    // Locale MX moderno (evita deprecation)
    private val localeMx: Locale = Locale.forLanguageTag("es-MX")

    // Fecha (para tvDate y para fecha del registro)
    private val dateFormatBar = SimpleDateFormat("dd/MM/yyyy", localeMx)
    private val dateHandler = Handler(Looper.getMainLooper())
    private var dateRunnable: Runnable? = null

    // --- Persistencia por zona ---
    private val gson = Gson()
    private fun prefs() = getSharedPreferences("main_records_prefs", Context.MODE_PRIVATE)
    private fun prefKeyForZone(zoneKey: String) = "people_json_$zoneKey"

    private var currentZoneName: String = "default"   // nombre mostrado (humano)
    private var currentZoneKey: String = "default"    // clave segura para almacenamiento/archivos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Zona seleccionada desde ZoneActivity
        val zoneName = intent.getStringExtra("zone_name")
        currentZoneName = if (!zoneName.isNullOrBlank()) zoneName else "default"
        currentZoneKey = toSafeKey(currentZoneName)

        binding.tvZoneTitle.text =
            if (currentZoneName != "default") "Zona $currentZoneName" else "Zona no seleccionada"

        // Volver a Zonas
        binding.btnBackToZones.setOnClickListener {
            val intent = Intent(this, ZoneActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }

        // Fecha (dd/MM/yyyy) en el toolbar
        updateDateNow()
        scheduleDateUpdateAtMidnight()

        // Adaptador personalizado
        personAdapter = object : ArrayAdapter<PersonRecord>(this, R.layout.item_person_row, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_person_row, parent, false)

                val tv = view.findViewById<TextView>(R.id.tvPerson)
                val btnDel = view.findViewById<ImageButton>(R.id.btnDelete)

                val rec = getItem(position)!!

                tv.text = "${rec.name} — ${rec.phone} • Asamblea: ${siNo(rec.asambleaSi)} • " +
                        "Grupo: ${siNo(rec.grupoSi)} • Sección: ${rec.section} • Fecha: ${rec.fecha}"

                btnDel.setOnClickListener {
                    // Confirmación antes de eliminar
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Eliminar contacto")
                        .setMessage("¿Seguro que deseas eliminar a ${rec.name}?")
                        .setPositiveButton("Sí") { _, _ ->
                            items.removeAt(position)
                            saveItemsForZone()
                            notifyDataSetChanged()
                            toast("Contacto eliminado")
                        }
                        .setNegativeButton("No", null)
                        .show()
                }

                return view
            }
        }

        binding.lvList.adapter = personAdapter

        // Cargar contactos de ESTA zona y mostrarlos
        loadItemsForZone()
        personAdapter.notifyDataSetChanged()

        // Agregar registro
        binding.btnAdd.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val sectionRaw = binding.etSection.text.toString().trim()
            val section = if (sectionRaw.isBlank()) "-" else sectionRaw

            if (name.isEmpty()) {
                toast("Ingresa el nombre de la persona")
                return@setOnClickListener
            }
            if (!isValidMexPhone(phone)) {
                toast("Número mexicano inválido. Usa 10 dígitos o +52 seguido de 10 dígitos")
                return@setOnClickListener
            }
            if (binding.rgAsamblea.checkedRadioButtonId == -1) {
                toast("Selecciona si asistirá a la asamblea (Sí/No)")
                return@setOnClickListener
            }
            if (binding.rgGrupo.checkedRadioButtonId == -1) {
                toast("Selecciona si está interesado en crear un grupo (Sí/No)")
                return@setOnClickListener
            }

            val asambleaSi = binding.rgAsamblea.checkedRadioButtonId == R.id.rbAsambleaSi
            val grupoSi = binding.rgGrupo.checkedRadioButtonId == R.id.rbGrupoSi
            val normalized = normalizedPhone(phone)
            val fechaActual = dateFormatBar.format(Date())

            val record = PersonRecord(
                name = name,
                phone = normalized,
                asambleaSi = asambleaSi,
                grupoSi = grupoSi,
                fecha = fechaActual,
                section = section
            )

            items.add(record)
            saveItemsForZone()
            personAdapter.notifyDataSetChanged()

            // Limpiar campos
            binding.etName.text?.clear()
            binding.etPhone.text?.clear()
            binding.etSection.text?.clear()
            binding.etName.requestFocus()
            binding.rgAsamblea.clearCheck()
            binding.rgGrupo.clearCheck()
        }

        // Exportar CSV (solo de la zona actual)
        binding.btnExport.setOnClickListener {
            if (items.isEmpty()) {
                toast("No hay datos para exportar en esta zona")
            } else {
                exportCsvToDownloads(items, this, currentZoneKey)
            }
        }

        // ====== ⬆️ Subir ZONA ACTUAL + CONTACTOS a Supabase (Auth) ======
        binding.btnSyncSupabase.setOnClickListener {
            lifecycleScope.launch {
                // Validaciones rápidas
                val client = App.supabase
                val uid = client?.auth?.currentUserOrNull()?.id
                if (client == null || uid == null) {
                    toast("Inicia sesión para sincronizar con Supabase")
                    return@launch
                }
                if (currentZoneName == "default") {
                    toast("Selecciona una zona antes de sincronizar")
                    return@launch
                }
                if (items.isEmpty()) {
                    toast("No hay contactos en esta zona para subir")
                    return@launch
                }

                try {
                    val zonasLocales = listOf(
                        Zona(nombre = currentZoneName, descripcion = null)
                    )
                    val contactosDeZona = items.map { it.toContacto() }

                    val result = SupabaseSyncRepo.syncZonasYContactos(
                        zonasLocales = zonasLocales,
                        // Usamos el mapa por nombre de zona (no necesitas zonaId real)
                        mapZonaNombreAContactos = mapOf(
                            currentZoneName to contactosDeZona
                        )
                    )

                    Toast.makeText(
                        this@MainActivity,
                        "Subido: ${result.zonasSubidas} zonas, ${result.contactosSubidos} contactos",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al subir: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ---------- Fecha ----------
    private fun updateDateNow() {
        binding.tvDate.text = dateFormatBar.format(Date())
    }

    private fun scheduleDateUpdateAtMidnight() {
        val now = Calendar.getInstance()
        val next = now.clone() as Calendar
        next.add(Calendar.DAY_OF_YEAR, 1)
        next.set(Calendar.HOUR_OF_DAY, 0)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        val delay = next.timeInMillis - now.timeInMillis
        dateRunnable = Runnable {
            updateDateNow()
            scheduleDateUpdateAtMidnight()
        }
        dateHandler.postDelayed(dateRunnable!!, delay)
    }

    override fun onDestroy() {
        super.onDestroy()
        dateRunnable?.let { dateHandler.removeCallbacks(it) }
    }

    // ---------- Persistencia por zona ----------
    private fun saveItemsForZone() {
        val json = gson.toJson(items)
        prefs().edit().putString(prefKeyForZone(currentZoneKey), json).apply()
    }

    private fun loadItemsForZone() {
        val json = prefs().getString(prefKeyForZone(currentZoneKey), null) ?: return
        val type = object : TypeToken<List<PersonRecord>>() {}.type
        val saved = gson.fromJson<List<PersonRecord>>(json, type) ?: emptyList()
        items.clear()
        items.addAll(saved)
    }

    // ---------- Utilidades ----------
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun siNo(v: Boolean) = if (v) "Sí" else "No"

    private fun normalizedPhone(input: String): String {
        var s = input.replace("""[\s\-()]+""".toRegex(), "")
        if (s.startsWith("+52")) s = s.removePrefix("+52")
        if (s.startsWith("52") && s.length > 10) s = s.removePrefix("52")
        if (s.length > 10) s = s.takeLast(10)
        return s
    }

    private fun isValidMexPhone(input: String): Boolean {
        val s = input.replace("""[\s\-()]+""".toRegex(), "")
        return s.matches(Regex("""^(\+52)?\d{10}$""")) || s.matches(Regex("""^52\d{10}$"""))
    }

    private fun toSafeKey(raw: String): String {
        val normalized = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return normalized.replace("[^a-z0-9_\\-]+".toRegex(), "_").trim('_')
            .ifEmpty { "default" }
    }

    // ---------- Exportar CSV SOLO de la zona actual ----------
    private fun exportCsvToDownloads(list: List<PersonRecord>, ctx: Context, zoneKey: String) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "contactos_${zoneKey}_$ts.csv"

        try {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                }
            }

            val uri: Uri? = resolver.insert(MediaStore.Downloads.getContentUri("external"), values)
            if (uri == null) {
                toast("No se pudo crear el archivo")
                return
            }

            resolver.openOutputStream(uri)?.use { out ->
                BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
                    // Cabecera con SECCION incluida
                    writer.write("NOMBRE,TELEFONO,ASISTENCIA,GRUPO,SECCION,FECHA")
                    writer.newLine()

                    for (rec in list) {
                        val fila = listOf(
                            escapeCsv(rec.name),
                            rec.phone,
                            siNo(rec.asambleaSi),
                            siNo(rec.grupoSi),
                            escapeCsv(rec.section),
                            rec.fecha
                        ).joinToString(",")
                        writer.write(fila)
                        writer.newLine()
                    }
                }
            }

            Toast.makeText(ctx, "Exportado: $filename en Descargas", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(ctx, "Error exportando: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field
    }

    // ===== Helpers para Sync =====
    /** Mapea un PersonRecord local a Contacto para envío (zonaId no se usa en el mapa; ponemos 0). */
    private fun PersonRecord.toContacto(): Contacto =
        Contacto(
            id = null,
            userId = null,
            zonaId = 0, // se resuelve en el servidor a partir del nombre de la zona
            nombre = this.name,
            telefono = this.phone,
            asistencia = this.asambleaSi,
            interes = this.grupoSi
        )
}
