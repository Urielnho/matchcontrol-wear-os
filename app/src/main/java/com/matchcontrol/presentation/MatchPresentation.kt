package com.matchcontrol.presentation

import com.matchcontrol.domain.Match
import com.matchcontrol.domain.Team
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun updatedLabel(updated: Long): String = if(updated <= 0) "Aún sin datos guardados" else
    "Actualizado: " + Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM · HH:mm"))

fun teamCode(name: String): String {
    val words = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+"))
        .filter { it.isNotBlank() && it.uppercase(Locale.ROOT) !in setOf("FC", "CF", "CLUB", "DE", "THE") }
    val code = if (words.size >= 3) words.take(3).joinToString("") { it.take(1) }
        else if(words.size==2) words[0].take(1)+words[1].take(2)
        else (words.firstOrNull() ?: name).take(3)
    return code.uppercase(Locale.ROOT)
}
fun isFavorite(match: Match, favorites: Set<String>) =
    match.home.id.toString() in favorites || match.away.id.toString() in favorites
fun featuredMatch(matches: List<Match>, favorites: Set<String>): Match? =
    matches.filter { it.live }.sortedByDescending { isFavorite(it, favorites) }.firstOrNull()
        ?: matches.filter { it.upcoming }.sortedWith(compareByDescending<Match> { isFavorite(it, favorites) }
            .thenBy { runCatching { OffsetDateTime.parse(it.kickoff).toInstant() }.getOrNull() }).firstOrNull()
fun localMatchTime(match: Match, pattern: String = "dd MMM · HH:mm"): String = runCatching {
    OffsetDateTime.parse(match.kickoff).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern))
}.getOrDefault("Horario por confirmar")
fun stateLabel(match: Match): String = when {
    match.live -> if (match.status == "HT") "DESCANSO" else "EN VIVO · ${match.minute?.let { "$it′" } ?: match.status}"
    match.finished -> "FINAL"
    match.status in setOf("SUSP", "INT") -> "SUSPENDIDO"
    match.status == "PST" -> "APLAZADO"
    match.status in setOf("CANC", "ABD") -> "CANCELADO"
    else -> localMatchTime(match, "HH:mm")
}
