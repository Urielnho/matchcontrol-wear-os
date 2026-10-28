package com.matchcontrol.core

/** Accept provider records only; old practice snapshots never become real by relabelling them. */
object RealData {
 const val Source="API_FOOTBALL"
 private val eventId=Regex("(?:api|basket|base):[0-9]+|f1:[0-9]{4}:[0-9]+")
 fun isEvent(id: String)=eventId.matches(id)
 fun only(snapshot: Snapshot): Snapshot {
  if(snapshot.source!=Source) return guest()
  val fixtures=snapshot.fixtures.filter {it.source==Source && isEvent(it.id)}
  val ids=fixtures.map {it.id}.toSet()
  val teams=fixtures.flatMap {listOf(it.home,it.away)+it.participants}.map {it.id}.toSet()
  val leagues=fixtures.map {it.leagueId}.toSet()
  val bets=snapshot.bets.filter {it.source==Source && it.picks.isNotEmpty() && it.picks.all {p->isEvent(p.matchId)}}
  val rejected=snapshot.bets.map {it.id}.toSet()-bets.map {it.id}.toSet()
  val clean=snapshot.copy(fixtures=fixtures,outcomes=snapshot.outcomes.filter {it.matchId in ids},bets=bets,
   transactions=snapshot.transactions.filter {it.reference !in rejected && it.kind!="RESET"},
   favoriteTeams=snapshot.favoriteTeams.intersect(teams),favoriteLeagues=snapshot.favoriteLeagues.intersect(leagues),favoriteMatches=snapshot.favoriteMatches.intersect(ids),
   favoriteTeamDetails=snapshot.favoriteTeamDetails.filter {it.id in teams},favoriteLeagueDetails=snapshot.favoriteLeagueDetails.filter {it.id in leagues},
   selectedMatch=snapshot.selectedMatch?.takeIf {it in ids},totalBetCount=bets.size)
  return if(clean.profile.id=="local-demo") clean.copy(profile=Profile("",""),balance=0,bets=emptyList(),transactions=emptyList(),accountSignedIn=false,totalBetCount=0) else clean
 }
 fun guest(snapshot: Snapshot?=null): Snapshot {
  val clean=if(snapshot?.source==Source) only(snapshot) else Snapshot(source=Source)
  return clean.copy(profile=Profile("",""),balance=0,bets=emptyList(),transactions=emptyList(),accountSignedIn=false,totalBetCount=0,source=Source)
 }
 fun mergeGuest(saved: Snapshot,incoming: Snapshot): Snapshot {
  val old=guest(saved);val fresh=guest(incoming)
  val ids=fresh.fixtures.map {it.id}.toSet()
  val fixtures=(fresh.fixtures+old.fixtures.filterNot {it.id in ids}).take(800)
  val next=old.copy(fixtures=fixtures,outcomes=old.outcomes.filterNot {it.matchId in ids}+fresh.outcomes,
   dataUpdatedAt=maxOf(old.dataUpdatedAt,fresh.dataUpdatedAt),stale=fresh.stale,updatedAt=System.currentTimeMillis())
  return guest(next)
 }
}
