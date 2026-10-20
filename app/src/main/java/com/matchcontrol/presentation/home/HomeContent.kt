package com.matchcontrol.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.*
import com.matchcontrol.domain.Match
import com.matchcontrol.presentation.*
import com.matchcontrol.presentation.components.*
import com.matchcontrol.presentation.theme.*

@Composable fun HomeControls(settings: () -> Unit, sports: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=if(LocalConfiguration.current.isScreenRound && LocalConfiguration.current.screenWidthDp<210) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.weight(1f).heightIn(min=MatchSpace.Touch).clickable(onClick=sports).semantics { contentDescription="Elegir deporte" },
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            SportGlyph(Glyph.Ball,Modifier.size(16.dp),MatchColors.Secondary)
            Text("Fútbol", style=MatchType.Label, color=MatchColors.Text)
            Text("⌄",style=MatchType.Label,color=MatchColors.Secondary)
        }
        IconButton(onClick = settings, modifier = Modifier.size(MatchSpace.Touch)
            .semantics { contentDescription = "Configuración" }) { SportGlyph(Glyph.Settings, Modifier.size(16.dp)) }
    }
}
private data class Shortcut(val route: String, val title: String, val glyph: Glyph)
private val shortcuts = listOf(Shortcut("live", "En vivo", Glyph.Live), Shortcut("upcoming", "Próximos", Glyph.Calendar),
    Shortcut("results", "Resultados", Glyph.Results), Shortcut("favorites", "Favoritos", Glyph.Star))

fun TransformingLazyColumnScope.premiumHome(
    state: UiState, matches: List<Match>, navigate: (String) -> Unit, select: (Match) -> Unit,
) {
    val match = featuredMatch(matches, state.settings.teams)
    if (match != null) item {
        MatchTile(match, isFavorite(match, state.settings.teams), hero = true) { select(match) }
    } else if (!state.loading && state.settingsReady && state.error == null) item {
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SportGlyph(Glyph.Ball, Modifier.size(30.dp), MatchColors.Secondary)
            Text("Sin partidos", style = MatchType.Title, color = MatchColors.Text, textAlign = TextAlign.Center)
            Text("Consulta otra fecha.", style = MatchType.Caption,
                color = MatchColors.Secondary, textAlign = TextAlign.Center)
        }
    }
    shortcuts.chunked(2).forEach { row -> item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { shortcut ->
                        Column(Modifier.weight(1f).clickable { navigate(shortcut.route) }.semantics { contentDescription=shortcut.title }, horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { navigate(shortcut.route) },
                                modifier = Modifier.size(MatchSpace.Touch).semantics { contentDescription = shortcut.title },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MatchColors.Surface)) {
                                SportGlyph(shortcut.glyph, Modifier.size(21.dp),
                                    if (shortcut.route == "live" && matches.any { it.live }) MatchColors.Live else MatchColors.Text)
                            }
                            Text(shortcut.title, style = MatchType.Caption, color = MatchColors.Text)
                        }
                    }
                }
    } }
    item {
        Text(updatedLabel(state.updated), modifier=Modifier.fillMaxWidth(), style=MatchType.Micro,
            color=MatchColors.Secondary, textAlign=TextAlign.Center)
    }
}
