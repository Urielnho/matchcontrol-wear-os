package com.matchcontrol.core

import java.time.LocalDate
import java.time.ZoneId

/** Pure sandbox authority. All mutations are persisted atomically by the repository. */
object DemoEngine {
 fun seed(now: Long=System.currentTimeMillis()): Snapshot {
  val base=LocalDate.now().atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val teams=listOf(DemoTeam("900001","Atlético Aurora","AUR"),DemoTeam("900002","Deportivo Horizonte","HOR"),DemoTeam("900003","Unión del Valle","VAL"),DemoTeam("900004","Estrella del Norte","NOR"),DemoTeam("900005","Club Costa Azul","AZU"),DemoTeam("900006","Real Montaña","MON"))
  val fixtures=(0..5).map { i-> Fixture("${910001+i}",teams[i],teams[(i+1)%6],if(i%2==0) "920001" else "920002",if(i%2==0) "Copa Campus" else "Liga Universitaria",if(i<3) maxOf(base+i*3_600_000L,now+3_600_000L+i*3_600_000L) else if(i==3) now-1_800_000 else base-86_400_000,
   if(i<3) FixtureStatus.SCHEDULED else if(i==3) FixtureStatus.LIVE else FixtureStatus.FINAL,if(i<3) null else if(i==3) 1 else 2,if(i<3) null else if(i==3) 0 else 1,i>=4,
   events=if(i>=3) listOf("23′ · Gol de ${teams[i].name}","41′ · Tarjeta amarilla") else emptyList(),statistics=if(i>=3) mapOf("Posesión" to "56% · 44%","Tiros" to "8 · 5") else emptyMap()) }
  val markets=fixtures.flatMap { f->MarketType.entries.flatMap { type->
   val values=when(type) { MarketType.WINNER->listOf(Triple("1",f.home.code,18500),Triple("X","Empate",32500),Triple("2",f.away.code,24000));MarketType.DOUBLE_CHANCE->listOf(Triple("1X","1 o empate",13500),Triple("X2","Empate o 2",14500),Triple("12","Sin empate",12500));MarketType.OVER_UNDER_15->listOf(Triple("OVER","Más de 1.5",14000),Triple("UNDER","Menos de 1.5",26000));MarketType.OVER_UNDER_25->listOf(Triple("OVER","Más de 2.5",19000),Triple("UNDER","Menos de 2.5",18000));MarketType.BOTH_SCORE->listOf(Triple("YES","Sí",17500),Triple("NO","No",20000));else->emptyList() }
   values.map { (key,label,odds)->Outcome("${f.id}:${type.name}:$key",f.id,type,key,label,odds,now,f.status==FixtureStatus.SCHEDULED) }
  } }
  val extra=MultiSportDemo.seed(now)
  return Snapshot(profile=Profile("local-demo","Estudiante"),balance=1_000_000,fixtures=fixtures+extra.first,outcomes=markets+extra.second,transactions=listOf(WalletTransaction(newId(),"INITIAL",1_000_000,"demo-profile",now)),updatedAt=now)
 }
 fun place(s: Snapshot,r: BetRequest,now: Long=System.currentTimeMillis()): Pair<Snapshot,Bet> {
  require(r.requestId.isNotBlank())
  s.bets.firstOrNull { it.requestId==r.requestId }?.let {
   require(it.stake==r.stake && it.picks.associate { p->p.outcomeId to p.lockedOdds }==r.outcomes) { "El identificador ya corresponde a otro boleto" }
   return s to it
  }
  require(r.stake in 100..10_000_000 && r.stake<=s.balance) { "Créditos insuficientes o importe inválido (mínimo 1)" }
  require(r.outcomes.size in 1..10) { "Selecciona entre 1 y 10 eventos" }
  val outcomes=r.outcomes.map { (id,odds)->s.outcomes.firstOrNull { it.id==id }?.also { require(it.open && it.odds==odds) { "El mercado está cerrado o cambió la cuota" } } ?: error("Selección no disponible") }
  require(outcomes.map { it.matchId }.distinct().size==outcomes.size) { "Solo una selección por partido" }
  val picks=outcomes.map { o->
   val f=s.fixtures.first { it.id==o.matchId }
   require(f.status==FixtureStatus.SCHEDULED && f.startsAt>now && f.source=="DEMO") { "El partido ya comenzó o no pertenece al sandbox" }
   require(SportRules.validOutcome(f,o.market,o.key)) {"Mercado incompatible con el deporte"}
   Pick(o.id,f.id,o.market,o.key,o.label,eventLabel(f),o.odds,sport=f.sport)
  }
  val bet=Bet(newId(),r.requestId,r.stake,BettingRules.totalOdds(outcomes.map { it.odds }).toPlainString(),BettingRules.returns(r.stake,outcomes.map { it.odds }),picks,createdAt=now)
  return s.copy(balance=s.balance-r.stake,bets=s.bets+bet,transactions=s.transactions+WalletTransaction(newId(),"STAKE",-r.stake,bet.id,now)) to bet
 }
 fun settle(s: Snapshot,now: Long=System.currentTimeMillis()): Snapshot {
  var next=s
  val bets=s.bets.map { old->
   val bet=BettingRules.settle(old,s.fixtures,now)
   if(old.status==BetStatus.PENDING && bet.status!=BetStatus.PENDING) {
    next=next.copy(balance=Math.addExact(next.balance,bet.returned),transactions=next.transactions+WalletTransaction("settle-${bet.id}",if(bet.status==BetStatus.VOID) "REFUND" else "SETTLEMENT",bet.returned,bet.id,now))
   }
   bet
  }
  return next.copy(bets=bets)
 }
 fun reset(s: Snapshot,id: String,now: Long=System.currentTimeMillis()): Snapshot {
  require(id.isNotBlank())
  if("reset-$id" in s.processed) return s
  require(s.bets.none { it.status==BetStatus.PENDING }) { "Resuelve los boletos pendientes antes de restablecer el saldo" }
  return s.copy(balance=1_000_000,processed=s.processed+"reset-$id",transactions=s.transactions+WalletTransaction(newId(),"RESET",1_000_000-s.balance,id,now))
 }
}
