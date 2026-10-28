package com.matchcontrol.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WatchMirrorTest {
 @Test fun pendingFavoritesAreImmediateAndScopedToTheCurrentAccount() {
  val state=DemoEngine.seed().copy(profile=Profile("account-a","Ana"))
  val add=SyncCommand(action="team",target="900001",enabled=true,profileId="account-a",source=state.source)
  assertTrue("900001" in WatchMirror.optimistic(state,listOf(add)).favoriteTeams)
  val remove=add.copy(id=newId(),enabled=false)
  assertFalse("900001" in WatchMirror.optimistic(state,listOf(add,remove)).favoriteTeams)
  assertTrue(WatchMirror.optimistic(state.copy(profile=Profile("account-b","Luis")),listOf(add)).favoriteTeams.isEmpty())
  assertTrue(WatchMirror.optimistic(state.copy(source="API_FOOTBALL"),listOf(add)).favoriteTeams.isEmpty())
 }
 @Test fun olderActiveParlayIsKeptAheadOfSettledHistory() {
  val seed=DemoEngine.seed()
  val selected=seed.outcomes.filter {it.market==MarketType.WINNER && it.key=="1"}.take(2)
  val pending=DemoEngine.place(seed,BetRequest("parlay",10000,selected.associate {it.id to it.odds})).second
  val settled=(1..40).map {pending.copy(id="settled-$it",createdAt=pending.createdAt+it,status=BetStatus.WON)}
  val mirror=WatchMirror.compact(seed.copy(bets=listOf(pending)+settled))
  assertEquals(41,mirror.totalBetCount)
  assertEquals(30,mirror.bets.size)
  assertEquals(pending,mirror.bets.first {it.id==pending.id})
  assertEquals(2,mirror.bets.first {it.id==pending.id}.picks.size)
 }
 @Test fun favoritesKeepNamesAndLeaguesBeyondTodaysList() {
  val seed=DemoEngine.seed()
  val team=seed.fixtures.first().home
  val league=seed.fixtures.first().leagueId
  val mirror=WatchMirror.compact(seed.copy(favoriteTeams=setOf(team.id),favoriteLeagues=setOf(league)))
  assertEquals(team,mirror.favoriteTeamDetails.single())
  assertEquals(league,mirror.favoriteLeagueDetails.single().id)
  assertTrue(mirror.fixtures.any {it.leagueId==league})
  val withoutFixtures=WatchMirror.compact(mirror.copy(fixtures=emptyList()))
  assertEquals(team,withoutFixtures.favoriteTeamDetails.single())
 }
 @Test fun oversizedMatchDetailsCannotBlockAccountDelivery() {
  val seed=DemoEngine.seed()
  val huge=seed.copy(fixtures=seed.fixtures.map {it.copy(events=List(50) {"e".repeat(5000)},statistics=(1..50).associate {"stat-$it" to "s".repeat(5000)})})
  val mirror=WatchMirror.compact(huge,12_000)
  assertTrue(Json.encodeToString(mirror).toByteArray().size<=12_000)
  assertEquals(seed.profile,mirror.profile)
  assertEquals(seed.balance,mirror.balance)
 }
}
