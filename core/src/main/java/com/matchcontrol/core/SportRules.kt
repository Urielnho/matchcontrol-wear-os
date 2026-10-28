package com.matchcontrol.core

fun totalOddsLabel(value: String): String {
 val exact=java.math.BigDecimal(value)
 val shown=exact.setScale(6,java.math.RoundingMode.DOWN)
 return (if(exact.compareTo(shown)!=0) "≈ " else "")+shown.stripTrailingZeros().toPlainString()
}

fun sportTitle(s: SportKind)=when(s) {SportKind.FOOTBALL->"Fútbol";SportKind.BASKETBALL->"Baloncesto";SportKind.BASEBALL->"Béisbol";SportKind.FORMULA1->"F1"}
fun eventLabel(f: Fixture)=if(f.sport==SportKind.FORMULA1) f.home.name else "${f.home.code} · ${f.away.code}"
fun marketTitle(m: MarketType)=when(m) {
 MarketType.WINNER->"Ganador · 1X2";MarketType.DOUBLE_CHANCE->"Doble oportunidad";MarketType.OVER_UNDER_15->"Goles 1.5";MarketType.OVER_UNDER_25->"Goles 2.5";MarketType.BOTH_SCORE->"Ambos anotan"
 MarketType.BASKETBALL_WINNER->"Ganador · incluye prórroga";MarketType.TOTAL_POINTS_2155->"Total de puntos 215.5"
 MarketType.BASEBALL_WINNER->"Ganador · incluye entradas extra";MarketType.TOTAL_RUNS_85->"Total de carreras 8.5"
 MarketType.RACE_WINNER->"Ganador de la carrera";MarketType.RACE_PODIUM->"Termina en el podio";MarketType.RACE_TOP10->"Termina en el top 10"
}
fun sportRules(s: SportKind)=when(s) {
 SportKind.FOOTBALL->"Tiempo reglamentario, sin prórroga ni penaltis. Se requiere el marcador reglamentario verificado."
 SportKind.BASKETBALL->"Marcador final, incluida la prórroga. Ganador sin empate; total de ambos equipos sobre o bajo 215.5 puntos."
 SportKind.BASEBALL->"Marcador final, incluidas entradas extra. Total de ambos equipos sobre o bajo 8.5 carreras. Un empate oficial anula el mercado de ganador."
 SportKind.FORMULA1->"Clasificación final oficial de la carrera principal, sin sprint. Podio: posiciones 1–3. Top 10: posiciones 1–10. Un piloto no clasificado pierde; una carrera anulada devuelve los créditos."
}+" Una anulación elimina esa cuota del parlay; si todas se anulan, se devuelve el importe."

object SportRules {
 fun validOutcome(f: Fixture,market: MarketType,key: String): Boolean = market in markets(f.sport) && when(market) {
  MarketType.WINNER->key in listOf("1","X","2")
  MarketType.DOUBLE_CHANCE->key in listOf("1X","X2","12")
  MarketType.BOTH_SCORE->key in listOf("YES","NO")
  MarketType.BASKETBALL_WINNER,MarketType.BASEBALL_WINNER->key in listOf("1","2")
  MarketType.RACE_WINNER,MarketType.RACE_PODIUM,MarketType.RACE_TOP10->f.participants.any {it.id==key}
  else->key in listOf("OVER","UNDER")
 }
 fun markets(s: SportKind): Set<MarketType> = when(s) {
  SportKind.FOOTBALL->setOf(MarketType.WINNER,MarketType.DOUBLE_CHANCE,MarketType.OVER_UNDER_15,MarketType.OVER_UNDER_25,MarketType.BOTH_SCORE)
  SportKind.BASKETBALL->setOf(MarketType.BASKETBALL_WINNER,MarketType.TOTAL_POINTS_2155)
  SportKind.BASEBALL->setOf(MarketType.BASEBALL_WINNER,MarketType.TOTAL_RUNS_85)
  SportKind.FORMULA1->setOf(MarketType.RACE_WINNER,MarketType.RACE_PODIUM,MarketType.RACE_TOP10)
 }
 fun result(f: Fixture,p: Pick): BetStatus {
  if(f.status==FixtureStatus.VOID) return BetStatus.VOID
  if(f.status!=FixtureStatus.FINAL || !f.verified || !validOutcome(f,p.market,p.key)) return BetStatus.PENDING
  fun decided(win: Boolean)=if(win) BetStatus.WON else BetStatus.LOST
  if(f.sport==SportKind.FORMULA1) {
   if(!f.classificationComplete) return BetStatus.PENDING
   val driver=f.raceResults.firstOrNull {it.driverId==p.key} ?: return BetStatus.PENDING
   val cutoff=when(p.market) {MarketType.RACE_WINNER->1;MarketType.RACE_PODIUM->3;MarketType.RACE_TOP10->10;else->return BetStatus.PENDING}
   return decided(driver.classified && driver.position!=null && driver.position in 1..cutoff)
  }
  val h=f.homeScore ?: return BetStatus.PENDING;val a=f.awayScore ?: return BetStatus.PENDING
  if(f.sport==SportKind.BASKETBALL && h==a) return BetStatus.PENDING
  return when(p.market) {
   MarketType.BASKETBALL_WINNER->if(h==a) BetStatus.PENDING else decided(if(p.key=="1") h>a else h<a)
   MarketType.BASEBALL_WINNER->if(h==a) BetStatus.VOID else decided(if(p.key=="1") h>a else h<a)
   MarketType.TOTAL_POINTS_2155->decided(if(p.key=="OVER") h+a>=216 else h+a<=215)
   MarketType.TOTAL_RUNS_85->decided(if(p.key=="OVER") h+a>=9 else h+a<=8)
   else->BetStatus.PENDING
  }
 }
}
