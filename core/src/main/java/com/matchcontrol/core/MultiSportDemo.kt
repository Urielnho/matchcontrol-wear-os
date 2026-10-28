package com.matchcontrol.core

/** Educational schedules and odds are explicitly fictitious, never provider data. */
object MultiSportDemo {
 fun upgrade(snapshot: Snapshot,now: Long=System.currentTimeMillis()): Snapshot {
  val template=seed(now);val race=template.first.first {it.sport==SportKind.FORMULA1}
  val eligible=snapshot.fixtures.filter {f->f.source=="DEMO" && f.sport==SportKind.FORMULA1 && f.status==FixtureStatus.SCHEDULED && f.startsAt>now && f.participants.size<11 && snapshot.bets.none {b->b.picks.any {it.matchId==f.id}}}
  if(eligible.isEmpty()) return snapshot
  val ids=eligible.map {it.id}.toSet()
  val extra=eligible.flatMap {f->template.second.filter {it.matchId==race.id && it.key !in f.participants.map {d->d.id}}.map {it.copy(id="${f.id}:${it.market.name}:${it.key}",matchId=f.id)}}
  return snapshot.copy(fixtures=snapshot.fixtures.map {if(it.id in ids) it.copy(participants=race.participants) else it},outcomes=snapshot.outcomes+extra,revision=snapshot.revision+1,updatedAt=now)
 }
 fun seed(now: Long): Pair<List<Fixture>,List<Outcome>> {
  val fixtures=mutableListOf<Fixture>();val outcomes=mutableListOf<Outcome>()
  fun add(f: Fixture) {
   fixtures+=f
   SportRules.markets(f.sport).forEach {market->
    val values=when(market) {
     MarketType.BASKETBALL_WINNER,MarketType.BASEBALL_WINNER->listOf(Triple("1",f.home.code,18500),Triple("2",f.away.code,20500))
     MarketType.TOTAL_POINTS_2155->listOf(Triple("OVER","Más de 215.5",19000),Triple("UNDER","Menos de 215.5",19000))
     MarketType.TOTAL_RUNS_85->listOf(Triple("OVER","Más de 8.5",19000),Triple("UNDER","Menos de 8.5",19000))
     else->f.participants.mapIndexed {i,d->Triple(d.id,d.name,when(market) {MarketType.RACE_WINNER->22000+i*6500;MarketType.RACE_PODIUM->13500+i*2200;else->11000+i*700})}
    }
    outcomes+=values.map {(key,label,odds)->Outcome("${f.id}:${market.name}:$key",f.id,market,key,label,odds,now)}
   }
  }
  listOf(SportKind.BASKETBALL,SportKind.BASEBALL).forEachIndexed {index,sport->
   repeat(2) {round->
    val base=930000+index*100+round*2
    val teams=if(sport==SportKind.BASKETBALL) listOf("Halcones Campus","Titanes del Valle") else listOf("Lobos del Parque","Águilas del Sur")
    add(Fixture((base+1).toString(),DemoTeam("${base+10}",teams[round%2],if(round==0) "LOC" else "VIS"),DemoTeam("${base+11}",teams[(round+1)%2],if(round==0) "VIS" else "LOC"),"demo-${sport.name}","Liga Campus · ${sportTitle(sport)}",now+90*60_000L+round*60*60_000L,sport=sport))
   }
  }
  val drivers=listOf("Mateo Solís","Sebastián Vega","Leo Campos","Bruno Torres","Nicolás Marín","Adrián Ríos","Diego Luna","Hugo Peña","Iván Mora","Lucas Sierra","Emilio Navarro","Gael Romero").mapIndexed {i,name->DemoTeam("demo-driver-$i",name,"P${i+1}")}
  repeat(2) {round->add(Fixture("${930301+round}",DemoTeam("demo-gp-$round","Gran Premio Campus ${round+1}","GP"),DemoTeam("demo-track-$round","Circuito Universitario","CIR"),"demo-f1","Campeonato Campus",now+120*60_000L+round*60*60_000L,sport=SportKind.FORMULA1,participants=drivers))}
  return fixtures to outcomes
 }
}
