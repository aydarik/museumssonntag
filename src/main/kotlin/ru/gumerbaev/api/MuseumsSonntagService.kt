package ru.gumerbaev.api

import com.google.common.base.Suppliers
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import ru.gumerbaev.api.dto.museum.Museum
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@Singleton
class MuseumsSonntagService(private val client: MuseumsSonntagClient) {
    companion object {
        private val logger = LoggerFactory.getLogger(MuseumsSonntagService::class.java)
    }

    private val museumsCache = Suppliers.memoizeWithExpiration(
        { client.museums().museums?.filter { it.id != 67 }?.sortedBy { it.id } },
        1,
        TimeUnit.HOURS
    )

    fun getMuseumsInfo(): List<Museum> {
        return museumsCache.get() ?: throw IllegalStateException("No museums available")
    }

    fun getMuseumInfo(id: Int): Museum {
        return museumsCache.get()?.first { it.id == id } ?: throw NoSuchElementException("Museum not found")
    }

    fun getFreeCapacities(museumId: Int, date: LocalDate): List<Date> {
        val ticketIds = client.tickets(museumId, date).tickets.map { it.id }
        if (ticketIds.size > 1) {
            logger.warn("Found more then one ticket ID for museum $museumId")
        }

        val ticketId = ticketIds.single()
        val capacities = client.capacities(ticketId, date).data
        if (capacities.size > 1) {
            logger.warn("Found more then one capacity for museum $museumId by ticket $ticketId")
        }

        val capacity = capacities.values.single().capacities
        return capacity.keys.filter { capacity[it]!! > 0 }.sorted()
    }
}
