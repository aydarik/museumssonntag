package ru.gumerbaev.telegram

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.gumerbaev.api.MuseumsSonntagService
import ru.gumerbaev.api.dto.museum.Museum
import ru.gumerbaev.jpa.entity.AppUser
import ru.gumerbaev.jpa.entity.Task
import ru.gumerbaev.jpa.repository.AppUserRepository
import ru.gumerbaev.jpa.repository.TaskRepository

@Context
class TelegramService(
    @Value("\${museumssonntag.bot}") botId: String,
    userRepository: AppUserRepository,
    private val taskRepository: TaskRepository,
    private val api: MuseumsSonntagService,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(TelegramService::class.java)
    }

    private val bot = TelegramBot(botId)
    private val userCache = HashMap<Long, AppUser>()

    init {
        logger.debug("Initializing listener...")
        bot.setUpdatesListener { updates: List<Update?>? ->
            updates?.filter { it?.message() != null }?.forEach { processMessage(it?.message()!!) }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
        logger.debug("Listener initialized successfully")

        logger.debug("Getting available users...")
        userRepository.findAll().forEach { userCache[it.id] = it }
        logger.debug("Users loaded")
    }

    private fun processMessage(message: Message) {
        val chat = message.chat()
        val user = userCache[chat.id()]
        if (user == null) {
            logger.warn("Message from unknown user ${chat.id()}:${chat.firstName()}: '${message.text()}'")
            sendText(AppUser(chat.id(), "Anonymous"), "Sorry, you are not allowed to write me.")
            return
        }

        val text = message.text()
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
        when (text) {
            "/start" -> sendText(user, "Hello ${user.name} 😃")
            "/list" -> sendText(user, formatMuseumList())
            "/tasks" -> sendText(user, formatUserTasks(user))
            else -> createOrDeleteTask(user, text)
        }
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

