package ru.gumerbaev.schedule

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
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

@Context
class SchedulerService(
    private val api: MuseumsSonntagService,
    private val telegram: TelegramService,
    private val taskRepository: TaskRepository,
    @Value("\${museumssonntag.days-max}") private val daysMax: Long,
    @Value("\${museumssonntag.days-min}") private val daysMin: Long
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
        private const val MIN_DELAY_SEC = 60
    }

    private lateinit var nextSunday: LocalDate
    private var isTimeToCheck: Boolean = false

    init {
        logger.info("Days between configuration: $daysMin ≤ X ≤ $daysMax")
        checkIfTimeToBook()
    }

    @Scheduled(fixedDelay = "\${museumssonntag.interval}", initialDelay = "${MIN_DELAY_SEC}s")
    fun checkTickets() {
        if (!isTimeToCheck) return

        val museumUsersMap = HashMap<Int, MutableSet<AppUser>>()
        taskRepository.findAll().forEach { museumUsersMap.putIfAbsent(it.museum, mutableSetOf(it.user))?.add(it.user) }
        if (museumUsersMap.isEmpty()) return

        museumUsersMap.keys.forEach { museumId ->
            val sleep = nextLong(0, MIN_DELAY_SEC * 1000 / museumUsersMap.size.toLong())
            logger.debug("Sleeping $sleep ms...").also { Thread.sleep(sleep) }
            logger.info("Checking museum $museumId for $nextSunday")
            if (api.hasFreeCapacity(museumId, nextSunday))
                sendAvailableMessages(museumId, museumUsersMap[museumId], nextSunday)
            else logger.debug("No available slots for $museumId")
        }
    }

    private fun sendAvailableMessages(museumId: Int, users: Set<AppUser>?, nextSunday: LocalDate) {
        logger.info("Found some available slots")
        val museum = api.getMuseumInfo(museumId)
        val textToSend = "Found some slots available for *$nextSunday* to *${museum.title}*\n" +
                "https://shop.museumssonntag.berlin/#/tickets/time?museum_id=$museumId&group=timeSlot&date=$nextSunday"
        users?.forEach { user -> telegram.sendText(user, textToSend) }
        taskRepository.deleteByMuseum(museumId)
        logger.info("Task for museum $museumId deleted")
    }

    @Scheduled(cron = "0 0 * * *")
    fun checkIfTimeToBook() {
        val now = LocalDate.now()
        val sunday = now.with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
        nextSunday = if (sunday.isAfter(now.plusDays(daysMin))) sunday
        else now.plusMonths(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
        logger.info("Next sunday: $nextSunday")

        val daysBetween = ChronoUnit.DAYS.between(now, nextSunday)
        logger.debug("Days between: $daysBetween")
        isTimeToCheck = !(daysBetween > daysMax || daysBetween < daysMin)
    }
}
