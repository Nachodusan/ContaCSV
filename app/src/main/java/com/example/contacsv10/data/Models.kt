// app/src/main/java/com/example/contacsv10/data/models.kt
package com.example.contacsv10.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Zona(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val nombre: String,
    val descripcion: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Contacto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("zona_id") val zonaId: Long?, // CAMBIO IMPORTANTE: Long → Long?
    val nombre: String,
    val telefono: String? = null,
    val asistencia: Boolean? = null,
    val interes: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
