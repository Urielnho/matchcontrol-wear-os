package com.matchcontrol.core

import org.junit.Assert.*
import org.junit.Test

class MultiSportRulesTest {
 private val now=2_000_000_000_000L
 private fun seed()=DemoEngine.seed(now)
 private fun pick(f: Fixture,m: MarketType,key: String)=Pick("outcome",f.id,m,key,key,eventLabel(f),18500,sport=f.sport)
 @Test fun everySportHasItsOwnMarkets() {
  val s=seed();assertEquals(SportKind.entries.toSet(),s.fixtures.map {it.sport}.toSet())
  s.fixtures.forEach {f->s.outcomes.filter {it.matchId==f.id}.forEach {assertTrue(SportRules.validOutcome(f,it.market,it.key))}}
  assertTrue(s.outcomes.any {it.market==MarketType.RACE_PODIUM})
 }
 @Test fun basketballUsesFinalPointsIncludingOvertime() {
  val f=seed().fixtures.first {it.sport==SportKind.BASKETBALL}.copy(status=FixtureStatus.FINAL,verified=true,homeScore=112,awayScore=105)
  assertEquals(BetStatus.WON,BettingRules.result(f,pick(f,MarketType.BASKETBALL_WINNER,"1")))
  assertEquals(BetStatus.WON,BettingRules.result(f,pick(f,MarketType.TOTAL_POINTS_2155,"OVER")))
  assertEquals(BetStatus.LOST,BettingRules.result(f,pick(f,MarketType.TOTAL_POINTS_2155,"UNDER")))
  assertEquals(BetStatus.PENDING,BettingRules.result(f.copy(homeScore=105),pick(f,MarketType.TOTAL_POINTS_2155,"UNDER")))
 }
 @Test fun baseballTotalsAndOfficialTieHaveDifferentRules() {
  val f=seed().fixtures.first {it.sport==SportKind.BASEBALL}.copy(status=FixtureStatus.FINAL,verified=true,homeScore=4,awayScore=4)
  assertEquals(BetStatus.VOID,BettingRules.result(f,pick(f,MarketType.BASEBALL_WINNER,"1")))
  assertEquals(BetStatus.WON,BettingRules.result(f,pick(f,MarketType.TOTAL_RUNS_85,"UNDER")))
  assertEquals(BetStatus.WON,BettingRules.result(f.copy(homeScore=5),pick(f,MarketType.TOTAL_RUNS_85,"OVER")))
 }
 @Test fun raceWinnerPodiumAndTopTenUseClassification() {
  val f0=seed().fixtures.first {it.sport==SportKind.FORMULA1}
  val f=f0.copy(status=FixtureStatus.FINAL,verified=true,classificationComplete=true,raceResults=f0.participants.mapIndexed {i,d->RaceResult(d.id,i+1,true)})
  assertEquals(BetStatus.WON,BettingRules.result(f,pick(f,MarketType.RACE_WINNER,f.participants[0].id)))
  assertEquals(BetStatus.WON,BettingRules.result(f,pick(f,MarketType.RACE_PODIUM,f.participants[2].id)))
  assertEquals(BetStatus.LOST,BettingRules.result(f,pick(f,MarketType.RACE_PODIUM,f.participants[3].id)))
  assertEquals(BetStatus.WON,BettingRules.result(f,pick(f,MarketType.RACE_TOP10,f.participants[5].id)))
  assertEquals(BetStatus.LOST,BettingRules.result(f,pick(f,MarketType.RACE_TOP10,f.participants[10].id)))
  val dnf=f.copy(raceResults=f.raceResults.mapIndexed {i,r->if(i==0) r.copy(position=null,classified=false) else r})
  assertEquals(BetStatus.LOST,BettingRules.result(dnf,pick(f,MarketType.RACE_TOP10,f.participants[0].id)))
 }
 @Test fun incompleteOrMissingRaceResultsStayPending() {
  val f0=seed().fixtures.first {it.sport==SportKind.FORMULA1}
  val f=f0.copy(status=FixtureStatus.FINAL,verified=true)
  assertEquals(BetStatus.PENDING,BettingRules.result(f,pick(f,MarketType.RACE_WINNER,f.participants[0].id)))
  assertEquals(BetStatus.PENDING,BettingRules.result(f.copy(classificationComplete=true),pick(f,MarketType.RACE_WINNER,f.participants[0].id)))
 }
 @Test fun mixedParlayWaitsForAllFourSportsAndSettlesOnce() {
  val s=seed();val selected=SportKind.entries.map {sport->val f=s.fixtures.first {it.sport==sport};s.outcomes.first {it.matchId==f.id && it.market==when(sport) {SportKind.FOOTBALL->MarketType.WINNER;SportKind.BASKETBALL->MarketType.BASKETBALL_WINNER;SportKind.BASEBALL->MarketType.BASEBALL_WINNER;SportKind.FORMULA1->MarketType.RACE_WINNER} && it.key==if(sport==SportKind.FORMULA1) f.participants[0].id else "1"}}
  val (placed,bet)=DemoEngine.place(s,BetRequest("four-sports",10000,selected.associate {it.id to it.odds}),now)
  var done=placed
  selected.filter {o->s.fixtures.first {it.id==o.matchId}.sport!=SportKind.FORMULA1}.forEach {val basket=s.fixtures.first {f->f.id==it.matchId}.sport==SportKind.BASKETBALL;done=DemoLab.finish(done,it.matchId,if(basket) 112 else 2,if(basket) 105 else 1)}
  assertEquals(BetStatus.PENDING,done.bets.single().status)
  val race=s.fixtures.first {it.id==selected.last().matchId}
  done=DemoLab.finishRace(done,race.id,race.participants.map {it.id})
  assertEquals(BetStatus.WON,done.bets.single().status);assertEquals(139295L,done.bets.single().returned)
  assertEquals(done,DemoEngine.settle(done));assertEquals(bet.stake,done.bets.single().stake)
 }
 @Test fun incompatibleMarketAndDuplicateRaceSelectionsAreRejected() {
  val s=seed();val race=s.fixtures.first {it.sport==SportKind.FORMULA1};val offers=s.outcomes.filter {it.matchId==race.id}.take(2)
  assertThrows(IllegalArgumentException::class.java) {DemoEngine.place(s,BetRequest("same-race",10000,offers.associate {it.id to it.odds}),now)}
  val f=s.fixtures.first {it.sport==SportKind.BASKETBALL};val bad=Outcome("invalid",f.id,MarketType.BOTH_SCORE,"YES","Sí",18500,now)
  assertThrows(IllegalArgumentException::class.java) {DemoEngine.place(s.copy(outcomes=s.outcomes+bad),BetRequest("bad",10000,mapOf(bad.id to bad.odds)),now)}
 }
 @Test fun demoRaceRequiresFullUniqueOrder() {
  val s=seed();val f=s.fixtures.first {it.sport==SportKind.FORMULA1}
  assertThrows(IllegalArgumentException::class.java) {DemoLab.finishRace(s,f.id,List(f.participants.size) {f.participants[0].id})}
  val done=DemoLab.cancel(s,f.id)
  assertTrue(done.outcomes.filter {it.matchId==f.id}.none {it.open})
 }
 @Test fun oldUnbetGridUpgradesWithoutChangingWalletOrReferencedRaces() {
  val s=seed();val f=s.fixtures.first {it.sport==SportKind.FORMULA1}
  val old=s.copy(fixtures=s.fixtures.map {if(it.id==f.id) it.copy(participants=it.participants.take(6)) else it},outcomes=s.outcomes.filterNot {it.matchId==f.id && it.key in f.participants.drop(6).map {d->d.id}})
  val upgraded=MultiSportDemo.upgrade(old,now)
  assertEquals(12,upgraded.fixtures.first {it.id==f.id}.participants.size)
  assertEquals(old.balance,upgraded.balance);assertEquals(old.transactions,upgraded.transactions)
  val offer=old.outcomes.first {it.matchId==f.id && it.market==MarketType.RACE_WINNER}
  val placed=DemoEngine.place(old,BetRequest("old-grid",10000,mapOf(offer.id to offer.odds)),now).first
  assertEquals(6,MultiSportDemo.upgrade(placed,now).fixtures.first {it.id==f.id}.participants.size)
 }
}
