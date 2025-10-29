package com.example.contacsv10.data

import com.example.contacsv10.App
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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

        // 1️⃣ Upsert ZONAS
        val zonasParaUpsert = zonasLocales.map { z ->
            buildJsonObject {
                put("user_id", userId)
                put("nombre", z.nombre)
                put("descripcion", z.descripcion ?: "")
            }
        }

        // Asegúrate de que zonasParaUpsert sea un array
        if (zonasParaUpsert.isEmpty()) {
            println("No hay zonas para upsert.")
        } else {
            println("Zonas para upsert: ${zonasParaUpsert.size}")
        }

        // Verificar que zonasParaUpsert no esté vacío y que esté correctamente formateado como un array
        val zonasUpserted: List<Zona> = if (zonasParaUpsert.isNotEmpty()) {
            try {
                // Usamos upsert para insertar o actualizar zonas
                val response = client.from("zonas")
                    .upsert(zonasParaUpsert, onConflict = "user_id,nombre")
                    .decodeList<Zona>()
                response // Devuelve la lista de zonas insertadas o actualizadas
            } catch (e: Exception) {
                error("Error al hacer upsert de zonas: ${e.message}")
            }
        } else emptyList()

        // 2️⃣ Consultar zonas de la base de datos
        val zonasDB: List<Zona> = try {
            client.from("zonas")
                .select()
                .decodeList<Zona>()
        } catch (e: Exception) {
            error("Error al consultar zonas desde la base de datos: ${e.message}")
        }

        val byName = (zonasUpserted + zonasDB).associateBy { it.nombre }.toMutableMap()

        // 3️⃣ Upsert CONTACTOS
        var contactosUpsertados = 0

        // 3a) Upsert de contactos con zonaId real
        if (contactosLocales.isNotEmpty()) {
            val payload = contactosLocales.map { c ->
                buildJsonObject {
                    if (c.id != null) put("id", JsonPrimitive(c.id)) else put("id", JsonNull)
                    put("user_id", userId)
                    put("zona_id", c.zonaId ?: error("Contacto sin zonaId real"))
                    put("nombre", c.nombre)
                    put("telefono", c.telefono ?: "")
                    put("asistencia", c.asistencia ?: false)
                    put("interes", c.interes ?: false)
                }
            }

            // Asegúrate de que el payload esté correctamente formado como un array
            if (payload.isEmpty()) {
                println("No hay contactos para upsert.")
            } else {
                println("Contactos para upsert: ${payload.size}")
            }

            // Verificamos que la lista de payload no esté vacía antes de realizar el upsert
            if (payload.isNotEmpty()) {
                try {
                    val response = client.from("contactos").upsert(payload).decodeList<Contacto>()
                    contactosUpsertados += response.size
                } catch (e: Exception) {
                    error("Error al hacer upsert de contactos: ${e.message}")
                }
            }
        }

        // 3b) Contactos mapeados por nombre de zona
        if (mapZonaNombreAContactos.isNotEmpty()) {
            mapZonaNombreAContactos.forEach { (nombreZona, contactos) ->
                val zona = byName[nombreZona]
                    ?: error("Zona '$nombreZona' no encontrada/creada para el usuario.")

                val payload = contactos.map { c ->
                    buildJsonObject {
                        if (c.id != null) put("id", JsonPrimitive(c.id)) else put("id", JsonNull)
                        put("user_id", userId)
                        put("zona_id", zona.id ?: error("Zona sin id"))
                        put("nombre", c.nombre)
                        put("telefono", c.telefono ?: "")
                        put("asistencia", c.asistencia ?: false)
                        put("interes", c.interes ?: false)
                    }
                }

                // Asegúrate de que el payload esté correctamente formado como un array
                if (payload.isEmpty()) {
                    println("No hay contactos mapeados para upsert.")
                } else {
                    println("Contactos mapeados para upsert: ${payload.size}")
                }

                // Verificamos que la lista de payload no esté vacía antes de realizar el upsert
                if (payload.isNotEmpty()) {
                    try {
                        val response = client.from("contactos").upsert(payload).decodeList<Contacto>()
                        contactosUpsertados += response.size
                    } catch (e: Exception) {
                        error("Error al hacer upsert de contactos mapeados por zona: ${e.message}")
                    }
                }
            }
        }

        // Retornamos el resultado de la sincronización
        SyncResult(zonasParaUpsert.size, contactosUpsertados)
    }
}
