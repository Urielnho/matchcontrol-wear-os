package com.matchcontrol.core

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

fun newId()=UUID.randomUUID().toString()
fun credits(cents: Long)=BigDecimal.valueOf(cents,2).toPlainString()
fun parseCredits(text: String): Long = text.trim().replace(',','.').toBigDecimal().setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
@Serializable enum class FixtureStatus { SCHEDULED,LIVE,FINAL,VOID,SUSPENDED }
@Serializable enum class BetStatus { PENDING,WON,LOST,VOID }
@Serializable enum class MarketType { WINNER,DOUBLE_CHANCE,OVER_UNDER_15,OVER_UNDER_25,BOTH_SCORE }
@Serializable data class DemoTeam(val id: String,val name: String,val code: String,val logo: String?=null)
@Serializable data class Fixture(val id: String,val home: DemoTeam,val away: DemoTeam,val leagueId: String,val league: String,val startsAt: Long,val status: FixtureStatus=FixtureStatus.SCHEDULED,val homeScore: Int?=null,val awayScore: Int?=null,val verified: Boolean=false,val source: String="DEMO",val events: List<String> = emptyList(),val statistics: Map<String,String> = emptyMap(),val regulationHomeScore: Int?=null,val regulationAwayScore: Int?=null)
@Serializable data class Outcome(val id: String,val matchId: String,val market: MarketType,val key: String,val label: String,val odds: Int,val updatedAt: Long,val open: Boolean=true) // odds / 10000
@Serializable data class Pick(val outcomeId: String,val matchId: String,val market: MarketType,val key: String,val label: String,val fixtureLabel: String,val lockedOdds: Int,val status: BetStatus=BetStatus.PENDING)
@Serializable data class Bet(val id: String,val requestId: String,val stake: Long,val totalOdds: String,val potential: Long,val picks: List<Pick>,val status: BetStatus=BetStatus.PENDING,val createdAt: Long,val settledAt: Long?=null,val returned: Long=0,val source: String="DEMO")
@Serializable data class WalletTransaction(val id: String,val kind: String,val amount: Long,val reference: String,val createdAt: Long)
@Serializable data class Profile(val id: String="local-demo",val displayName: String="Estudiante")
@Serializable data class Snapshot(val profile: Profile=Profile(),val balance: Long=1_000_000,val fixtures: List<Fixture> = emptyList(),val outcomes: List<Outcome> = emptyList(),val bets: List<Bet> = emptyList(),val transactions: List<WalletTransaction> = emptyList(),val favoriteTeams: Set<String> = emptySet(),val favoriteLeagues: Set<String> = emptySet(),val favoriteMatches: Set<String> = emptySet(),val processed: Set<String> = emptySet(),val revision: Long=1,val updatedAt: Long=0,val notifications: Boolean=false,val selectedMatch: String?=null,val source: String="DEMO",val dataUpdatedAt: Long=0)
@Serializable data class SyncCommand(val id: String=newId(),val action: String,val target: String="",val enabled: Boolean=false,val createdAt: Long=System.currentTimeMillis(),val profileId: String="",val source: String="DEMO")
@Serializable data class BetRequest(val requestId: String,val stake: Long,val outcomes: Map<String,Int>)

interface MatchesRepository { fun matches(): List<Fixture> }
interface ResultsRepository { fun results(): List<Fixture> }
interface OddsRepository { fun markets(matchId: String): List<Outcome> }
interface UsersRepository { fun profile(): Profile }
interface WalletsRepository { fun balance(): Long; fun reset(requestId: String) }
interface BetsRepository { fun place(request: BetRequest): Bet }
interface HistoryRepository { fun history(): List<Bet>; fun movements(): List<WalletTransaction> }

object BettingRules {
 fun totalOdds(odds: List<Int>): BigDecimal {
  require(odds.isNotEmpty() && odds.size<=10) { "Selecciona entre 1 y 10 eventos" }
  return odds.fold(BigDecimal.ONE) { acc,n -> require(n in 10001..1000000) { "Cuota inválida" };acc.multiply(BigDecimal.valueOf(n.toLong(),4)) }
 }
 fun returns(stake: Long,odds: List<Int>): Long = totalOdds(odds).multiply(BigDecimal.valueOf(stake)).setScale(0,RoundingMode.DOWN).longValueExact()
 fun result(f: Fixture,p: Pick): BetStatus {
  if(f.status==FixtureStatus.VOID) return BetStatus.VOID
  if(f.status!=FixtureStatus.FINAL || !f.verified) return BetStatus.PENDING
  val h=(if(f.source=="DEMO") f.homeScore else f.regulationHomeScore) ?: return BetStatus.PENDING
  val a=(if(f.source=="DEMO") f.awayScore else f.regulationAwayScore) ?: return BetStatus.PENDING
  val win=when(p.market) {
   MarketType.WINNER->when(p.key) { "1"->h>a;"X"->h==a;"2"->h<a;else->error("Resultado inválido") }
   MarketType.DOUBLE_CHANCE->when(p.key) { "1X"->h>=a;"X2"->h<=a;"12"->h!=a;else->error("Resultado inválido") }
   MarketType.OVER_UNDER_15->if(p.key=="OVER") h+a>=2 else h+a<=1
   MarketType.OVER_UNDER_25->if(p.key=="OVER") h+a>=3 else h+a<=2
   MarketType.BOTH_SCORE->if(p.key=="YES") h>0 && a>0 else h==0 || a==0
  }
  return if(win) BetStatus.WON else BetStatus.LOST
 }
 fun settle(bet: Bet,fixtures: List<Fixture>,now: Long): Bet {
  if(bet.status!=BetStatus.PENDING) return bet
  val picks=bet.picks.map { p->p.copy(status=fixtures.firstOrNull { it.id==p.matchId }?.let { result(it,p) } ?: BetStatus.PENDING) }
  if(picks.any { it.status==BetStatus.PENDING }) return bet.copy(picks=picks)
  val status=when { picks.any { it.status==BetStatus.LOST }->BetStatus.LOST;picks.all { it.status==BetStatus.VOID }->BetStatus.VOID;else->BetStatus.WON }
  val valid=picks.filter { it.status!=BetStatus.VOID }
  val returned=when(status) { BetStatus.WON->returns(bet.stake,valid.map { it.lockedOdds });BetStatus.VOID->bet.stake;else->0 }
  return bet.copy(picks=picks,status=status,settledAt=now,returned=returned)
 }
}
