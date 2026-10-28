package com.matchcontrol.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchcontrol.domain.Sport
import com.matchcontrol.core.SportKind
import com.matchcontrol.core.sportTitle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.navigation.*
import com.matchcontrol.presentation.theme.*
import com.matchcontrol.presentation.home.*
import com.matchcontrol.presentation.components.*
import kotlinx.coroutines.delay

@Composable fun MatchApp(vm: MatchViewModel, notificationMatch: com.matchcontrol.domain.Match? = null, requestPermission: () -> Unit) {
 val s by vm.state.collectAsStateWithLifecycle()
 val sandbox by vm.mirror.collectAsStateWithLifecycle()
 val link by vm.sync.state.collectAsStateWithLifecycle()
 val sportsVm: SportsViewModel = viewModel()
 val extra by sportsVm.state.collectAsStateWithLifecycle()
 val nav=rememberSwipeDismissableNavController()
 var chosenSport by rememberSaveable {mutableStateOf(SportKind.BASKETBALL)}
 var chosenFixture by rememberSaveable {mutableStateOf<String?>(null)}
 val lifecycle=LocalLifecycleOwner.current
 val config=LocalConfiguration.current
 val density=LocalDensity.current
 val side=(config.screenWidthDp*if(config.isScreenRound) .115f else .08f).dp
 val initialOffset=with(density) { (config.screenHeightDp.dp/2).roundToPx() }
 LaunchedEffect(notificationMatch) { notificationMatch?.let { vm.select(it);nav.navigate("details") } }
 LaunchedEffect(lifecycle,s.settingsReady) {
  if(!s.settingsReady) return@LaunchedEffect
  lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   vm.refresh()
   while(true) { delay(if(vm.state.value.matches.any { it.live }) 15*60_000L else 60*60_000L);vm.refresh() }
  }
 }
 MatchControlTheme {
  AppScaffold(modifier=Modifier.fillMaxSize().clip(if(config.isScreenRound) CircleShape else RectangleShape).background(MatchColors.Background),timeText={TimeText()}) {
   SwipeDismissableNavHost(navController=nav,startDestination="home") {
    listOf("home","live","upcoming","results","favorites","settings","details","sports","sportfeed","sportdetail","sandbox","bet/{id}","sharedsport","shareddetail").forEach { route -> composable(route) { backEntry ->
 val matches=s.matches.filter { m -> s.settings.leagues.isEmpty() || s.competitions.any { it.id.toString() in s.settings.leagues && it.includes(m) } }

     LaunchedEffect(route) { if(route=="home") vm.today() }
     val scroll=rememberTransformingLazyColumnState(initialAnchorItemScrollOffset=initialOffset)
     // The initial anchor stays at the beginning while asynchronous fixtures are inserted.
     val routeLoading=when(route) { "sportfeed"->extra.loading;"sportdetail"->extra.loading || extra.detailLoading;"sports"->false;else->s.loading }
     val dataKey=when(route) { "sportfeed"->"${extra.sport}:${extra.date}";"sportdetail"->extra.detail?.id;else->s.date.toString() }
     var positioned by rememberSaveable(dataKey) { mutableStateOf(false) }
     LaunchedEffect(routeLoading,s.settingsReady) {
      if(s.settingsReady && !routeLoading && !positioned) { scroll.scrollToItem(0,initialOffset);positioned=true }
     }
     ScreenScaffold(scrollState=scroll,
      contentPadding=PaddingValues(start=side,end=side,top=if(config.screenWidthDp<210) 18.dp else 28.dp,bottom=8.dp),
      edgeButton={ EdgeButton(onClick={when(route) { "sports"->nav.popBackStack("home",false);"sportfeed"->sportsVm.refresh(true);"sportdetail"->sportsVm.reloadDetail();else->{ if(route=="details") vm.reloadDetail() else vm.refresh(true) } }},buttonSize=EdgeButtonSize.Small,
       colors=ButtonDefaults.buttonColors(containerColor=MatchColors.Raised,contentColor=MatchColors.Text)) {
       Text(if(route=="sports") "Inicio" else "Actualizar",style=MatchType.Label,maxLines=1)
      } }) { padding ->
      TransformingLazyColumn(state=scroll,modifier=Modifier.fillMaxSize(),contentPadding=padding,verticalArrangement=Arrangement.spacedBy(MatchSpace.Small)) {
       item(key="$route-header") {
        if(route=="home") HomeControls(settings={nav.navigate("settings")},sports={nav.navigate("sports")})
        else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
         IconButton(onClick={nav.popBackStack()},modifier=Modifier.size(MatchSpace.Touch).semantics { contentDescription="Volver" }) { SportGlyph(Glyph.Back,Modifier.size(20.dp)) }
         Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
         Text(when(route) { "sandbox"->"Boletos";"bet/{id}"->"Boleto";"sharedsport","shareddetail"->sportTitle(chosenSport);"live"->"En vivo";"upcoming"->"Próximos";"results"->"Resultados";"favorites"->"Favoritos";"settings"->"Ajustes";"sports"->"Deportes";"sportfeed","sportdetail"->extra.sport.title;else->"Partido" },style=MatchType.Title,color=MatchColors.Text)
         }
        }
       }
       if(vm.repository.hasReplica() && sandbox.source=="DEMO") item(key="demo") { Text("DEMO · datos ficticios",style=MatchType.Micro,color=MatchColors.Accent) }
       if(!s.settingsReady || routeLoading || (route=="details" && s.detailLoading)) item(key="loading") {
        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
         CircularProgressIndicator(modifier=Modifier.size(22.dp),strokeWidth=2.dp)
         Text(if(route=="details") "Cargando detalle" else if(route in listOf("sportfeed","sportdetail")) "Cargando resultados" else "Cargando partidos",style=MatchType.Body,color=MatchColors.Secondary)
        }
       }
       if(!s.online && s.settingsReady) item(key="offline") { StatusNotice(Glyph.Offline,"Sin internet","Puedes consultar los datos guardados.") }
       (if(route in listOf("sportfeed","sportdetail")) extra.error else if(route=="sports") null else s.error)?.let { message -> item(key="error") { StatusNotice(Glyph.Error,"No se pudo completar",message,MatchColors.Warning) } }
       if(if(route in listOf("sportfeed","sportdetail")) extra.stale else route!="sports" && s.stale) item(key="stale") { StatusNotice(Glyph.Refresh,"Pendiente de actualizar","Mostrando la última información guardada.") }
       val currentError=if(route in listOf("sportfeed","sportdetail")) extra.error else s.error
       if(route!="sports" && (!s.online || currentError!=null) && !routeLoading) item(key="retry") {
        SportAction("Reintentar",Glyph.Refresh) {
         when(route) { "sportfeed"->sportsVm.refresh(true);"sportdetail"->sportsVm.reloadDetail();"details"->vm.reloadDetail();else->vm.refresh(true) }
        }
       }
       when(route) {
        "home"->{
         if(vm.repository.hasReplica()) premiumHome(s,matches,{nav.navigate(it)},{vm.select(it);nav.navigate("details")})
         else {item {StatePanel(Glyph.Refresh,"Una sola cuenta","Vincula el móvil. Sus favoritos y boletos aparecerán aquí.")};item {SportAction("Conectar móvil",Glyph.Refresh) {vm.sync.refresh()}}}
         item {SportAction("Créditos y boletos",Glyph.Star) {nav.navigate("sandbox")}}
        }
        "live","upcoming","results"->sportListing(route,s,matches,vm::day) { vm.select(it);nav.navigate("details") }
        "favorites"->sharedFavoritesContent(sandbox,vm::favorite) {if(it.sport==SportKind.FOOTBALL) {vm.selectFixture(it);nav.navigate("details")} else {chosenSport=it.sport;chosenFixture=it.id;nav.navigate("shareddetail")}}
        "details"->{detailContent(s,vm::toggleTeam);s.detail?.let { match->
         item {SportAction("Enviar al móvil",Glyph.Refresh) {vm.sync.command(com.matchcontrol.core.SyncCommand(action="select",target=vm.externalId(match.id)))}}
         item {SportAction(if(vm.externalId(match.id) in sandbox.favoriteMatches) "Quitar partido favorito" else "Guardar partido",Glyph.Star) {vm.sync.command(com.matchcontrol.core.SyncCommand(action="match",target=vm.externalId(match.id),enabled=vm.externalId(match.id) !in sandbox.favoriteMatches))}}
        }}
        "sandbox"->sandboxContent(sandbox,link,vm.repository.hasReplica(),{vm.sync.refresh()}) {nav.navigate("bet/${it.id}")}
        "bet/{id}"->betDetailContent(sandbox.bets.firstOrNull {it.id==backEntry.arguments?.getString("id")},sandbox)
        "sports"->sportsPicker { sport -> if(sport==Sport.FOOTBALL) nav.popBackStack("home",false) else {chosenSport=SportKind.valueOf(sport.name);vm.sync.command(com.matchcontrol.core.SyncCommand(action="sport",target=chosenSport.name));nav.navigate("sharedsport")} }
        "sharedsport"->sharedSportContent(sandbox,chosenSport) {chosenFixture=it.id;nav.navigate("shareddetail")}
        "shareddetail"->sharedFixtureContent(sandbox.fixtures.firstOrNull {it.id==chosenFixture},vm::favorite,sandbox) {vm.sync.command(com.matchcontrol.core.SyncCommand(action="select",target=it.id))}
        "sportfeed"->sportsFeed(extra,sportsVm::day,{sportsVm.refresh(true)}) { sportsVm.select(it);nav.navigate("sportdetail") }
        "sportdetail"->sportsDetail(extra)
        "settings"->settingsContent(s,vm,requestPermission)
       }
       if(route=="home") item { SportAction("Actualizar resultados",Glyph.Refresh,onClick={vm.refresh(true)}) }
       if(route !in listOf("home","sports","sportfeed","sportdetail","settings")) item {
        Text(updatedLabel(s.updated),style=MatchType.Caption,color=MatchColors.Secondary)
       }
      }
     }
    } }
   }
  }
 }
}
