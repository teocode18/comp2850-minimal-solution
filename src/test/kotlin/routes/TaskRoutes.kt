package routes

import data.TaskRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter

fun Route.taskRoutes() {
    val pebble = PebbleEngine.Builder().build()

    get("/tasks") {
        val model = mapOf(
            "title" to "Tasks",
            "tasks" to TaskRepository.all()
        )
        val template = pebble.getTemplate("templates/tasks/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        call.respondText(writer.toString(), ContentType.Text.Html)
    }

    post("/tasks") {
        val title = call.receiveParameters()["title"].orEmpty().trim()
        if (title.isNotBlank()) {
            TaskRepository.add(title)
        }
        call.respondRedirect("/tasks") // PRG pattern
    }

    post("/tasks/{id}/delete") {
        val id = call.parameters["id"]?.toIntOrNull()
        id?.let { TaskRepository.delete(it) }
        call.respondRedirect("/tasks") // PRG pattern
    }
}
