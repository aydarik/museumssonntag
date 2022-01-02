package ru.gumerbaev.api.dto.ticket

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class Capacities(
    val capacities: Map<Date, Int>,
    @JsonProperty("total_capacities") val totalCapacities: Map<Date, Int>
)
