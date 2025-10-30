package com.example.contacsv10.data

import com.example.contacsv10.App
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SupabaseSyncRepo {

    private val client get() = App.supabase ?: error("Supabase no inicializado")
    private val auth get() = client.auth

    /** UID del usuario actual vía Auth */
    suspend fun currentUserId(): String = withContext(Dispatchers.IO) {
        auth.currentUserOrNull()?.id ?: error("No hay sesión activa. Inicia sesión.")
    }

    data class SyncResult(val zonasSubidas: Int, val contactosSubidos: Int)

    suspend fun syncZonasYContactos(
        zonasLocales: List<Zona>,
        contactosLocales: List<Contacto> = emptyList(),
        mapZonaNombreAContactos: Map<String, List<Contacto>> = emptyMap()
    ): SyncResult = withContext(Dispatchers.IO) {

        val userId = currentUserId()

        // 1) Upsert ZONAS
        val zonasParaUpsert = zonasLocales.map { z ->
            buildJsonObject {
                put("user_id", userId)
                put("nombre", z.nombre)
                put("descripcion", z.descripcion ?: "")
            }
        }
        if (zonasParaUpsert.isNotEmpty()) {
            client.from("zonas").upsert(zonasParaUpsert, onConflict = "user_id,nombre")
        }

        // Re-consultar para asegurar ids de zonas
        val zonasDB: List<Zona> = client.from("zonas").select().decodeList<Zona>()
        val byName = zonasDB.associateBy { it.nombre }.toMutableMap()

        // 2) Upsert CONTACTOS
        var contactosUpsertados = 0

        // 2a) Contactos con zonaId real en el modelo
        if (contactosLocales.isNotEmpty()) {
            val payload = contactosLocales.map { c ->
                buildJsonObject {
                    // ⚠️ Solo incluye "id" si NO es null
                    if (c.id != null) put("id", JsonPrimitive(c.id))
                    put("user_id", userId)
                    put("zona_id", c.zonaId ?: error("Contacto sin zonaId real"))
                    put("nombre", c.nombre)
                    put("telefono", c.telefono ?: "")
                    put("asistencia", c.asistencia ?: false)
                    put("interes", c.interes ?: false)
                }
            }
            if (payload.isNotEmpty()) {
                client.from("contactos").upsert(payload)
                contactosUpsertados += payload.size
            }
        }

        // 2b) Contactos mapeados por nombre de zona
        if (mapZonaNombreAContactos.isNotEmpty()) {
            mapZonaNombreAContactos.forEach { (nombreZona, contactos) ->
                val zona = byName[nombreZona]
                    ?: error("Zona '$nombreZona' no encontrada para el usuario.")

                val payload = contactos.map { c ->
                    buildJsonObject {
                        // ⚠️ Solo incluye "id" si NO es null
                        if (c.id != null) put("id", JsonPrimitive(c.id))
                        put("user_id", userId)
                        put("zona_id", zona.id ?: error("Zona sin id"))
                        put("nombre", c.nombre)
                        put("telefono", c.telefono ?: "")
                        put("asistencia", c.asistencia ?: false)
                        put("interes", c.interes ?: false)
                    }
                }
                if (payload.isNotEmpty()) {
                    client.from("contactos").upsert(payload)
                    contactosUpsertados += payload.size
                }
            }
        }

        SyncResult(zonasParaUpsert.size, contactosUpsertados)
    }
}
