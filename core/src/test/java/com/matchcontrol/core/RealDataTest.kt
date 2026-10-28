package com.matchcontrol.core

import org.junit.Assert.*
import org.junit.Test

class RealDataTest {
 private fun fixture(id: String="api:123",sport: SportKind=SportKind.FOOTBALL)=Fixture(id,DemoTeam("1","Local","LOC"),DemoTeam("2","Visitante","VIS"),"7","Competición",2_000_000_000_000,source=RealData.Source,sport=sport)
 private fun real(vararg fixtures: Fixture)=Snapshot(fixtures=fixtures.toList(),source=RealData.Source)
 @Test fun oldPracticeDoesNotBecomeARealAccountOrBalance() {
  val old=DemoEngine.seed().copy(favoriteTeams=setOf("900001"))
  val clean=RealData.only(old)
  assertTrue(clean.fixtures.isEmpty());assertTrue(clean.bets.isEmpty());assertTrue(clean.favoriteTeams.isEmpty())
  assertEquals(0L,clean.balance);assertTrue(clean.profile.id.isBlank());assertEquals(RealData.Source,clean.source)
 }
 @Test fun sourceAndCanonicalIdAreBothRequiredAndOrphanQuotesAreRemoved() {
  val provider=fixture();val fake=provider.copy(id="910001",source="DEMO")
  val renamed=fake.copy(source=RealData.Source)
  val valid=Outcome("quote",provider.id,MarketType.WINNER,"1","Local",18500,0)
  val clean=RealData.only(real(provider,fake,renamed).copy(outcomes=listOf(valid,valid.copy(id="fake",matchId=fake.id))))
  assertEquals(listOf(provider),clean.fixtures);assertEquals(listOf(valid),clean.outcomes)
 }
 @Test fun guestHasNoPersonalWalletHistoryOrIdentity() {
  val provider=fixture();val pick=Pick("quote",provider.id,MarketType.WINNER,"1","Local","Evento",18500)
  val personal=real(provider).copy(profile=Profile("account","Persona"),balance=10000,accountSignedIn=true,
   bets=listOf(Bet("bet","request",100,"1.85",185,listOf(pick),createdAt=0,source=RealData.Source)),transactions=listOf(WalletTransaction("tx","STAKE",-100,"bet",0)))
  val public=RealData.guest(personal)
  assertEquals(listOf(provider),public.fixtures);assertTrue(public.profile.id.isBlank());assertEquals(0L,public.balance)
  assertFalse(public.accountSignedIn);assertTrue(public.bets.isEmpty());assertTrue(public.transactions.isEmpty())
 }
 @Test fun oldFictionalBetsAndRelatedFavoritesNeverLeakIntoRealSnapshots() {
  val provider=fixture();val old=DemoEngine.seed();val pick=Pick("fake","910001",MarketType.WINNER,"1","Fake","Fake",18500)
  val fakeBet=Bet("practice","request",100,"1.85",185,listOf(pick),createdAt=0)
  val clean=RealData.only(real(provider).copy(profile=Profile("real-user","Cuenta"),balance=99900,
   fixtures=listOf(provider)+old.fixtures,bets=listOf(fakeBet),transactions=listOf(WalletTransaction("tx","STAKE",-100,fakeBet.id,0)),
   favoriteTeams=setOf("1","900001"),favoriteMatches=setOf(provider.id,"910001"),favoriteTeamDetails=old.fixtures.map {it.home}))
  assertTrue(clean.bets.isEmpty());assertTrue(clean.transactions.isEmpty());assertEquals(setOf("1"),clean.favoriteTeams)
  assertEquals(setOf(provider.id),clean.favoriteMatches);assertTrue(clean.favoriteTeamDetails.isEmpty())
 }
 @Test fun switchingSportsRetainsOtherProviderEventsAndReplacesTheirQuotes() {
  val football=fixture();val basket=fixture("basket:456",SportKind.BASKETBALL)
  val oldQuote=Outcome("quote",football.id,MarketType.WINNER,"1","Local",18500,0)
  val saved=real(football).copy(outcomes=listOf(oldQuote),favoriteMatches=setOf(football.id))
  val both=RealData.mergeGuest(saved,real(basket))
  assertEquals(setOf(football.id,basket.id),both.fixtures.map {it.id}.toSet());assertEquals(listOf(oldQuote),both.outcomes)
  val updated=RealData.mergeGuest(both,real(football.copy(status=FixtureStatus.LIVE)).copy(stale=true))
  assertTrue(updated.outcomes.isEmpty());assertEquals(setOf(football.id),updated.favoriteMatches)
  assertTrue(updated.stale)
 }
 @Test fun everySportUsesCanonicalProviderIds() {
  listOf("api:1","basket:2","base:3","f1:2026:4").forEach {assertTrue(RealData.isEvent(it))}
  listOf("910001","demo:1","api:-1","api:1/odds","f1:season:4").forEach {assertFalse(RealData.isEvent(it))}
 }
}
