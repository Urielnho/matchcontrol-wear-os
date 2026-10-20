package com.matchcontrol.domain

import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId

enum class Sport(val title: String,val host: String?) {
 FOOTBALL("Fútbol",null), BASKETBALL("Baloncesto","v1.basketball.api-sports.io"), BASEBALL("Béisbol","v1.baseball.api-sports.io"), FORMULA1("Fórmula 1",null)
}
enum class EventState { SCHEDULED, LIVE, FINAL, PENDING, POSTPONED, CANCELLED }
data class PeriodScore(val label: String,val home: String?,val away: String?)
data class DriverResult(val position: Int?,val name: String,val team: String,val time: String?,val points: String?,val laps: String?,val status: String?)
data class SportEvent(
 val id: String,val sport: Sport,val title: String,val competition: String,val kickoff: String,
 val state: EventState,val home: Team?=null,val away: Team?=null,val homeScore: Int?=null,val awayScore: Int?=null,
 val stage: String?=null,val periods: List<PeriodScore> = emptyList(),val statistics: List<PeriodScore> = emptyList(),
 val circuit: String?=null,val drivers: List<DriverResult> = emptyList(),val season: Int?=null,val round: Int?=null,
) {
 val breakdownInconsistent: Boolean get() = state==EventState.FINAL && periods.isNotEmpty() && homeScore!=null && awayScore!=null &&
  (periods.mapNotNull { it.home?.toIntOrNull() }.sum()!=homeScore || periods.mapNotNull { it.away?.toIntOrNull() }.sum()!=awayScore)
 val localDate: LocalDate? get()=runCatching { if(kickoff.length==10) LocalDate.parse(kickoff) else OffsetDateTime.parse(kickoff).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
}
