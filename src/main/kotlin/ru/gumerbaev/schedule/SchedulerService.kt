package ru.gumerbaev.schedule

import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import ru.gumerbaev.api.MuseumsSonntagService
import ru.gumerbaev.jpa.entity.AppUser
import ru.gumerbaev.jpa.repository.TaskRepository
import ru.gumerbaev.telegram.TelegramService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random.Default.nextLong

@Singleton
class SchedulerService(
    private val api: MuseumsSonntagService,
    private val telegram: TelegramService,
    private val taskRepository: TaskRepository,
    @Value("\${museumssonntag.daysMax}") private val daysMax: Long,
    @Value("\${museumssonntag.daysMin}") private val daysMin: Long
) {

    companion object {
        private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
        private const val MIN_DELAY_SEC = 60
    }

    @Scheduled(fixedDelay = "\${museumssonntag.interval}", initialDelay = "${MIN_DELAY_SEC}s")
    fun checkTickets() {
        val now = LocalDate.now()
        val nextSunday = calculateNextSunday()
        val daysBetween = ChronoUnit.DAYS.between(now, nextSunday)
        logger.debug("Next Sunday: $nextSunday, days between: $daysBetween")
        if (daysBetween > daysMax || daysBetween < daysMin) return

        logger.debug("Time to check tickets!")
        val map = HashMap<Int, MutableSet<AppUser>>()
        taskRepository.findAll().forEach { map.putIfAbsent(it.museum, mutableSetOf(it.user))?.add(it.user) }
        if (map.isEmpty()) return
        logger.debug("Found ${map.size} museums to check")

        map.keys.forEach { museumId ->
            logger.debug("Checking museum $museumId for $nextSunday")
            val sleep = nextLong(0, MIN_DELAY_SEC * 1000 / map.size.toLong())
            logger.debug("Sleeping $sleep ms...")
            Thread.sleep(sleep)

            val freeSlots = api.getFreeCapacities(museumId, nextSunday)
            if (freeSlots.isNotEmpty()) {
                logger.debug("Found some available slots")

                val museum = api.getMuseumInfo(museumId)
                map[museumId]!!.forEach { user ->
                    telegram.sendText(user, "Found some slots available for *$nextSunday* to *${museum.title}*")
                }
                taskRepository.deleteByMuseum(museumId)
                logger.debug("Task for museum $museumId deleted")
            }
        }
    }

    private fun calculateNextSunday(): LocalDate {
        val now = LocalDate.now()
        val sunday = now.with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
        return if (sunday.isAfter(now.plusDays(daysMin))) {
            sunday
        } else {
            now.plusMonths(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
        }
    }
}
