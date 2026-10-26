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
 private val mutable=MutableStateFlow(UiState())
 val state=mutable.asStateFlow()
 init { viewModelScope.launch {repository.state.collect {render(it)}};sync.refresh() }
 private fun legacyId(value: String)=value.substringAfterLast(':').toIntOrNull() ?: (value.hashCode() and Int.MAX_VALUE)
 fun externalId(id: Int)=repository.state.value.fixtures.firstOrNull {legacyId(it.id)==id}?.id ?: id.toString()
 private fun convert(f: Fixture)=Match(legacyId(f.id),Team(legacyId(f.home.id),f.home.name,f.home.logo),Team(legacyId(f.away.id),f.away.name,f.away.logo),f.league,Instant.ofEpochMilli(f.startsAt).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(),when(f.status) {FixtureStatus.SCHEDULED->"NS";FixtureStatus.LIVE->"LIVE";FixtureStatus.FINAL->"FT";FixtureStatus.VOID->"CANC";FixtureStatus.SUSPENDED->"SUSP"},homeGoals=f.homeScore,awayGoals=f.awayScore,events=f.events,statistics=f.statistics.map {"${it.key}: ${it.value}"},leagueId=legacyId(f.leagueId))
 private fun render(s: Snapshot) {
  var teams=s.favoriteTeams;var leagues=s.favoriteLeagues
  repository.pending().forEach {c->if(c.action=="team") teams=if(c.enabled) teams+c.target else teams-c.target;if(c.action=="league") leagues=if(c.enabled) leagues+c.target else leagues-c.target}
  val matches=s.fixtures.map(::convert).filter {it.date==mutable.value.date}
  mutable.update {old->old.copy(settings=Settings(s.notifications,teams.map {legacyId(it).toString()}.toSet(),leagues.map {legacyId(it).toString()}.toSet()),matches=matches,detail=old.detail?.let { d->s.fixtures.firstOrNull {legacyId(it.id)==d.id}?.let(::convert)},updated=s.dataUpdatedAt.takeIf {it>0} ?: s.updatedAt,online=true,settingsReady=true,knownTeams=s.fixtures.flatMap {listOf(Team(legacyId(it.home.id),it.home.name),Team(legacyId(it.away.id),it.away.name))}.distinctBy {it.id},competitions=s.fixtures.distinctBy {it.leagueId}.map {Competition(legacyId(it.leagueId),it.league,it.league,s.source)}) }
 }
 fun refresh(force: Boolean=false) {render(repository.state.value);sync.refresh()}
 fun today() {mutable.update {it.copy(date=LocalDate.now())};render(repository.state.value)}
 fun day(offset: Long) {mutable.update {it.copy(date=it.date.plusDays(offset),detail=null)};render(repository.state.value)}
 fun select(match: Match) {mutable.update {it.copy(detail=match)}}
 fun reloadDetail() {refresh()}
 fun toggleTeam(team: Team) {val raw=repository.state.value.fixtures.flatMap {listOf(it.home,it.away)}.firstOrNull {legacyId(it.id)==team.id}?.id ?: return;sync.command(SyncCommand(action="team",target=raw,enabled=team.id.toString() !in mutable.value.settings.teams));render(repository.state.value)}
 fun toggleLeague(c: Competition) {val raw=repository.state.value.fixtures.firstOrNull {legacyId(it.leagueId)==c.id}?.leagueId ?: return;sync.command(SyncCommand(action="league",target=raw,enabled=c.id.toString() !in mutable.value.settings.leagues));render(repository.state.value)}
 fun setNotifications(value: Boolean) { if(repository.authority) repository.update {it.copy(notifications=value)} }
}
