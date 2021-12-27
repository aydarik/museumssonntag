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
    @Value("\${museumssonntag.users}") users: String
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(AppContext::class.java)
    }

    init {
        val availableUsers = users.split(",")
        logger.info("Available users: ${availableUsers.size}")
        availableUsers.forEach {
            val user = it.split(":")
            val id = user[0].toLong()
            val name = user[1]
            userRepository.findById(id).ifPresentOrElse({ logger.info("User exists: $name") }) {
                logger.info("Creating user $name")
                userRepository.save(AppUser(id, name))
            }
        }
    }
}
