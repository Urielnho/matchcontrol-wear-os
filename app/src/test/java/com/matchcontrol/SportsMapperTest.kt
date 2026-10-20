package com.matchcontrol

import com.matchcontrol.data.*
import com.matchcontrol.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.TimeZone

/** Public responses captured from the actual services; never bundled in the application. */
class SportsMapperTest {
 private fun response(name: String)=Json.parseToJsonElement(javaClass.getResource("/real-$name.json")!!.readText()).jsonObject
 private fun first(name: String)=response(name).getValue("response").jsonArray.first().jsonObject
 @Test fun basketballKeepsThreeDigitTotalsAndActualQuarters() {
  val e=requireNotNull(SportsMapper.game(first("basketball"),Sport.BASKETBALL))
  assertEquals(102,e.homeScore);assertEquals(58,e.awayScore);assertEquals(EventState.FINAL,e.state)
  assertEquals("31",e.periods.first().home);assertEquals("6",e.periods.first().away);assertEquals(4,e.periods.size);assertFalse(e.breakdownInconsistent)
 }
 @Test fun inconsistentPublishedBreakdownIsFlaggedWithoutInventingOvertime() {
  val row=response("basketball").getValue("response").jsonArray.map { it.jsonObject }.first { it.getValue("id").jsonPrimitive.int==525710 }
  val e=requireNotNull(SportsMapper.game(row,Sport.BASKETBALL))
  assertEquals(100,e.homeScore);assertEquals(111,e.awayScore)
  assertEquals(93,e.periods.mapNotNull { it.home?.toIntOrNull() }.sum())
  assertTrue(e.breakdownInconsistent)
  assertFalse(e.periods.any { it.label=="Prórroga" })
 }
 @Test fun baseballKeepsRunsInningsHitsAndErrorsSeparate() {
  val e=requireNotNull(SportsMapper.game(first("baseball"),Sport.BASEBALL))
  assertEquals(6,e.homeScore);assertEquals(10,e.awayScore)
  assertEquals(9,e.periods.size);assertEquals("2",e.periods.first().home)
  assertEquals("10",e.statistics.first { it.label=="Hits" }.home)
  assertEquals("0",e.statistics.first { it.label=="Errores" }.home)
 }
 @Test fun missingTotalNeverBecomesZero() {
  val row=first("basketball");val scores=row.getValue("scores").jsonObject
  val home=scores.getValue("home").jsonObject
  val modified=JsonObject(row+("scores" to JsonObject(scores+("home" to JsonObject(home-"total")))))
  assertNull(requireNotNull(SportsMapper.game(modified,Sport.BASKETBALL)).homeScore)
 }
 @Test fun raceUsesPublishedPositionsTimesAndPoints() {
  val race=requireNotNull(SportsMapper.race(SportsMapper.races(response("f1results")).first()))
  assertEquals(EventState.FINAL,race.state);assertEquals(1,race.drivers.first().position)
  assertEquals("Max Verstappen",race.drivers.first().name);assertEquals("25",race.drivers.first().points)
  assertEquals("+7.335",race.drivers[1].time);assertNull(race.homeScore);assertNull(race.awayScore)
 }
 @Test fun elapsedRaceWithoutPublishedResultsIsNotInventedAsFinishedOrLive() {
  val row=SportsMapper.races(response("f1")).first()
  val race=requireNotNull(SportsMapper.race(row,Instant.parse("2026-10-19T12:00:00Z")))
  assertEquals(EventState.PENDING,race.state);assertTrue(race.drivers.isEmpty())
 }
 @Test fun missingRaceTimeStaysUnknown() {
  val row=SportsMapper.races(response("f1")).first()
  val race=requireNotNull(SportsMapper.race(JsonObject(row-"time")))
  assertEquals(row.getValue("date").jsonPrimitive.content,race.kickoff)
  assertEquals("Horario por confirmar",race.stage)
 }
 @Test fun utcGamesAreAssignedToTheWatchLocalDay() {
  val old=TimeZone.getDefault()
  try {
   TimeZone.setDefault(TimeZone.getTimeZone("America/Phoenix"))
   val e=requireNotNull(SportsMapper.game(first("basketball"),Sport.BASKETBALL))
   assertEquals(LocalDate.of(2026,10,18),e.localDate)
  } finally { TimeZone.setDefault(old) }
 }
}
