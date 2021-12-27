package ru.gumerbaev.jpa.repository

import io.micronaut.context.annotation.Executable
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import ru.gumerbaev.jpa.entity.AppUser
import ru.gumerbaev.jpa.entity.Task

@Repository
interface TaskRepository : CrudRepository<Task, Long> {

    @Executable
    fun findByUser(user: AppUser): Collection<Task>

    @Executable
    fun findByUserAndMuseum(user: AppUser, museumId: Int): Task?

    @Executable
    fun deleteByMuseum(museumId: Int)
}
