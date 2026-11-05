package io.stridelab.privatealpha

/**
 * Active Android activity providers after retiring Intervals.icu integration.
 *
 * Historical Intervals.icu rows are retained in the account-scoped SQLite
 * cache and on the server, but no longer participate in presentation, plan
 * mileage, source grouping or fresh backend activity imports. Garmin, native
 * recordings, manual and user-authorized FIT records remain available.
 */
internal object ActiveActivitySources {
    fun accepts(source:String):Boolean = !source.equals("intervals",ignoreCase=true)
    fun filter(history:List<ActivityRecord>):List<ActivityRecord> =
        history.filter { accepts(it.source) }
}
