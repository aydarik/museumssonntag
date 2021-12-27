package ru.gumerbaev.jpa.entity

import io.micronaut.data.annotation.DateCreated
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "museum"])])
data class Task(
    @Id @GeneratedValue val id: Long = 0,
    @ManyToOne(optional = false) val user: AppUser,
    val museum: Int,
    @DateCreated val createdDate: Date? = null
)
