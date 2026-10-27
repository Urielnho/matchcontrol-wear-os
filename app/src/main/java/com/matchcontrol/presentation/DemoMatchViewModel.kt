package com.matchcontrol.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matchcontrol.core.*
import com.matchcontrol.core.sync.AppGraph
import com.matchcontrol.data.Settings
import com.matchcontrol.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import java.time.*

class MatchViewModel(app: Application): AndroidViewModel(app) {
 val repository=AppGraph.repository(app)
 val sync=AppGraph.sync(app)
 val mirror=combine(repository.state,repository.pendingCommands) {s,commands->
  if(repository.hasReplica()) WatchMirror.optimistic(s,commands) else Snapshot(profile=Profile(id="",displayName=""),balance=0)
 }.stateIn(viewModelScope,SharingStarted.Eagerly,Snapshot(profile=Profile(id="",displayName=""),balance=0))
 private val mutable=MutableStateFlow(UiState())
 val state=mutable.asStateFlow()
 init { viewModelScope.launch {mirror.collect {render(it)}};sync.refresh() }
 private fun legacyId(value: String)=value.substringAfterLast(':').toIntOrNull() ?: (value.hashCode() and Int.MAX_VALUE)
 fun externalId(id: Int)=repository.state.value.fixtures.firstOrNull {legacyId(it.id)==id}?.id ?: id.toString()
 private fun convert(f: Fixture)=Match(legacyId(f.id),Team(legacyId(f.home.id),f.home.name,f.home.logo),Team(legacyId(f.away.id),f.away.name,f.away.logo),f.league,Instant.ofEpochMilli(f.startsAt).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(),when(f.status) {FixtureStatus.SCHEDULED->"NS";FixtureStatus.LIVE->"LIVE";FixtureStatus.FINAL->"FT";FixtureStatus.VOID->"CANC";FixtureStatus.SUSPENDED->"SUSP"},homeGoals=f.homeScore,awayGoals=f.awayScore,events=f.events,statistics=f.statistics.map {"${it.key}: ${it.value}"},leagueId=legacyId(f.leagueId))
 private fun render(s: Snapshot) {
  val teams=s.favoriteTeams;val leagues=s.favoriteLeagues
  val matches=s.fixtures.map(::convert).filter {it.date==mutable.value.date}
  mutable.update {old->old.copy(settings=Settings(s.notifications,teams.map {legacyId(it).toString()}.toSet(),leagues.map {legacyId(it).toString()}.toSet()),matches=matches,detail=old.detail?.let { d->s.fixtures.firstOrNull {legacyId(it.id)==d.id}?.let(::convert)},updated=s.dataUpdatedAt.takeIf {it>0} ?: s.updatedAt,online=true,settingsReady=true,knownTeams=s.fixtures.flatMap {listOf(Team(legacyId(it.home.id),it.home.name),Team(legacyId(it.away.id),it.away.name))}.distinctBy {it.id},competitions=s.fixtures.distinctBy {it.leagueId}.map {Competition(legacyId(it.leagueId),it.league,it.league,s.source)}) }
 }
 fun refresh(force: Boolean=false) {render(mirror.value);sync.refresh()}
 fun today() {mutable.update {it.copy(date=LocalDate.now())};render(mirror.value)}
 fun day(offset: Long) {mutable.update {it.copy(date=it.date.plusDays(offset),detail=null)};render(mirror.value)}
 fun select(match: Match) {mutable.update {it.copy(detail=match)}}
 fun selectFixture(fixture: Fixture) {mutable.update {it.copy(detail=convert(fixture))}}
 fun favorite(action: String,id: String,enabled: Boolean) {sync.command(SyncCommand(action=action,target=id,enabled=enabled))}
 fun reloadDetail() {refresh()}
 fun toggleTeam(team: Team) {val raw=(mirror.value.favoriteTeamDetails+mirror.value.fixtures.flatMap {listOf(it.home,it.away)}).firstOrNull {legacyId(it.id)==team.id}?.id ?: return;favorite("team",raw,raw !in mirror.value.favoriteTeams)}
 fun toggleLeague(c: Competition) {val raw=(mirror.value.favoriteLeagueDetails+mirror.value.fixtures.map {FavoriteLeague(it.leagueId,it.league)}).firstOrNull {legacyId(it.id)==c.id}?.id ?: return;favorite("league",raw,raw !in mirror.value.favoriteLeagues)}
 fun setNotifications(value: Boolean) { if(repository.authority) repository.update {it.copy(notifications=value)} }
}
