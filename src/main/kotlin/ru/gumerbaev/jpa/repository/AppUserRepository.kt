package ru.gumerbaev.jpa.repository

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import ru.gumerbaev.jpa.entity.AppUser

@Repository
interface AppUserRepository : CrudRepository<AppUser, Long>
