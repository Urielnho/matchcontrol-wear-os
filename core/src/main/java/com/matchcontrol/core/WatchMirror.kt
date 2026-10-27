package com.matchcontrol.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Transport ordering is independent of backend revisions and account switches. */
@Serializable data class MirrorFrame(val authorityId: String,val sequence: Long,val snapshot: Snapshot)

object WatchMirror {
 private val json=Json {encodeDefaults=true;ignoreUnknownKeys=true}

 fun optimistic(snapshot: Snapshot,commands: List<SyncCommand>): Snapshot = commands.fold(snapshot) {s,c->
  if(c.profileId!=s.profile.id || c.source!=s.source) s else {
   fun Set<String>.toggle()=if(c.enabled) this+c.target else this-c.target
   when(c.action) {
    "team"->s.copy(favoriteTeams=s.favoriteTeams.toggle())
    "league"->s.copy(favoriteLeagues=s.favoriteLeagues.toggle())
    "match"->s.copy(favoriteMatches=s.favoriteMatches.toggle())
    else->s
   }
  }
 }

 /** Keep active tickets and favorite names, even outside today's match list. */
 fun compact(snapshot: Snapshot,maxBytes: Int=85_000): Snapshot {
  val bets=snapshot.bets.sortedWith(compareBy<Bet> {it.status!=BetStatus.PENDING}.thenByDescending {it.createdAt}).take(30).sortedBy {it.createdAt}
  val referenced=bets.flatMap {it.picks.map(Pick::matchId)}.toSet()
  val favorites=snapshot.fixtures.filter {it.id in snapshot.favoriteMatches || it.leagueId in snapshot.favoriteLeagues || it.home.id in snapshot.favoriteTeams || it.away.id in snapshot.favoriteTeams}
  val fixtures=(snapshot.fixtures.filter {it.id in referenced}+favorites+snapshot.fixtures.filter {it.status==FixtureStatus.LIVE}.take(8)+snapshot.fixtures.filter {it.status==FixtureStatus.SCHEDULED}.sortedBy {it.startsAt}.take(10)+snapshot.fixtures.sortedByDescending {it.startsAt}.take(6)).distinctBy {it.id}.take(35)
  var result=snapshot.copy(
   fixtures=fixtures.map {it.copy(events=it.events.take(4).map {text->text.take(180)},statistics=it.statistics.entries.take(6).associate {p->p.key.take(80) to p.value.take(100)})},
   bets=bets,outcomes=emptyList(),transactions=emptyList(),processed=snapshot.processed.toList().takeLast(256).toSet(),
   favoriteTeamDetails=(snapshot.favoriteTeamDetails+snapshot.fixtures.flatMap {listOf(it.home,it.away)}).distinctBy {it.id}.filter {it.id in snapshot.favoriteTeams},
   favoriteLeagueDetails=(snapshot.favoriteLeagueDetails+snapshot.fixtures.map {FavoriteLeague(it.leagueId,it.league)}).distinctBy {it.id}.filter {it.id in snapshot.favoriteLeagues},
   totalBetCount=snapshot.bets.size
  )
  while(json.encodeToString(result).toByteArray().size>maxBytes) {
   result=when {
    result.fixtures.isNotEmpty()->result.copy(fixtures=result.fixtures.dropLast(1))
    result.bets.isNotEmpty()->{
     val oldestSettled=result.bets.firstOrNull {it.status!=BetStatus.PENDING} ?: result.bets.first()
     result.copy(bets=result.bets.filterNot {it.id==oldestSettled.id})
    }
    else->error("El resumen de favoritos supera el tamaño permitido")
   }
  }
  return result
 }
}
