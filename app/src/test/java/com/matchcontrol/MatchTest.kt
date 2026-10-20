package com.matchcontrol
import com.matchcontrol.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone
class MatchTest {
 private fun match(status: String, kickoff: String="2026-10-17T01:30:00Z") = Match(1,Team(1,"A"),Team(2,"B"),"Liga",kickoff,status)
 @Test fun liveAndFinalStatusesAreDisjoint() {
  listOf("1H","HT","2H","ET","BT","P","LIVE").forEach { assertTrue(match(it).live); assertFalse(match(it).finished) }
  listOf("FT","AET","PEN","AWD","WO").forEach { assertTrue(match(it).finished); assertFalse(match(it).live) }
  listOf("NS","PST","CANC","ABD").forEach { assertFalse(match(it).live); assertFalse(match(it).finished) }
 }
 @Test fun fixturesUseWatchLocalDateAcrossMidnight() {
  val original=TimeZone.getDefault()
  try { TimeZone.setDefault(TimeZone.getTimeZone("America/Phoenix")); assertEquals(LocalDate.of(2026,10,16),match("NS").date) }
  finally { TimeZone.setDefault(original) }
 }
 @Test fun missingGoalsAreNotInvented() { assertNull(match("NS").homeGoals); assertNull(match("NS").awayGoals); assertTrue(match("NS").statistics.isEmpty()) }
 @Test fun cancelledAndAbandonedMatchesAreNotUpcoming() {
  listOf("CANC","ABD","AWD","WO","FT").forEach { assertFalse(match(it).upcoming) }
  listOf("NS","TBD","PST").forEach { assertTrue(match(it).upcoming) }
 }
 @Test fun apiOffsetTimestampsUseTheSameLocalDayAsUtcTimestamps() {
  val original=TimeZone.getDefault()
  try {
   TimeZone.setDefault(TimeZone.getTimeZone("America/Phoenix"))
   val expected=LocalDate.of(2026,10,16)
   assertEquals(expected, match("NS", "2026-10-17T01:30:00+00:00").date)
   assertEquals(expected, match("NS", "2026-10-17T03:30:00+02:00").date)
   assertEquals(expected, match("NS", "2026-10-17T01:30:00Z").date)
  } finally { TimeZone.setDefault(original) }
 }
}
