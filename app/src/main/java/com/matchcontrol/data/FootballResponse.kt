package com.matchcontrol.data

import kotlinx.serialization.json.*

internal object FootballResponse {
    fun validate(result: JsonObject) {
        val errors = result["errors"]
        val empty = errors == null || errors is JsonNull ||
            errors is JsonArray && errors.isEmpty() || errors is JsonObject && errors.isEmpty()
        check(empty) {
            val fields = (errors as? JsonObject)?.keys.orEmpty()
            when {
                "plan" in fields -> "No hay cobertura disponible para esta fecha o temporada."
                "token" in fields || "key" in fields -> "No se pudo acceder a los resultados."
                fields.any { "rate" in it || "limit" in it || "request" in it } -> "No se pueden actualizar más resultados por ahora. Puedes consultar los datos guardados."
                else -> "No se pueden consultar resultados para esta fecha."
            }
        }
        check(result["response"] is JsonArray) { "El servicio devolvió información incompleta" }
    }
}
