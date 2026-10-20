package com.matchcontrol.data
import com.matchcontrol.domain.*
import kotlinx.serialization.json.*
private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
internal object FootballMapper {
 private fun pairedStatistics(row: JsonObject, homeId: Int, awayId: Int): List<MatchStatistic> {
  fun values(id: Int): Map<String,String?> = row.array("statistics").mapNotNull { it as? JsonObject }
   .firstOrNull { it.obj("team").int("id")==id }?.array("statistics")?.mapNotNull { v ->
    val o=v as? JsonObject ?: return@mapNotNull null
    val type=o.str("type") ?: return@mapNotNull null
    type to o.str("value")
   }?.toMap() ?: emptyMap()
  val h=values(homeId); val a=values(awayId)
  return (h.keys+a.keys).distinct().map { MatchStatistic(it,h[it],a[it]) }.filter { it.home!=null || it.away!=null }
 }
 internal fun parse(row: JsonObject): Match? = runCatching {
  val fixture=row.obj("fixture"); val teams=row.obj("teams"); val goals=row.obj("goals")
  fun team(key: String): Team { val t=teams.obj(key); return Team(requireNotNull(t.int("id")), t.str("name") ?: "Equipo", t.str("logo")) }
  Match(requireNotNull(fixture.int("id")),team("home"),team("away"),row.obj("league").str("name") ?: "Competición",requireNotNull(fixture.str("date")),fixture.obj("status").str("short") ?: "TBD",fixture.obj("status").int("elapsed"),goals.int("home"),goals.int("away"),
   row.array("events").map { e -> val o=e.jsonObject; "${o.obj("time").int("elapsed") ?: ""}′ · ${o.str("type") ?: ""} · ${o.str("detail") ?: ""} · ${o.obj("player").str("name") ?: ""}" },
   row.array("statistics").flatMap { s -> val o=s.jsonObject; o.array("statistics").mapNotNull { t -> val v=t.jsonObject; val value=v["value"]; if(value==null || value is JsonNull) null else "${o.obj("team").str("name")}: ${v.str("type")} · ${value.jsonPrimitive.content}" } }, leagueId=row.obj("league").int("id"),
   timeline=row.array("events").mapNotNull { e -> (e as? JsonObject)?.let { o -> MatchEvent(o.obj("time").int("elapsed"),o.obj("team").int("id"),o.obj("player").str("name"),o.str("type") ?: "Event",o.str("detail") ?: "",o.obj("assist").str("name")) } },
   comparisons=pairedStatistics(row, team("home").id, team("away").id))
 }.getOrNull()
}
