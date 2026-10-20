package com.matchcontrol.data

internal object RequestBudget {
    fun allows(localCount: Int, remoteRemaining: Int?): Boolean =
        localCount < 95 && (remoteRemaining == null || remoteRemaining > 5)
}
