package com.example.contacsv10

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class ZoneActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var lv: ListView
    private lateinit var adapter: ZonesAdapter

    private val zonas = mutableListOf<Zona>()   // memoria
    private val gson = Gson()

    // Overlay de ubicación reutilizable + request code de permisos
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val REQ_LOC = 200

    // 🚩 Bandera: si después de localizar debemos proponer guardar la zona
    private var askToAddZoneAfterFix: Boolean = false

    data class Zona(
        val name: String,
        val lat: Double,
        val lon: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Requerido por osmdroid: user agent
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_zone)
        map = findViewById(R.id.mapView)
        lv = findViewById(R.id.lvZonas)
        val btnCenter: Button = findViewById(R.id.btnCenter) // texto "Rastrear"
        val btnClear: Button = findViewById(R.id.btnClear)

        // Adapter personalizado: X roja para borrar, tocar para ir a Main, y botón compartir
        adapter = ZonesAdapter(
            context = this,
            data = zonas,
            onDelete = { pos ->
                val z = zonas[pos]
                AlertDialog.Builder(this)
                    .setTitle("Eliminar zona")
                    .setMessage("¿Eliminar \"${z.name}\"?")
                    .setPositiveButton("Sí") { _, _ ->
                        zonas.removeAt(pos)
                        saveZonas()
                        renderZonasOnMap()
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton("No", null)
                    .show()
            },
            onClickItem = { pos ->
                val z = zonas[pos]
                val i = Intent(this, MainActivity::class.java).apply {
                    putExtra("zone_name", z.name)
                }
                startActivity(i)
            },
            onShare = { pos ->
                val z = zonas[pos]
                // Intent directo a Google Maps (si está instalada)
                val geoUri = Uri.parse("geo:${z.lat},${z.lon}?q=${z.lat},${z.lon}(${Uri.encode(z.name)})")
                val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    startActivity(mapsIntent)
                } catch (_: Exception) {
                    // Fallback a navegador
                    val webUrl = Uri.parse("https://www.google.com/maps/search/?api=1&query=${z.lat},${z.lon}")
                    startActivity(Intent(Intent.ACTION_VIEW, webUrl))
                }
            }
        )
        lv.adapter = adapter

        // Map setup
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(12.0)
        map.controller.setCenter(GeoPoint(19.4326, -99.1332)) // CDMX por defecto

        // Overlays útiles
        val compass = CompassOverlay(this, InternalCompassOrientationProvider(this), map)
        compass.enableCompass()
        map.overlays.add(compass)

        val scaleBar = ScaleBarOverlay(map)
        map.overlays.add(scaleBar)

        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled = true
        map.overlays.add(rotationGestureOverlay)

        // 🚫 NO usamos taps/long-press para crear zonas

        // Cargar zonas guardadas
        loadZonas()
        renderZonasOnMap()
        adapter.notifyDataSetChanged()

        // ▶️ Botón "Rastrear": localiza y luego ofrece guardar la zona en la posición actual
        btnCenter.setOnClickListener {
            askToAddZoneAfterFix = true
            if (!hasLocationPermission()) {
                requestLocationPermission()
            } else {
                startTrackingMyLocation()
            }
        }

        btnClear.setOnClickListener {
            if (zonas.isEmpty()) {
                Toast.makeText(this, "No hay zonas", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Limpiar")
                    .setMessage("¿Eliminar todas las zonas?")
                    .setPositiveButton("Sí") { _, _ ->
                        zonas.clear()
                        saveZonas()
                        renderZonasOnMap()
                        adapter.notifyDataSetChanged()
                    }.setNegativeButton("No", null)
                    .show()
            }
        }
    }

    private fun renderZonasOnMap() {
        // Limpia markers previos (para evitar duplicados)
        map.overlays.removeAll { it is Marker }

        zonas.forEach { z ->
            val marker = Marker(map).apply {
                position = GeoPoint(z.lat, z.lon)
                title = z.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                isDraggable = false
            }
            map.overlays.add(marker)
        }

        map.invalidate()
    }

    // -------- Permisos / Rastreo de ubicación --------
    private fun hasLocationPermission(): Boolean {
        val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
        return checkSelfPermission(fine) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(coarse) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQ_LOC
        )
    }

    private fun startTrackingMyLocation() {
        if (myLocationOverlay == null) {
            val provider = GpsMyLocationProvider(this)
            myLocationOverlay = MyLocationNewOverlay(provider, map).apply {
                enableMyLocation()
            }
            map.overlays.add(myLocationOverlay)
        } else {
            myLocationOverlay?.enableMyLocation()
        }

        val fix = myLocationOverlay?.lastFix
        if (fix != null) {
            map.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
            map.controller.setZoom(16.0)
            // ✅ Proponer guardar zona con coordenadas actuales
            if (askToAddZoneAfterFix) {
                askToAddZoneAfterFix = false
                promptSaveCurrentLocationAsZone(fix.latitude, fix.longitude)
            }
        } else {
            myLocationOverlay?.runOnFirstFix {
                runOnUiThread {
                    val f = myLocationOverlay?.lastFix
                    if (f != null) {
                        map.controller.animateTo(GeoPoint(f.latitude, f.longitude))
                        map.controller.setZoom(16.0)
                        // ✅ Proponer guardar zona con coordenadas al obtener el primer fix
                        if (askToAddZoneAfterFix) {
                            askToAddZoneAfterFix = false
                            promptSaveCurrentLocationAsZone(f.latitude, f.longitude)
                        }
                    } else {
                        Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Toast.makeText(this, "Obteniendo ubicación…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptSaveCurrentLocationAsZone(lat: Double, lon: Double) {
        // Diálogo simple con un EditText para nombre (sin depender de un layout XML)
        val input = EditText(this).apply {
            hint = "Nombre de la zona"
            setSingleLine(true)
        }

        val coordsText = "(${String.format("%.5f", lat)}, ${String.format("%.5f", lon)})"

        AlertDialog.Builder(this)
            .setTitle("Guardar zona actual")
            .setMessage("Ubicación actual $coordsText\nPonle un nombre:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim().ifEmpty {
                    // Nombre por defecto si no escribió nada
                    "Zona $coordsText"
                }
                val z = Zona(name, lat, lon)
                zonas.add(z)
                saveZonas()
                renderZonasOnMap()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Zona guardada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC) {
            val granted = grantResults.isNotEmpty() && grantResults.any {
                it == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                startTrackingMyLocation()
            } else {
                askToAddZoneAfterFix = false // evita mostrar diálogo si no hay permisos
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------- Persistencia simple con SharedPreferences (JSON) --------
    private fun prefs() = getSharedPreferences("zones_prefs", Context.MODE_PRIVATE)

    private fun saveZonas() {
        val json = gson.toJson(zonas)
        prefs().edit().putString("zones_json", json).apply()
    }

    private fun loadZonas() {
        val json = prefs().getString("zones_json", null) ?: return
        val type = object : TypeToken<List<Zona>>() {}.type
        zonas.clear()
        zonas.addAll(gson.fromJson<List<Zona>>(json, type) ?: emptyList())
    }

    // ---------- Adapter personalizado para la lista ----------
    private class ZonesAdapter(
        context: Context,
        private val data: MutableList<Zona>,
        private val onDelete: (position: Int) -> Unit,
        private val onClickItem: (position: Int) -> Unit,
        private val onShare: (position: Int) -> Unit
    ) : ArrayAdapter<Zona>(context, 0, data) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.list_item_zone, parent, false)

            val ivDelete = view.findViewById<ImageButton>(R.id.ivDelete)
            val tvZone = view.findViewById<TextView>(R.id.tvZone)
            val ivShare = view.findViewById<ImageButton>(R.id.ivShare)

            val z = data[position]

            tvZone.text = "${z.name}: ${"%.5f".format(z.lat)}, ${"%.5f".format(z.lon)}"

            // ❌ Eliminar con la X roja (izquierda)
            ivDelete.setOnClickListener { onDelete(position) }

            // ▶️ Tocar el ítem -> ir a MainActivity con la zona
            view.setOnClickListener { onClickItem(position) }

            // 📤 Compartir/Ver en Google Maps
            ivShare.setOnClickListener { onShare(position) }

            return view
        }
    }
}
