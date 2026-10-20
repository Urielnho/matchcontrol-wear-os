package com.matchcontrol
import com.matchcontrol.domain.*
import com.matchcontrol.presentation.*
import com.matchcontrol.data.FootballMapper
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class PresentationRulesTest {
 private fun match(id: Int,status: String,time: String="2026-10-19T15:00:00Z")=Match(id,Team(id,"Club Atlético Norte"),Team(id+100,"Real Madrid"),"Liga",time,status)
 @Test fun featuredPrioritizesLiveFavoriteThenLiveThenUpcoming() {
  val live=match(1,"2H");val favorite=match(2,"2H");val next=match(3,"NS")
  assertEquals(favorite,featuredMatch(listOf(next,live,favorite),setOf("2")))
  assertEquals(live,featuredMatch(listOf(next,live),setOf("3")))
  assertEquals(next,featuredMatch(listOf(match(4,"FT"),next),emptySet()))
  assertNull(featuredMatch(listOf(match(4,"FT")),emptySet()))
 }
 @Test fun upcomingUsesInstantAcrossOffsets() {
  val late=match(1,"NS","2026-10-19T12:00:00-07:00")
  val early=match(2,"NS","2026-10-19T15:00:00Z")
  assertEquals(early,featuredMatch(listOf(late,early),emptySet()))
 }
 @Test fun codesKeepUnicodeAndOmitGenericClubWords() {
  assertEquals("ANO",teamCode("Club Atlético Norte"));assertEquals("MCI",teamCode("Manchester City International FC"));assertEquals("RMA",teamCode("Real Madrid Atlético"));assertEquals("RMA",teamCode("Real Madrid"));assertNotEquals(teamCode("Manchester City"),teamCode("Manchester United"))
 }
 @Test fun comparativeBarsNeverInventMissingOrInvalidValues() {
  assertEquals(.55f,MatchStatistic("pos","55%","45%").fraction()!!,.001f)
  assertEquals(.5f,MatchStatistic("shots","0","0").fraction()!!,.001f)
  listOf(MatchStatistic("s",null,"2"),MatchStatistic("s","—","2"),MatchStatistic("s","-1","3"),MatchStatistic("s","NaN","2")).forEach { assertNull(it.fraction()) }
 }
 @Test fun mapperPairsStatisticsByTeamIdAndKeepsEvents() {
  val json="""{"fixture":{"id":1,"date":"2026-10-19T15:00:00Z","status":{"short":"2H"}},"teams":{"home":{"id":10,"name":"A"},"away":{"id":20,"name":"B"}},"statistics":[{"team":{"id":20},"statistics":[{"type":"Total Shots","value":5}]},{"team":{"id":10},"statistics":[{"type":"Total Shots","value":8},{"type":"Ball Possession","value":"55%"}]}],"events":[{"time":{"elapsed":12},"team":{"id":20},"player":{"name":"Player"},"type":"Goal","detail":"Normal Goal"}]}"""
  val m=requireNotNull(FootballMapper.parse(Json.parseToJsonElement(json).jsonObject))
  assertEquals("8",m.comparisons.first { it.type=="Total Shots" }.home)
  assertEquals("5",m.comparisons.first { it.type=="Total Shots" }.away)
  assertNull(m.comparisons.first { it.type=="Ball Possession" }.fraction())
  assertEquals(20,m.timeline.single().teamId);assertEquals(12,m.timeline.single().minute)
 }
}
