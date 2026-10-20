package com.matchcontrol.data

import com.matchcontrol.domain.*
import kotlinx.serialization.json.*
import java.time.*
internal fun JsonObject.objectAt(key: String)=this[key] as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.stringAt(key: String)=(this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.numberAt(key: String)=(this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.listAt(key: String)=(this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
internal object SportsMapper {
 fun game(row: JsonObject,sport: Sport): SportEvent?=runCatching {
  require(sport==Sport.BASKETBALL || sport==Sport.BASEBALL)
  val id=requireNotNull(row.numberAt("id"));val date=requireNotNull(row.stringAt("date"))
  val teams=row.objectAt("teams");val scores=row.objectAt("scores")
  fun team(side: String): Team { val t=teams.objectAt(side);return Team(requireNotNull(t.numberAt("id")),t.stringAt("name") ?: "Equipo",t.stringAt("logo")) }
  val home=team("home");val away=team("away");val status=row.objectAt("status").stringAt("short") ?: ""
  val state=when {
   status in setOf("FT","AOT","AP")->EventState.FINAL
   status in setOf("NS","TBD")->EventState.SCHEDULED
   status in setOf("POST","PST","SUSP","INT")->EventState.POSTPONED
   status in setOf("CANC","ABD")->EventState.CANCELLED
   status in setOf("Q1","Q2","Q3","Q4","OT","BT","HT","LIVE") || Regex("IN[0-9]+").matches(status)->EventState.LIVE
   else->EventState.PENDING
  }
  val h=scores.objectAt("home");val a=scores.objectAt("away")
  val periods=if(sport==Sport.BASKETBALL) (1..4).map { PeriodScore("$it.º cuarto",h.stringAt("quarter_$it"),a.stringAt("quarter_$it")) }+PeriodScore("Prórroga",h.stringAt("over_time"),a.stringAt("over_time"))
   else (h.objectAt("innings").keys+a.objectAt("innings").keys).distinct().sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }).map { key -> PeriodScore(if(key=="extra") "Extra" else "Entrada $key",h.objectAt("innings").stringAt(key),a.objectAt("innings").stringAt(key)) }
  val stats=if(sport==Sport.BASEBALL) listOf(PeriodScore("Hits",h.stringAt("hits"),a.stringAt("hits")),PeriodScore("Errores",h.stringAt("errors"),a.stringAt("errors"))) else emptyList()
  SportEvent(id.toString(),sport,"${home.name} vs ${away.name}",row.objectAt("league").stringAt("name") ?: sport.title,date,state,home,away,h.numberAt("total"),a.numberAt("total"),
   stage=when { sport==Sport.BASEBALL && status.startsWith("IN")->"Entrada ${status.removePrefix("IN")}";state==EventState.LIVE && status.startsWith("Q")->"${status.removePrefix("Q")}.º cuarto";state==EventState.LIVE && status=="OT"->"Prórroga";else->null },
   periods=periods.filter { it.home!=null || it.away!=null },statistics=stats.filter { it.home!=null || it.away!=null })
 }.getOrNull()
 fun races(root: JsonObject): List<JsonObject> = root.objectAt("MRData").objectAt("RaceTable").listAt("Races")
 fun race(row: JsonObject,now: Instant=Instant.now()): SportEvent?=runCatching {
  val season=requireNotNull(row.numberAt("season"));val round=requireNotNull(row.numberAt("round"));val date=requireNotNull(row.stringAt("date"))
  val time=row.stringAt("time");val kickoff=if(time!=null) date+"T"+time else date
  val drivers=row.listAt("Results").map { result ->
   val d=result.objectAt("Driver");DriverResult(result.numberAt("position"),listOfNotNull(d.stringAt("givenName"),d.stringAt("familyName")).joinToString(" "),result.objectAt("Constructor").stringAt("name") ?: "",result.objectAt("Time").stringAt("time"),result.stringAt("points"),result.stringAt("laps"),result.stringAt("status"))
  }.sortedBy { it.position ?: Int.MAX_VALUE }
  val state=when { drivers.isNotEmpty()->EventState.FINAL;(if(time==null) LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() else OffsetDateTime.parse(kickoff).toInstant())>now->EventState.SCHEDULED;else->EventState.PENDING }
  SportEvent("$season-$round",Sport.FORMULA1,row.stringAt("raceName") ?: "Gran Premio","Fórmula 1 · $season",kickoff,state,stage=if(time==null) "Horario por confirmar" else null,
   circuit=row.objectAt("Circuit").stringAt("circuitName"),drivers=drivers,season=season,round=round)
 }.getOrNull()
}
