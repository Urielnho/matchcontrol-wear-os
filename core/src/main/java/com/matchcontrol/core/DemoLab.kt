package com.matchcontrol.core

/** Explicit DEMO laboratory, separate from match/market/bet screens. Never accepts real events. */
object DemoLab {
 fun newRound(s: Snapshot,now: Long=System.currentTimeMillis()): Snapshot {
  val seeded=DemoEngine.seed(now)
  // Watch uses compact numeric legacy IDs; use a checked monotonically increasing range.
  val start=(s.fixtures.mapNotNull {it.id.toLongOrNull()}.maxOrNull() ?: 910000)+1
  val ids=seeded.fixtures.mapIndexed {i,f->f.id to (start+i).toString()}.toMap()
  require(start+seeded.fixtures.size<Int.MAX_VALUE)
  return s.copy(fixtures=s.fixtures+seeded.fixtures.map {it.copy(id=ids.getValue(it.id))},outcomes=s.outcomes+seeded.outcomes.map {it.copy(id=it.id.replaceBefore(':',ids.getValue(it.matchId)),matchId=ids.getValue(it.matchId))})
 }
 fun finish(s: Snapshot,id: String,home: Int,away: Int): Snapshot {
  val fixture=s.fixtures.first { it.id==id };require(fixture.source=="DEMO")
  require(fixture.sport!=SportKind.FORMULA1)
  val max=if(fixture.sport==SportKind.BASKETBALL) 300 else 100
  require(home in 0..max && away in 0..max) {"Marcador fuera del rango permitido"}
  require(fixture.sport!=SportKind.BASKETBALL || home!=away) {"El baloncesto debe terminar sin empate"}
  require(fixture.status!=FixtureStatus.FINAL && fixture.status!=FixtureStatus.VOID) { "Resultado ya confirmado" }
  return DemoEngine.settle(s.copy(fixtures=s.fixtures.map { if(it.id==id) it.copy(status=FixtureStatus.FINAL,homeScore=home,awayScore=away,verified=true,events=it.events+"Final DEMO confirmado: $home–$away") else it },outcomes=s.outcomes.map { if(it.matchId==id) it.copy(open=false) else it }))
 }
 fun finishRace(s: Snapshot,id: String,order: List<String>): Snapshot {
  val f=s.fixtures.first {it.id==id};require(f.source=="DEMO" && f.sport==SportKind.FORMULA1 && f.status !in listOf(FixtureStatus.FINAL,FixtureStatus.VOID))
  require(order.size==f.participants.size && order.toSet()==f.participants.map {it.id}.toSet()) {"Se requiere una clasificación completa sin duplicados"}
  val results=order.mapIndexed {i,driver->RaceResult(driver,i+1,true)}
  return DemoEngine.settle(s.copy(fixtures=s.fixtures.map {if(it.id==id) it.copy(status=FixtureStatus.FINAL,verified=true,classificationComplete=true,raceResults=results) else it},outcomes=s.outcomes.map {if(it.matchId==id) it.copy(open=false) else it}))
 }
 fun cancel(s: Snapshot,id: String): Snapshot {
  require(s.fixtures.any { it.id==id && it.source=="DEMO" && it.status in listOf(FixtureStatus.SCHEDULED,FixtureStatus.LIVE) })
  return DemoEngine.settle(s.copy(fixtures=s.fixtures.map { if(it.id==id) it.copy(status=FixtureStatus.VOID,verified=true) else it },outcomes=s.outcomes.map { if(it.matchId==id) it.copy(open=false) else it }))
 }
}
