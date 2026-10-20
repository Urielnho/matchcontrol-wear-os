package com.matchcontrol.domain
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId
@Serializable data class Team(val id: Int, val name: String, val logo: String? = null)
@Serializable data class Match(val id: Int, val home: Team, val away: Team, val league: String, val kickoff: String, val status: String, val minute: Int? = null, val homeGoals: Int? = null, val awayGoals: Int? = null, val events: List<String> = emptyList(), val statistics: List<String> = emptyList(), val leagueId: Int? = null, val timeline: List<MatchEvent> = emptyList(), val comparisons: List<MatchStatistic> = emptyList()) {
 val live get() = status in setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE")
 val finished get() = status in setOf("FT", "AET", "PEN", "AWD", "WO")
 val upcoming get() = status in setOf("NS", "TBD", "PST")
 val date: LocalDate get() = OffsetDateTime.parse(kickoff).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}
interface FootballRepository {
 suspend fun matches(date: LocalDate): List<Match>
 suspend fun details(match: Match): Match
}

@Serializable data class Competition(val id: Int, val name: String, val apiName: String, val country: String) {
 fun includes(match: Match): Boolean = if (match.leagueId != null) match.leagueId == id
     else match.league == name || match.league == apiName
}

@Serializable data class MatchEvent(val minute: Int?, val teamId: Int?, val player: String?, val kind: String, val detail: String, val assist: String? = null)
@Serializable data class MatchStatistic(val type: String, val home: String?, val away: String?) {
 fun fraction(): Float? {
  val h=home?.removeSuffix("%")?.toFloatOrNull() ?: return null
  val a=away?.removeSuffix("%")?.toFloatOrNull() ?: return null
  if (!h.isFinite() || !a.isFinite() || h<0 || a<0) return null
  return if(h+a==0f) 0.5f else h/(h+a)
 }
}
