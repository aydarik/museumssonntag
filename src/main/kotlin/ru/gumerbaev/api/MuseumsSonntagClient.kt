package ru.gumerbaev.api

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import ru.gumerbaev.api.dto.museum.Museums
import ru.gumerbaev.api.dto.ticket.CapacityData
import ru.gumerbaev.api.dto.ticket.Tickets
import java.time.LocalDate

@Client("https://kpb-museum.gomus.de/api/v4")
interface MuseumsSonntagClient {

    @Get("/museums?locale=de&per_page=1000")
    fun museums(): Museums

    @Get("/tickets?by_bookable=true&by_free_timing=false&by_museum_ids%5B%5D={museumId}&by_ticket_type=time_slot&locale=de&per_page=1000&valid_at={date}")
    fun tickets(museumId: Int, date: LocalDate): Tickets

    @Get("/tickets/capacities?date={date}{&ticket_ids%5B%5D*}")
    fun capacities(date: LocalDate, @QueryValue("ticket_ids%5B%5D") ticketIds: List<Int>): CapacityData
}
