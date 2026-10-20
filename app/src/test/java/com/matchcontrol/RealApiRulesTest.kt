package com.matchcontrol

import com.matchcontrol.data.FootballResponse
import com.matchcontrol.data.RequestBudget
import com.matchcontrol.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class RealApiRulesTest {
    private fun match(leagueId: Int?, name: String) =
        Match(1, Team(1, "A"), Team(2, "B"), name, "2026-10-19T20:00:00Z", "NS", leagueId = leagueId)

    @Test fun sameLeagueNameFromAnotherCountryDoesNotPassIdFilter() {
        val competition = Competition(39, "Premier League", "Premier League", "England")
        assertTrue(competition.includes(match(39, "Premier League")))
        assertFalse(competition.includes(match(123456, "Premier League")))
    }

    @Test fun translatedNamesStillMatchVerifiedCatalog() {
        val competition = Competition(140, "LaLiga", "La Liga", "Spain")
        assertTrue(competition.includes(match(null, "LaLiga")))
        assertTrue(competition.includes(match(140, "La Liga")))
        assertFalse(competition.includes(match(123456, "LaLiga")))
    }

    @Test fun providerPlanErrorIsNeverAcceptedAsEmptyMatchDay() {
        val response = Json.parseToJsonElement("""{"errors":{"plan":"restricted season"},"response":[]}""").jsonObject
        val error = assertThrows(IllegalStateException::class.java) { FootballResponse.validate(response) }
        assertTrue(error.message.orEmpty().contains("cobertura"))
    }

    @Test fun legitimateEmptyDayIsAccepted() {
        FootballResponse.validate(Json.parseToJsonElement("""{"errors":[],"response":[]}""").jsonObject)
        FootballResponse.validate(Json.parseToJsonElement("""{"errors":{},"response":[]}""").jsonObject)
    }

    @Test fun incompletePayloadIsRejectedBeforeCaching() {
        assertThrows(IllegalStateException::class.java) {
            FootballResponse.validate(Json.parseToJsonElement("""{"errors":[]}""").jsonObject)
        }
    }

    @Test fun localRequestBudgetPreservesDailyReserve() {
        assertTrue(RequestBudget.allows(94, null))
        assertFalse(RequestBudget.allows(95, null))
        assertFalse(RequestBudget.allows(100, 100))
    }

    @Test fun remoteBudgetProtectsSharedQuotaEvenWithLowLocalUsage() {
        assertTrue(RequestBudget.allows(0, 6))
        assertFalse(RequestBudget.allows(0, 5))
        assertFalse(RequestBudget.allows(0, 0))
    }
}
