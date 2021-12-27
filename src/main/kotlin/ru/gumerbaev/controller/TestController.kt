package ru.gumerbaev.controller

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import ru.gumerbaev.api.MuseumsSonntagService
import ru.gumerbaev.api.dto.museum.Museum

@Controller("/test")
class TestController(private val api: MuseumsSonntagService) {

    @Get(produces = [MediaType.APPLICATION_JSON])
    fun index(): List<Museum> {
        return api.getMuseumsInfo()
    }
}
