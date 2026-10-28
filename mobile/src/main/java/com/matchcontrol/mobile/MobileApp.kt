package com.matchcontrol.mobile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.*
import com.matchcontrol.core.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.math.BigDecimal

internal fun time(ms: Long,pattern: String="dd MMM · HH:mm")=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
internal fun odds(value: Int)=BigDecimal.valueOf(value.toLong(),4).stripTrailingZeros().toPlainString()
private fun marketName(m: MarketType)=marketTitle(m)
internal fun status(s: BetStatus)=when(s) { BetStatus.PENDING->"Pendiente";BetStatus.WON->"Ganada";BetStatus.LOST->"Perdida";BetStatus.VOID->"Anulada" }
private data class Destination(val route: String,val name: String,val icon: ImageVector)
private val destinations=listOf(Destination("home","Inicio",Icons.Outlined.Home),Destination("matches","Partidos",Icons.Outlined.SportsSoccer),Destination("bets","Apuestas",Icons.Outlined.ConfirmationNumber),Destination("favorites","Favoritos",Icons.Outlined.StarBorder),Destination("settings","Perfil",Icons.Outlined.PersonOutline))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MobileApp(vm: MobileViewModel) {
 var chosenSport by rememberSaveable {mutableStateOf<SportKind?>(null)}
 val received by vm.state.collectAsStateWithLifecycle();val slip by vm.slip.collectAsStateWithLifecycle();val sync by vm.sync.state.collectAsStateWithLifecycle()
 // Legacy caches cannot reintroduce generated events or tickets into the release UI.
 val realFixtures=received.fixtures.filter {it.source=="API_FOOTBALL"}
 val realIds=realFixtures.map {it.id}.toSet()
 val state=received.copy(fixtures=realFixtures,outcomes=received.outcomes.filter {it.matchId in realIds},bets=received.bets.filter {it.source=="API_FOOTBALL"})
 val signed by vm.signedIn.collectAsStateWithLifecycle();val loading by vm.loading.collectAsStateWithLifecycle()
 val online by vm.online.collectAsStateWithLifecycle()
 val now by produceState(System.currentTimeMillis()) {while(true) {delay(30_000);value=System.currentTimeMillis()}}
 val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route ?: "home"
 val snack=remember { SnackbarHostState() };val message by vm.message.collectAsStateWithLifecycle()
 LaunchedEffect(message) { message?.let { snack.showSnackbar(it);vm.message.value=null } }
 val lifecycle=LocalLifecycleOwner.current
 LaunchedEffect(lifecycle) {lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {var tick=0;while(true) {vm.sync.refresh();delay(15_000);if(++tick%4==0 && vm.online.value) vm.poll();if(tick%60==0 && vm.online.value) vm.refresh()}} }
 LaunchedEffect(state.selectedMatch) { state.selectedMatch?.let { id->nav.navigate("match/$id");vm.act { vm.account.consumeSelection() } } }
 val wide=LocalConfiguration.current.screenWidthDp>=700
 MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Color.Black,primaryContainer=Raised,onPrimaryContainer=Lime,background=Background,surface=Surface,surfaceVariant=Raised,onSurface=Color(0xFFF7F9FA),onSurfaceVariant=Muted,secondary=Lime,secondaryContainer=Raised,onSecondaryContainer=Lime,outline=Color(0xFF343D44))) {
  Scaffold(snackbarHost={SnackbarHost(snack)},topBar={
   Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal=24.dp,vertical=10.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      if(route=="home") Icon(Icons.Outlined.SportsSoccer,null,tint=Lime,modifier=Modifier.size(22.dp))
      Text(if(route.startsWith("match/")) "Evento" else when(route) { "home"->"MatchControl";"matches"->"Partidos";"slip"->"Boleto";"bets"->"Mis apuestas";"wallet"->"Créditos";"favorites"->"Favoritos";"settings"->"Perfil";else->"MatchControl" },fontSize=22.sp,fontWeight=FontWeight.Bold)
     }
     Row(verticalAlignment=Alignment.CenterVertically) {
      IconButton(onClick={nav.navigate("slip")}) {BadgedBox(badge={if(slip.isNotEmpty()) Badge {Text("${slip.size}")}}) {Icon(Icons.Outlined.ConfirmationNumber,"Abrir boleto")}}
     }
    }
    if(route in listOf("slip","bets","wallet")) Text("Créditos virtuales · sin dinero real",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))
    if(loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth().padding(top=8.dp),color=Lime,trackColor=Raised)
    if(!online) Row(Modifier.padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.WifiOff,null,modifier=Modifier.size(14.dp),tint=Muted);Text("Sin conexión · datos guardados",color=Muted,fontSize=11.sp)}
    else if(state.stale) Text("Resultados guardados · actualización pendiente",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
   }
  },bottomBar={ if(!wide) NavigationBar(containerColor=Surface) { destinations.forEach { d-> NavigationBarItem(selected=route==d.route,onClick={nav.navigate(d.route) { popUpTo("home");launchSingleTop=true }},icon={BadgedBox(badge={if(d.route=="slip" && slip.isNotEmpty()) Badge {Text("${slip.size}")} }) {Icon(d.icon,d.name)}},label={Text(d.name,fontSize=11.sp)}) } } }) { padding->
   Row(Modifier.fillMaxSize().padding(padding)) {
    if(wide) NavigationRail { destinations.forEach { d->NavigationRailItem(selected=route==d.route,onClick={nav.navigate(d.route)},icon={Icon(d.icon,d.name)},label={Text(d.name)}) } }
    Box(Modifier.weight(1f),contentAlignment=Alignment.TopCenter) {
     NavHost(nav,startDestination="home",modifier=Modifier.widthIn(max=800.dp).fillMaxSize()) {
      composable("home") {
       val upcoming=state.fixtures.filter {it.status==FixtureStatus.SCHEDULED && it.startsAt>now && (chosenSport==null || it.sport==chosenSport)}.sortedBy {it.startsAt}.take(6)
       val latestResults=state.fixtures.filter {it.status==FixtureStatus.FINAL && (chosenSport==null || it.sport==chosenSport)}.sortedByDescending {it.startsAt}.take(3)
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SportPicker(chosenSport) {chosenSport=it;vm.date(LocalDate.now(),it ?: SportKind.FOOTBALL)}}
        item { Card(onClick={nav.navigate(if(signed) "wallet" else "settings")},shape=shape,colors=CardDefaults.cardColors(containerColor=Surface)) {
         Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {Column {Text("Créditos",color=Muted,fontSize=12.sp);Text(if(signed) credits(state.balance) else "Inicia sesión",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold)};Text(if(signed) "créditos  ›" else "›",color=Lime,fontSize=12.sp) }
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick={nav.navigate("bets")},modifier=Modifier.weight(1f)) {Text("${state.bets.count { it.status==BetStatus.PENDING }} pendientes")};OutlinedButton(onClick={nav.navigate("favorites")},modifier=Modifier.weight(1f)) {Text("Favoritos") } } }
        if(state.fixtures.any {it.status==FixtureStatus.LIVE && (chosenSport==null || it.sport==chosenSport)}) item { Heading("En vivo") }
        items(state.fixtures.filter { it.status==FixtureStatus.LIVE && (chosenSport==null || it.sport==chosenSport)}.take(1)) { FixtureCard(it,hero=true) {nav.navigate("match/${it.id}")} }
        item { Heading("Próximos encuentros") }
        items(upcoming) { FixtureCard(it) {nav.navigate("match/${it.id}")} }
        if(upcoming.isEmpty() && !loading) item {
         Empty("Sin próximos eventos","Consulta otra fecha en Partidos o actualiza los resultados.")
        }
        if(latestResults.isNotEmpty()) item {Heading("Últimos resultados")}
        items(latestResults) {FixtureCard(it) {nav.navigate("match/${it.id}")}}
        item {TextButton(onClick={vm.refresh()}) {Icon(Icons.Outlined.Refresh,null,modifier=Modifier.size(16.dp));Text("Actualizar",Modifier.padding(start=6.dp))}}
        if(state.dataUpdatedAt>0) item {Text("Actualizado ${time(state.dataUpdatedAt)}",color=Muted,fontSize=11.sp)}
       }
      }
      composable("matches") {
       var day by rememberSaveable { mutableIntStateOf(0) };var kind by rememberSaveable {mutableStateOf("Todos")};var league by rememberSaveable { mutableStateOf<String?>(null) }
       val date=LocalDate.now().plusDays(day.toLong())
       LaunchedEffect(date,signed,chosenSport) {league=null;vm.date(date,chosenSport ?: SportKind.FOOTBALL)}
       val fixtures=state.fixtures.filter { (chosenSport==null || it.sport==chosenSport) && (chosenSport==SportKind.FORMULA1 || Instant.ofEpochMilli(it.startsAt).atZone(ZoneId.systemDefault()).toLocalDate()==date) && (league==null || it.leagueId==league) && when(kind) { "En vivo"->it.status==FixtureStatus.LIVE;"Próximos"->it.status==FixtureStatus.SCHEDULED && it.startsAt>now;"Finales"->it.status==FixtureStatus.FINAL;else->true } }
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {SportPicker(chosenSport) {chosenSport=it}}
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={day--}) {Icon(Icons.Outlined.ChevronLeft,"Día anterior")};Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));IconButton(onClick={day++}) {Icon(Icons.Outlined.ChevronRight,"Día siguiente")} } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {listOf("Todos","En vivo","Próximos","Finales").forEach { label->FilterChip(selected=kind==label,onClick={kind=label},label={Text(label,fontSize=11.sp)}) } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(selected=league==null,onClick={league=null},label={Text("Todas")});state.fixtures.filter {chosenSport==null || it.sport==chosenSport}.distinctBy { it.leagueId }.forEach { f->FilterChip(selected=league==f.leagueId,onClick={league=f.leagueId},label={Text(f.league,fontSize=11.sp)}) }} }
        if(fixtures.isEmpty()) item { Empty("No hay encuentros","Cambia la fecha o los filtros.") }
        items(fixtures) { FixtureCard(it) {nav.navigate("match/${it.id}")} }
       }
      }
      composable("match/{id}") { back->
       val fixture=state.fixtures.firstOrNull { it.id==back.arguments?.getString("id") }
       LaunchedEffect(fixture?.id) {fixture?.let {vm.details(it.id)}}
       if(fixture!=null) LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick={nav.popBackStack()}) {Text("‹ Volver")} }
        item { FixtureCard(fixture,hero=true) {} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
         OutlinedButton(onClick={vm.favorite("match",fixture.id,fixture.id !in state.favoriteMatches)},modifier=Modifier.weight(1f)) {Text(if(fixture.id in state.favoriteMatches) "Guardado ★" else "Guardar partido")}
         OutlinedButton(onClick={nav.navigate("slip")},modifier=Modifier.weight(1f)) {Text("Boleto (${slip.size})")}
        } }
        item { Heading(if(fixture.sport==SportKind.FORMULA1) "Pilotos" else "Tus equipos") }
        items(if(fixture.sport==SportKind.FORMULA1) fixture.participants else listOf(fixture.home,fixture.away)) { team->ListItem(headlineContent={Text(team.name)},trailingContent={IconButton(onClick={vm.favorite("team",team.id,team.id !in state.favoriteTeams)}) {Icon(if(team.id in state.favoriteTeams) Icons.Outlined.Star else Icons.Outlined.StarBorder,"Seguir ${team.name}")}},colors=ListItemDefaults.colors(containerColor=Surface)) }
        if(fixture.raceResults.isNotEmpty()) item {Heading("Clasificación");fixture.raceResults.forEach {r->Text((r.position?.toString() ?: "NC")+" · "+(fixture.participants.firstOrNull {it.id==r.driverId}?.name ?: r.driverId),Modifier.padding(vertical=5.dp),color=Muted)}}
        if(fixture.events.isNotEmpty()) item { Heading("Lo que pasó");fixture.events.forEach {Text(it,Modifier.padding(vertical=6.dp),color=Muted)} }
        if(fixture.statistics.isNotEmpty()) item { Heading("Estadísticas");Statistics(fixture.statistics) }
        item {Heading("Mercados","Créditos virtuales")}
        item {OutlinedButton(onClick={vm.markets(fixture.id)}) {Text("Consultar cuotas")}}
        if(state.outcomes.none {it.matchId==fixture.id}) item {Empty("Sin cuotas disponibles","El proveedor no ofrece cuotas para este evento. No es posible agregarlo al boleto.")}
        if(fixture.status!=FixtureStatus.SCHEDULED || fixture.startsAt<=now) item {Empty("Mercados cerrados","No se aceptan selecciones una vez iniciado el evento.")} else {
         MarketType.entries.filter {type->state.outcomes.any {it.matchId==fixture.id && it.market==type}}.forEach { type->item {
          Text(marketName(type),fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=10.dp))
          state.outcomes.filter {it.matchId==fixture.id && it.market==type}.chunked(if(fixture.sport==SportKind.FORMULA1) 2 else 3).forEach {group->Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {group.forEach { o->
           val picked=slip.any { it.id==o.id }
           Card(onClick={vm.pick(o)},enabled=o.open && !loading,modifier=Modifier.weight(1f).semantics {contentDescription=if(o.open) "Elegir ${o.label}, cuota ${odds(o.odds)}, ${marketName(type)}" else "Mercado cerrado: ${o.label}"},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(picked) Lime else Surface,contentColor=if(picked) Color.Black else Color.White)) {
            Column(Modifier.fillMaxWidth().padding(vertical=14.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {Text(o.label,fontSize=12.sp);Text(if(o.open) odds(o.odds) else "Cerrado",fontSize=20.sp,fontWeight=FontWeight.Bold)}
           }
          } } }
         } }
         item { Text(sportRules(fixture.sport),fontSize=12.sp,color=Muted) }
        }
       }
      }
      composable("slip") {
       var stake by rememberSaveable {mutableStateOf("100")};var confirm by remember {mutableStateOf(false)}
       val available=slip.all {o->state.fixtures.any {it.id==o.matchId && it.status==FixtureStatus.SCHEDULED && it.startsAt>now} && state.outcomes.any {it.id==o.id && it.open && it.odds==o.odds}}
       LaunchedEffect(available,state.profile.id,signed) {confirm=false}
       val potential=runCatching {BettingRules.returns(parseCredits(stake),slip.map { it.odds })}.getOrNull()
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Heading(if(slip.size>1) "Parlay · ${slip.size} partidos" else "Apuesta simple","Una selección por partido · hasta 10 partidos")}
        if(slip.isEmpty()) {item {Empty("Tu boleto está vacío","Abre un próximo partido y elige un mercado.")};item {Button(onClick={nav.navigate("matches")}) {Text("Explorar partidos")}}}
        items(slip,key={it.id}) {o->val f=state.fixtures.firstOrNull { it.id==o.matchId };Card(shape=shape,colors=CardDefaults.cardColors(containerColor=Surface)) {Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text(f?.let(::eventLabel) ?: "Evento no disponible",color=Muted);Text(o.label,fontWeight=FontWeight.Bold);Text("${marketName(o.market)} · ${odds(o.odds)}",fontSize=12.sp,color=Lime)};IconButton(onClick={vm.pick(o)}) {Icon(Icons.Outlined.Close,"Quitar selección")}}} }
        if(slip.isNotEmpty()) {
         item {OutlinedButton(onClick={nav.navigate("matches") {launchSingleTop=true}},modifier=Modifier.fillMaxWidth()) {Text(if(slip.size==1) "Agregar otro partido · crear parlay" else "Agregar otro partido")}}
         item {Text("Cuota total "+runCatching {totalOddsLabel(BettingRules.totalOdds(slip.map {it.odds}).toPlainString())}.getOrDefault("—"),fontWeight=FontWeight.SemiBold)}
         item {OutlinedTextField(value=stake,onValueChange={stake=it},label={Text("Créditos virtuales")},supportingText={Text("Disponibles: ${credits(state.balance)} · mínimo 1")},singleLine=true,modifier=Modifier.fillMaxWidth())}
         item {Card(shape=shape,colors=CardDefaults.cardColors(containerColor=Surface)) {Column(Modifier.fillMaxWidth().padding(22.dp)) {Text("RETORNO POTENCIAL",color=Muted,fontSize=12.sp);Text(potential?.let(::credits) ?: "Importe inválido",fontSize=32.sp,color=Lime,fontWeight=FontWeight.Bold);Text("Incluye los créditos apostados",fontSize=12.sp,color=Muted)}}}
         if(!available) item {Empty("Revisa tus selecciones","Un evento ya comenzó, se cerró el mercado o cambió su cuota. Quita esa selección y consulta los mercados de nuevo.")}
         if(!signed) item {Text("Inicia sesión para confirmar tu boleto",color=Muted);TextButton(onClick={nav.navigate("settings")}) {Text("Iniciar sesión")}}
         item {Button(onClick={confirm=true},enabled=signed && !loading && available && potential!=null && runCatching {parseCredits(stake) in 100..state.balance}.getOrDefault(false),modifier=Modifier.fillMaxWidth().height(54.dp)) {Text("Confirmar boleto")}}
        }
       }
       if(confirm && signed) AlertDialog(onDismissRequest={confirm=false},title={Text("Confirmar apuesta")},text={Text("Se descontarán $stake créditos virtuales. Las cuotas quedarán bloqueadas. No hay dinero real.")},confirmButton={TextButton(onClick={confirm=false;vm.confirm(stake) {nav.navigate("bets")}}) {Text("Confirmar")}},dismissButton={TextButton(onClick={confirm=false}) {Text("Cancelar")}})
      }
      composable("bets") {
       var active by rememberSaveable {mutableStateOf(true)}
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(selected=active,onClick={active=true},label={Text("Activas")});FilterChip(selected=!active,onClick={active=false},label={Text("Finalizadas")})}}
        val bets=state.bets.asReversed().filter { (it.status==BetStatus.PENDING)==active }
        if(bets.isEmpty()) item {Empty("Sin boletos",if(signed) "Tus boletos aparecerán aquí." else "Inicia sesión para consultar tus boletos.")}
        if(!signed) item {TextButton(onClick={nav.navigate("settings")}) {Text("Iniciar sesión")}}
        items(bets,key={it.id}) {bet->BetCard(bet)}
       }
      }
      composable("wallet") {
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(!signed) item {Empty("Inicia sesión","Accede a tu cuenta para consultar tus créditos y movimientos.");TextButton(onClick={nav.navigate("settings")}) {Text("Iniciar sesión")}}
        if(signed) item {Heading("${credits(state.balance)} créditos")}
        if(signed) item {Text("Utilizados: ${credits(state.bets.sumOf { it.stake })}",color=Muted);Text("Retornos: ${credits(state.bets.sumOf { it.returned })}",color=Lime);Text("Resultado neto: ${credits(state.bets.filter { it.status==BetStatus.WON || it.status==BetStatus.LOST }.sumOf { it.returned-it.stake })}",color=Muted)}
        if(signed) item {Heading("Movimientos")}
        items(if(signed) state.transactions.asReversed() else emptyList(),key={it.id}) {t->Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {Column {Text(when(t.kind) {"INITIAL"->"Créditos iniciales";"STAKE"->"Boleto confirmado";"RESET"->"Reinicio confirmado";"REFUND"->"Devolución";else->"Liquidación"});Text(time(t.createdAt),fontSize=12.sp,color=Muted)};Text((if(t.amount>0) "+" else "")+credits(t.amount),color=if(t.amount>0) Lime else Color.White)}}
       }
      }
      composable("favorites") {
       var choosing by rememberSaveable {mutableStateOf(false)}
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick={choosing=!choosing}) {Text(if(choosing) "Ver favoritos" else "Elegir equipos y ligas")}}
        if(!choosing && state.favoriteTeams.isEmpty() && state.favoriteLeagues.isEmpty() && state.favoriteMatches.isEmpty()) item {Empty("Sin favoritos","Sigue un equipo o guarda un partido.")}
        if(choosing || state.favoriteTeams.isNotEmpty()) item {Heading("Equipos")}
        items(state.fixtures.flatMap {if(it.sport==SportKind.FORMULA1) it.participants else listOf(it.home,it.away)}.distinctBy { it.id }.filter {choosing || it.id in state.favoriteTeams}) {t->ListItem(headlineContent={Text(t.name)},trailingContent={Switch(checked=t.id in state.favoriteTeams,onCheckedChange={vm.favorite("team",t.id,it)})},colors=ListItemDefaults.colors(containerColor=Surface))}
        if(choosing || state.favoriteLeagues.isNotEmpty()) item {Heading("Ligas")}
        items(state.fixtures.distinctBy { it.leagueId }.filter {choosing || it.leagueId in state.favoriteLeagues}) {f->ListItem(headlineContent={Text(f.league)},trailingContent={Switch(checked=f.leagueId in state.favoriteLeagues,onCheckedChange={vm.favorite("league",f.leagueId,it)})},colors=ListItemDefaults.colors(containerColor=Surface))}
        if(state.favoriteMatches.isNotEmpty()) item {Heading("Partidos guardados")}
        items(state.fixtures.filter {it.id in state.favoriteMatches}) {FixtureCard(it) {nav.navigate("match/${it.id}")}}
       }
      }
      composable("more") {
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        listOf("wallet" to "Billetera virtual","favorites" to "Favoritos","settings" to "Configuración").forEach { (path,title)->item {Card(onClick={nav.navigate(path)},shape=shape,colors=CardDefaults.cardColors(containerColor=Surface)) {Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text(title,fontSize=20.sp);Text("↗",color=Lime)}}} }
       }
      }
      composable("settings") {
       val context=LocalContext.current
       val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->vm.notifications(granted)}
       LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {AuthPanel(vm,signed,state.profile.displayName,loading)}
        if(signed) item {Card(onClick={nav.navigate("wallet")},shape=shape,colors=CardDefaults.cardColors(containerColor=Surface)) {Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text("Créditos virtuales");Text("${credits(state.balance)}  ›",color=Lime)}}}
        item {ListItem(headlineContent={Text("Notificaciones")},trailingContent={Switch(checked=state.notifications,onCheckedChange={on->if(on && android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notifications(on)})},colors=ListItemDefaults.colors(containerColor=Surface))}
        item {Heading("Smartwatch");Text("El reloj usa esta misma cuenta, favoritos y boletos.",color=Muted,fontSize=12.sp);Text(if(sync.connected) "Conectado" else "Desconectado",color=if(sync.connected) Lime else Muted);Text(if(sync.lastSync>0) "Actualizado ${time(sync.lastSync)}" else "Sin sincronización confirmada",color=Muted,fontSize=12.sp);if(sync.pending>0) Text("${sync.pending} cambios pendientes",color=Muted);TextButton(onClick={vm.sync.refresh()}) {Text("Sincronizar")}}
        item {Text("MatchControl 2.1",color=Muted,fontSize=12.sp)}
       }
      }
     }
    }
   }
  }
 }
}
