package com.matchcontrol.domain

/** Compare published snapshots; never infer a start or result from the clock. */
fun matchAlerts(old: Match?, next: Match, favorites: Set<String>): List<String> {
    if(old==null || old.id!=next.id ||
        (next.home.id.toString() !in favorites && next.away.id.toString() !in favorites)) return emptyList()
    return buildList {
        if(!old.live && !old.finished && next.live) add("Inicio")
        val homeScored=next.home.id.toString() in favorites && old.homeGoals!=null && next.homeGoals!=null && next.homeGoals>old.homeGoals
        val awayScored=next.away.id.toString() in favorites && old.awayGoals!=null && next.awayGoals!=null && next.awayGoals>old.awayGoals
        if(homeScored || awayScored) add("Gol")
        if(!old.finished && next.finished) add("Final")
    }
}
