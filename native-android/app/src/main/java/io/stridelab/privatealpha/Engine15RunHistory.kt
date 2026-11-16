package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Observed session pattern only: sparse logs never establish complete weekly
 * mileage, measured fitness, pace zones or tolerance for automatic progression.
 */
internal data class Engine15RunHistory(
    val easyKm:Double?,
    val longKm:Double?,
    val evidenceIds:List<String>,
    val thresholdWorkKm:Double?=null,
    /** Explicitly completed quality-work distance, by the matching workout family. */
    val qualityWorkKmByFamily:Map<String,Double> = emptyMap()
) {
    fun verifiedWorkKm(family:String):Double? =
        qualityWorkKmByFamily[family] ?: if(family=="Threshold")thresholdWorkKm else null

    companion object {
        private fun median(values:List<Double>):Double {
            require(values.isNotEmpty())
            val ordered=values.sorted()
            return (ordered[(ordered.size-1)/2]+ordered[ordered.size/2])/2
        }

        fun assess(owner:String,plans:List<PlanSnapshot>,now:Instant):Engine15RunHistory? {
            val today=now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val sessions=plans.filter { it.owner==owner && Engine1Engines.canManage(it) }.flatMap { it.sessions }
            val duplicateLinks=sessions.mapNotNull { it.linkedActivityId }.groupingBy { it }.eachCount().filterValues { it>1 }.keys
            val duplicateIds=sessions.groupingBy { it.id }.eachCount().filterValues { it>1 }.keys
            val observed=sessions.filter { s ->
                val log=s.coachingLog()
                val date=log?.performedOn
                s.discipline=="run" && !s.quality && s.kind in setOf("easy","long") && s.status=="completed" &&
                    s.logRevision>0 && s.id !in duplicateIds && s.linkedActivityId !in duplicateLinks &&
                    log!=null && !log.at.isAfter(now) && date!=null && ChronoUnit.DAYS.between(date,today) in 0L..27L &&
                    kotlin.math.abs(ChronoUnit.DAYS.between(s.date,date))<=2 && log.distanceKm?.let { it.isFinite() && it in 0.1..120.0 }==true &&
                    log.effort in 1..5 && log.discomfort !in setOf("Pain","Soreness") && log.completionDifficulty !in setOf("Harder","Could not finish")
            }
            fun medianDistance(kind:String):Double? {
                val rows=observed.filter { it.kind==kind }
                // Repetition across weeks, not duplicated uploads or a single exceptional run.
                if(rows.map { it.coachingLog()!!.performedOn!!.with(java.time.DayOfWeek.MONDAY) }.distinct().size<2 || rows.size<2)return null
                val dates=rows.map { it.coachingLog()!!.performedOn!! }
                if(ChronoUnit.DAYS.between(dates.min(),dates.max())<7 || ChronoUnit.DAYS.between(dates.max(),today)>14)return null
                return median(rows.map { it.coachingLog()!!.distanceKm!! })
            }
            val easy=medianDistance("easy")
            val long=medianDistance("long")

            // Q3: only an explicitly logged, well-tolerated, completed WORK
            // distance can inform the same family in the next draft. A planned
            // interval, GPS distance, RPE alone or sparse single week is not
            // evidence that the prescribed work was actually performed.
            val quality=sessions.filter { s ->
                val log=s.coachingLog()
                val date=log?.performedOn
                val family=s.runningPrescription?.family
                s.discipline=="run" && s.quality && family in setOf(
                    "Threshold","Tempo","Intervals","Hills","Fartlek","Progression") &&
                    s.suggestion==null && s.status=="completed" && s.logRevision>0 &&
                    s.id !in duplicateIds && s.linkedActivityId !in duplicateLinks &&
                    log!=null && !log.at.isAfter(now) && date!=null &&
                    ChronoUnit.DAYS.between(date,today) in 0L..27L &&
                    kotlin.math.abs(ChronoUnit.DAYS.between(s.date,date))<=2 &&
                    log.effort in 6..7 &&
                    log.completionDifficulty=="As planned" && log.discomfort=="None" &&
                    log.distanceKm?.let { it.isFinite() && it in 0.1..120.0 }==true &&
                    log.qualityWorkKm?.let { it.isFinite() && it in 0.1..120.0 && it<=log.distanceKm!! }==true
            }
            val approvedByFamily=mutableMapOf<String,Double>()
            val qualifiedRows=mutableListOf<PlannedSession>()
            for((family,rows) in quality.groupBy { it.runningPrescription!!.family!! }) {
                val dates=rows.map { it.coachingLog()!!.performedOn!! }
                val weeks=rows.groupBy { it.coachingLog()!!.performedOn!!.with(java.time.DayOfWeek.MONDAY) }
                if(weeks.size<2 || ChronoUnit.DAYS.between(dates.min(),dates.max())<7 ||
                    ChronoUnit.DAYS.between(dates.max(),today)>14) continue
                // Give each observed week one vote; two sessions in one week
                // cannot inflate the evidence dose or impersonate two weeks.
                val weeklyWork=weeks.values.map { weekly ->
                    median(weekly.map { it.coachingLog()!!.qualityWorkKm!! })
                }
                approvedByFamily[family]=median(weeklyWork)
                qualifiedRows+=rows
            }
            if(easy==null && long==null && approvedByFamily.isEmpty())return null
            return Engine15RunHistory(
                easy,long,(observed+qualifiedRows).map { "${it.id}@${it.logRevision}" }.distinct().sorted(),
                approvedByFamily["Threshold"],approvedByFamily.toSortedMap()
            )
        }
    }
}
