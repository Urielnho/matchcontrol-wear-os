package com.matchcontrol.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.matchcontrol.domain.Match
import com.matchcontrol.domain.Team
import com.matchcontrol.presentation.*
import com.matchcontrol.presentation.theme.*

@Composable fun TeamCrest(team: Team, size: Dp = 28.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MatchColors.Raised)
        .semantics { contentDescription = team.name }, contentAlignment = Alignment.Center) {
        Text(teamCode(team.name), style = MatchType.Micro, color = MatchColors.Text)
    }
}

@Composable fun AnimatedScore(value: String, hero: Boolean = false, detail: Boolean = false, compact: Boolean = false) {
    val style = when { detail -> if(compact) MatchType.DetailScore.copy(fontSize=36.sp,lineHeight=41.sp) else MatchType.DetailScore; hero -> MatchType.HeroScore; else -> MatchType.Score }
    if (LocalReduceMotion.current) Text(value, style = style, color = MatchColors.Text)
    else AnimatedContent(targetState = value, transitionSpec = {
        fadeIn(tween(180)) togetherWith fadeOut(tween(100))
    }, label = "Marcador") { Text(it, style = style, color = MatchColors.Text) }
}

@Composable fun MatchStatus(match: Match) {
    val color = when { match.live -> MatchColors.Live; match.status in setOf("SUSP", "INT", "PST") -> MatchColors.Warning; else -> MatchColors.Secondary }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (match.live) Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(stateLabel(match), style = MatchType.Micro, color = color)
    }
}

@Composable fun MatchCard(
    match: Match, favorite: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, hero: Boolean = false,
    transformation: SurfaceTransformation? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.975f else 1f,
        animationSpec = tween(100), label = "Pulsación de partido")
    Card(
        onClick = onClick, interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = "${match.home.name} contra ${match.away.name}, ${match.homeGoals ?: "sin marcador"} a ${match.awayGoals ?: "sin marcador"}, ${stateLabel(match)}, ${match.league}" },
        shape = RoundedCornerShape(MatchSpace.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MatchColors.Surface),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = if (hero) 10.dp else 9.dp),
        transformation = transformation,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (hero) 6.dp else 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                if(match.upcoming) Text(if(match.status=="PST") "Aplazado" else "Próximo",style=MatchType.Micro,color=MatchColors.Secondary)
                else MatchStatus(match)
                if (favorite) {
                    Spacer(Modifier.width(6.dp))
                    SportGlyph(Glyph.Star, Modifier.size(11.dp), MatchColors.Accent)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TeamCrest(match.home, if (hero) 30.dp else 24.dp)
                }
                if (!match.upcoming || match.homeGoals != null || match.awayGoals != null) {
                    AnimatedScore("${match.homeGoals ?: "–"} : ${match.awayGoals ?: "–"}", hero)
                } else {
                    Text(localMatchTime(match, "HH:mm"), style = MatchType.Title, color = MatchColors.Text)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TeamCrest(match.away, if (hero) 30.dp else 24.dp)
                }
            }
            Text(match.league, modifier = Modifier.fillMaxWidth(), style = MatchType.Caption,
                color = MatchColors.Secondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable fun TransformingLazyColumnItemScope.MatchTile(match: Match, favorite: Boolean, hero: Boolean = false, onClick: () -> Unit) {
    val spec = rememberTransformationSpec()
    MatchCard(match, favorite, onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        hero = hero, transformation = SurfaceTransformation(spec))
}
