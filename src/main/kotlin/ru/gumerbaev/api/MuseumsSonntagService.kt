package ru.gumerbaev.api

import com.google.common.base.Suppliers
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import ru.gumerbaev.api.dto.museum.Museum
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Singleton
class MuseumsSonntagService(private val client: MuseumsSonntagClient) {
    companion object {
        private val logger = LoggerFactory.getLogger(MuseumsSonntagService::class.java)
    }

    private val museumsCache = Suppliers.memoizeWithExpiration(
        { client.museums().museums?.filter { it.id != 67 }?.sortedBy { it.id } }, 1, TimeUnit.HOURS
    )

    fun getMuseumsInfo(): List<Museum> {
        return museumsCache.get() ?: throw IllegalStateException("No museums available")
    }

    fun getMuseumInfo(id: Int): Museum {
        return museumsCache.get()?.first { it.id == id } ?: throw NoSuchElementException("Museum not found")
    }

    fun getCapacities(museumId: Int, date: LocalDate): Pair<Int, Int> {
        val ticketIds = client.tickets(museumId, date).tickets.map { it.id }
        if (ticketIds.none { true }) logger.warn("No tickets found for museum $museumId").also { return 0 to 0 }
        val capacityData = client.capacities(date, ticketIds).data
        return capacityData.values.sumOf { data -> data.capacities.values.sum() } to
                capacityData.values.sumOf { data -> data.totalCapacities.values.sum() }
    }
}
