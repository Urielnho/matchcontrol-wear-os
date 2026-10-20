package com.matchcontrol

import com.matchcontrol.domain.*
import org.junit.Assert.*
import org.junit.Test

class MatchAlertsTest {
 private val next=Match(1,Team(10,"Local"),Team(20,"Visitante"),"Liga","2026-10-19T15:00:00Z","2H",60,2,1)
 @Test fun firstObservationAndUnfollowedTeamsDoNotSendAlerts() {
  assertTrue(matchAlerts(null,next,setOf("10")).isEmpty())
  assertTrue(matchAlerts(next.copy(homeGoals=1),next,emptySet()).isEmpty())
 }
 @Test fun onlyFavoriteScoreIncreasesProduceGoalAlerts() {
  assertEquals(listOf("Gol"),matchAlerts(next.copy(homeGoals=1),next,setOf("10")))
  assertTrue(matchAlerts(next.copy(homeGoals=1),next,setOf("20")).isEmpty())
  assertTrue(matchAlerts(next.copy(homeGoals=3),next,setOf("10")).isEmpty())
  assertTrue(matchAlerts(next,next,setOf("10")).isEmpty())
 }
 @Test fun MissingScoreDoesNotImplyGoalAndDifferentFixturesNeverCompare() {
  assertTrue(matchAlerts(next.copy(homeGoals=null),next,setOf("10")).isEmpty())
  assertTrue(matchAlerts(next.copy(id=2,homeGoals=1),next,setOf("10")).isEmpty())
 }
 @Test fun startAndFinalRequirePublishedStatusChanges() {
  assertEquals(listOf("Inicio"),matchAlerts(next.copy(status="NS"),next,setOf("10")))
  assertEquals(listOf("Final"),matchAlerts(next,next.copy(status="FT"),setOf("10")))
  assertTrue(matchAlerts(next.copy(status="FT"),next,setOf("10")).isEmpty())
 }
}
