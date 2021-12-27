package ru.gumerbaev.telegram

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.gumerbaev.api.MuseumsSonntagService
import ru.gumerbaev.api.dto.museum.Museum
import ru.gumerbaev.jpa.entity.AppUser
import ru.gumerbaev.jpa.entity.Task
import ru.gumerbaev.jpa.repository.AppUserRepository
import ru.gumerbaev.jpa.repository.TaskRepository

@Singleton
class TelegramService(
    @Value("\${museumssonntag.bot}") botId: String,
    private val userRepository: AppUserRepository,
    private val taskRepository: TaskRepository,
    private val api: MuseumsSonntagService
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(TelegramService::class.java)
    }

    private val bot = TelegramBot(botId)
    private val userCache = HashMap<Long, AppUser>()
    private lateinit var admin: AppUser

    fun init(adminId: Long) {
        logger.debug("Initializing listener...")
        bot.setUpdatesListener { updates: List<Update?>? ->
            updates?.filter { it?.message() != null }?.forEach { processMessage(it?.message()!!) }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
        logger.debug("Listener initialized successfully")

        logger.debug("Getting available users...")
        userRepository.findAll().forEach { userCache[it.id] = it }
        admin = userCache[adminId]!!
        logger.debug("Users loaded")
    }

    private fun processMessage(message: Message) {
        val chat = message.chat()
        val user = userCache[chat.id()]
        val text = message.text()
        if (user == null) {
            logger.warn("Message from unknown user ${chat.id()}:${chat.firstName()}: '$text'")
            sendText(AppUser(chat.id(), "Anonymous"), "Sorry, you are not allowed to write me.")
            if (text == "/start") sendText(admin, "${chat.id()}:${chat.firstName()} requested to join")
            return
        }

        if (text != null) {
            logger.info("Message from ${user.name} received: `$text`")
            try {
                processTextMessage(user, text)
            } catch (e: Exception) {
                logger.error("Error on the workflow", e)
                sendText(user, "Sorry, something went wrong \uD83D\uDE14")
            }
        } else {
            sendText(user, "Sorry, I can only process text messages.")
        }
    }

    private fun processTextMessage(user: AppUser, text: String) {
        if (tryAdminCommands(user, text)) return
        when (text) {
            "/start" -> sendText(user, "Hello ${user.name} 😃")
                .also { processTextMessage(user, "/help") }
            "/list" -> sendText(user, formatMuseumList())
            "/tasks" -> sendText(user, formatUserTasks(user))
            "/help" -> sendText(user, formatHelpText(user))
            else -> createOrDeleteTask(user, text)
        }
    }

    private fun formatHelpText(user: AppUser): String {
        return if (user != admin)
            "Just send the museum ID and I'll keep an eye on it for the upcoming *MuseumsSonntag* in Berlin.\n" +
                    "Send it again to delete an existing task. Please also check the list of available commands!"
        else "/adminUsers [ID[:NAME]] - list/add/rename/delete users\n" +
                "/adminTasks [ID <or> USER_ID:MUSEUM_UD] - list/delete/create tasks"
    }

    private fun tryAdminCommands(user: AppUser, text: String): Boolean {
        if (user != admin) return false
        if (text.startsWith("/adminUsers")) {
            adminUsers(user, text)
            return true
        }
        if (text.startsWith("/adminTasks")) {
            adminTasks(user, text)
            return true
        }
        return false
    }

    private fun adminUsers(user: AppUser, text: String) {
        val textToSend: String
        val split = text.split(" ")
        if (split.size > 1) {
            val userSplit = split[1].split(":")
            val userId = userSplit[0].toLong()
            if (userSplit.size > 1) {
                val userName = userSplit[1]
                userCache[userId] =
                    userRepository.update(userCache[userId]?.apply { name = userName } ?: AppUser(userId, userName))
                textToSend = "User $userName added with ID: $userId"
                logger.info(textToSend)
            } else {
                userCache.remove(userId).also { if (it != null) userRepository.delete(it) }
                textToSend = "User $userId deleted"
                logger.info(textToSend)
            }
        } else {
            textToSend = userRepository.findAll().joinToString("\n") { "${it.id} -> ${it.name}" }
        }
        sendText(user, textToSend)
    }

    private fun adminTasks(user: AppUser, text: String) {
        val textToSend: String
        val split = text.split(" ")
        if (split.size > 1) {
            val taskSplit = split[1].split(":")
            val id = taskSplit[0].toLong()
            if (taskSplit.size > 1) {
                val appUser = userCache[id]
                if (appUser != null) {
                    taskRepository.save(Task(user = appUser, museum = taskSplit[1].toInt()))
                    textToSend = "Task for user ${user.name} with museum ${taskSplit[1]} created"
                    logger.info(textToSend)
                } else {
                    textToSend = "No such user: $id"
                }
            } else {
                val taskId = split[1].toLong()
                taskRepository.findById(taskId).ifPresent { taskRepository.delete(it) }
                textToSend = "Task $taskId deleted"
                logger.info(textToSend)
            }
        } else {
            textToSend = taskRepository.findAll().joinToString("\n") { "${it.id} -> ${it.museum}: ${it.user.name}" }
        }
        sendText(user, textToSend)
    }

    private fun formatMuseumList(): String {
        return api.getMuseumsInfo().joinToString("\n") { "*${it.id}*: ${it.title}" }
    }

    private fun formatUserTasks(user: AppUser): String {
        val userTasks = taskRepository.findByUser(user)
        return if (userTasks.isEmpty()) "Nothing to check yet" else userTasks
            .joinToString("\n") { "*${it.museum}*: ${api.getMuseumInfo(it.museum).title}" }
    }

    private fun createOrDeleteTask(user: AppUser, text: String) {
        try {
            val museum = api.getMuseumInfo(text.toInt())
            var task = taskRepository.findByUserAndMuseum(user, museum.id)
            if (task == null) {
                task = taskRepository.save(Task(user = user, museum = museum.id))
                logger.info("Task created for ${user.name}: ${task.id}")
                sendBooking(user, museum)
            } else {
                taskRepository.delete(task)
                logger.info("Task deleted for ${user.name}: ${task.id}")
                sendText(user, "Task deleted, no more notifications about *${museum.title}*")
            }
        } catch (e: Exception) {
            logger.warn("Incorrect message from ${user.name}", e)
            sendText(user, "Hmm, looks like not correct museum ID")
        }
    }

    private fun sendBooking(user: AppUser, museum: Museum) {
        logger.info("Sending booking to ${user.name}: '${museum.title}'")
        bot.execute(
            SendPhoto(user.id, museum.picture.detail)
                .caption("Ok, I'll notify you about tickets to *${museum.title}*")
                .parseMode(ParseMode.Markdown)
        )
    }

    fun sendText(user: AppUser, textToSend: String) {
        logger.debug("Sending text to ${user.name}")
        bot.execute(SendMessage(user.id, textToSend).parseMode(ParseMode.Markdown))
    }
}

