package ru.gumerbaev

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.gumerbaev.jpa.entity.AppUser
import ru.gumerbaev.jpa.repository.AppUserRepository

@Context
class AppContext(
    userRepository: AppUserRepository,
    @Value("\${museumssonntag.admin}") admin: String
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(AppContext::class.java)
    }

    init {
        val user = admin.split(":")
        val id = user[0].toLong()
        val name = user[1]
        userRepository.findById(id).ifPresentOrElse({ logger.info("Admin exists: $name") }) {
            logger.info("Creating user $name").also { userRepository.save(AppUser(id, name)) }
        }
    }
}
