package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Observed session pattern only: sparse logs never establish complete weekly mileage. */
internal data class Engine15RunHistory(val easyKm:Double?,val longKm:Double?,val evidenceIds:List<String>,val thresholdWorkKm:Double?=null) {
    companion object {
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
            fun median(kind:String):Double? {
                val rows=observed.filter { it.kind==kind }
                // Repetition across weeks, not duplicated uploads or a single exceptional run.
                if(rows.map { it.coachingLog()!!.performedOn!!.with(java.time.DayOfWeek.MONDAY) }.distinct().size<2 || rows.size<2)return null
                val dates=rows.map { it.coachingLog()!!.performedOn!! }
                if(ChronoUnit.DAYS.between(dates.min(),dates.max())<7 || ChronoUnit.DAYS.between(dates.max(),today)>14)return null
                val values=rows.map { it.coachingLog()!!.distanceKm!! }.sorted()
                return (values[(values.size-1)/2]+values[values.size/2])/2
            }
            val easy=median("easy"); val long=median("long")
            val threshold=sessions.filter { s ->
                val log=s.coachingLog();val date=log?.performedOn
                s.discipline=="run" && s.quality && s.runningPrescription?.family=="Threshold" && s.suggestion?.accepted!=true &&
                    s.status=="completed" && s.logRevision>0 && s.id !in duplicateIds && s.linkedActivityId !in duplicateLinks &&
                    log!=null && !log.at.isAfter(now) && date!=null && ChronoUnit.DAYS.between(date,today) in 0L..27L &&
                    kotlin.math.abs(ChronoUnit.DAYS.between(s.date,date))<=2 && log.effort in 6..7 &&
                    log.completionDifficulty=="As planned" && log.discomfort=="None" && log.qualityWorkKm!=null && log.distanceKm!=null
            }
            val dates=threshold.map { it.coachingLog()!!.performedOn!! }
            val work=if(dates.size>=2 && ChronoUnit.DAYS.between(dates.min(),dates.max())>=7 && ChronoUnit.DAYS.between(dates.max(),today)<=14) {
                val values=threshold.map { it.coachingLog()!!.qualityWorkKm!! }.sorted()
                (values[(values.size-1)/2]+values[values.size/2])/2
            } else null
            if(easy==null && long==null && work==null)return null
            return Engine15RunHistory(easy,long,(observed+threshold).map { "${it.id}@${it.logRevision}" }.distinct().sorted(),work)
        }
    }
}
