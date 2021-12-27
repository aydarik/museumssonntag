package ru.gumerbaev.jpa.entity

import io.micronaut.data.annotation.DateCreated
import java.util.*
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.OneToMany

@Entity
data class AppUser(
    @Id val id: Long,
    val name: String,
    @OneToMany(cascade = [CascadeType.REMOVE]) val tasks: Collection<Task>? = null,
    @DateCreated val createdDate: Date? = null
)
