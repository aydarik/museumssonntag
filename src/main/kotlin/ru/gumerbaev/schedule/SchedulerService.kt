package ru.gumerbaev.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MediaType
import io.micronaut.scheduling.annotation.Scheduled
import org.slf4j.LoggerFactory
import ru.gumerbaev.api.MuseumsSonntagService
import ru.gumerbaev.jpa.entity.AppUser
import ru.gumerbaev.jpa.repository.TaskRepository
import ru.gumerbaev.telegram.TelegramService
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    private val client = HttpClient.newBuilder().build()

    init {
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
            val capacities = api.getCapacities(museumId, nextSunday)
            logger.info("Available slots: ${capacities.first} / ${capacities.second}")
            if (capacities.first > 0)
                sendAvailableMessages(museumId, museumUsersMap[museumId], nextSunday, capacities)
            else if (capacities.second > 0) {
                sendNotAvailableMessages(museumId, museumUsersMap[museumId], nextSunday, capacities)
            }
        }
    }

    private fun sendAvailableMessages(
        museumId: Int,
        users: Set<AppUser>?,
        nextSunday: LocalDate,
        capacities: Pair<Int, Int>
    ) {
        val museum = api.getMuseumInfo(museumId)
        val textToSend = "Found ${capacities.first} out of ${capacities.second} " +
                "available slots for *$nextSunday* to *${museum.title}*\n" +
                "https://shop.museumssonntag.berlin/#/tickets/time?museum_id=$museumId&group=timeSlot&date=$nextSunday"
        users?.forEach { telegram.sendText(it, textToSend) }
        webhookIfNeeded(users, Message("Found ${capacities.first} available slots for $nextSunday to ${museum.title}"))
        taskRepository.deleteByMuseum(museumId)
        logger.info("Task for museum $museumId deleted")
    }

    private fun sendNotAvailableMessages(
        museumId: Int, users: Set<AppUser>?, nextSunday: LocalDate, capacities: Pair<Int, Int>
    ) {
        val museum = api.getMuseumInfo(museumId)
        val textToSend = "All ${capacities.second} slots are already reserved for *$nextSunday* to *${museum.title}*"
        users?.forEach { user -> telegram.sendText(user, textToSend) }
        taskRepository.deleteByMuseum(museumId)
        logger.info("Task for museum $museumId deleted")
    }

    private fun webhookIfNeeded(users: Set<AppUser>?, message: Message) {
        users?.filter { !it.webhook.isNullOrEmpty() }?.forEach {
            val requestBody = ObjectMapper().writeValueAsString(message)
            val request = HttpRequest.newBuilder().uri(URI.create(it.webhook!!))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    @Scheduled(cron = "0 0 * * *")
    fun checkIfTimeToBook() {
        val now = LocalDate.now()
        val sunday = now.with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
        nextSunday = if (sunday.isBefore(now)) {
            now.plusMonths(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
        } else sunday
        logger.info("Next sunday: $nextSunday")

        val daysBetween = ChronoUnit.DAYS.between(now, nextSunday)
        isTimeToCheck = !(daysBetween > daysMax || daysBetween < daysMin)
        logger.info("$daysMin ≤ $daysBetween ≤ $daysMax: $isTimeToCheck")
    }
}
