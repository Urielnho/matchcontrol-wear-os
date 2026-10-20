package com.matchcontrol
import com.matchcontrol.data.FootballMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
class FootballMapperTest {
 private val fixture="""{
  "fixture":{"id":12,"date":"2026-10-17T01:30:00Z","status":{"short":"NS","elapsed":null}},
  "teams":{"home":{"id":1,"name":"Local","logo":null},"away":{"id":2,"name":"Visitante"}},
  "league":{"id":39,"name":"Liga de prueba"},"goals":{"home":null,"away":null},
  "statistics":[{"team":{"name":"Local"},"statistics":[{"type":"Ball Possession","value":null},{"type":"Shots on Goal","value":3}]}]
 }"""
 @Test fun incompleteScoresAndStatsRemainAbsent() {
  val result=requireNotNull(FootballMapper.parse(Json.parseToJsonElement(fixture).jsonObject))
  assertNull(result.homeGoals); assertNull(result.minute); assertNull(result.home.logo)
  assertEquals(listOf("Local: Shots on Goal · 3"), result.statistics)
  assertTrue(result.events.isEmpty()); assertEquals(39, result.leagueId)
 }
 @Test fun missingFixtureIdentityIsRejected() { assertNull(FootballMapper.parse(Json.parseToJsonElement("{} ").jsonObject)) }
}
