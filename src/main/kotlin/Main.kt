import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.sessions.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.util.AttributeKey
import io.pebbletemplates.pebble.PebbleEngine
import routes.configureTaskRoutes
import routes.configureHealthCheck
// import routes.configureEditRoutes // Week 7
import utils.ReqIdKey
import utils.SessionData
import utils.generateRequestId
import java.io.StringWriter

/**
 * Main entry point for COMP2850 HCI server-first application.
 *
 * **Architecture**:
 * - Server-side rendering with Pebble templates
 * - Progressive enhancement via HTMX
 * - No-JS parity required for all features
 * - Privacy-by-design: anonymous session IDs only
 *
 * **Key Principles**:
 * - WCAG 2.2 AA compliance mandatory
 * - Semantic HTML baseline
 * - ARIA live regions for dynamic updates
 * - Keyboard navigation support
 *
 * @see <a href="https://htmx.org/docs/">HTMX Documentation</a>
 * @see <a href="https://www.w3.org/WAI/WCAG22/quickref/">WCAG 2.2 Quick Reference</a>
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = "0.0.0.0" // Required for Codespaces

    embeddedServer(Netty, port = port, host = host) {
        configureLogging()
        configureTemplating()
        configureSessions()
        configureStatusPages()
        configureRouting()
    }.start(wait = true)
}

/**
 * Configure request logging for development and debugging.
 */
fun Application.configureLogging() {
    install(CallLogging) {
        // Log format: METHOD /path - status (duration ms)
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "$method $path - $status"
        }
    }
}

/**
 * Configure Pebble templating engine.
 * Templates are in src/main/resources/templates/
 *
 * **Template conventions**:
 * - Partials start with underscore: `_list.peb`, `_item.peb`
 * - Layouts in `_layout/` subdirectory
 * - Full pages in root or feature subdirectories
 */
fun Application.configureTemplating() {
    val pebbleEngine =
        PebbleEngine
            .Builder()
            .loader(
                io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                    prefix = "templates/"
                },
            ).autoEscaping(true) // XSS protection via auto-escaping
            .cacheActive(false) // Disable cache in dev for hot reload
            .strictVariables(false) // Allow undefined variables (fail gracefully)
            .build()

    environment.monitor.subscribe(ApplicationStarted) { app ->
        app.log.info("✓ Pebble templates loaded from resources/templates/")
        app.log.info("✓ Server running on configured port")
    }

    // Make Pebble available to all routes
    attributes.put(PebbleEngineKey, pebbleEngine)
}

/**
 * AttributeKey for storing Pebble engine instance.
 */
val PebbleEngineKey = AttributeKey<PebbleEngine>("PebbleEngine")

/**
 * Render a Pebble template to HTML string.
 *
 * **Usage**:
 * ```kotlin
 * val html = call.renderTemplate("tasks/index.peb", mapOf("tasks" to taskList))
 * call.respondText(html, ContentType.Text.Html)
 * ```
 *
 * **Context enrichment**:
 * - Automatically adds `sessionId` from session
 * - Automatically adds `isHtmx` flag (true if HX-Request header present)
 *
 * @param templateName Template path relative to resources/templates/
 * @param context Data to pass to template (map of variable names to values)
 * @return Rendered HTML string
 */
suspend fun ApplicationCall.renderTemplate(
    templateName: String,
    context: Map<String, Any> = emptyMap(),
): String {
    val engine = application.attributes[PebbleEngineKey]
    val writer = StringWriter()
    val template = engine.getTemplate(templateName)

    // Add global context available to all templates
    val sessionData = sessions.get<SessionData>()
    val enrichedContext =
        context +
            mapOf(
                "sessionId" to (sessionData?.id ?: "anonymous"),
                "isHtmx" to isHtmxRequest(),
            )

    template.evaluate(writer, enrichedContext)
    return writer.toString()
}

/**
 * Check if request is from HTMX (progressive enhancement mode).
 *
 * **HTMX detection**:
 * - HTMX adds `HX-Request: true` header to all AJAX requests
 * - Use this to return fragments vs full pages
 *
 * **Pattern**:
 * ```kotlin
 * if (call.isHtmxRequest()) {
 *     // Return partial HTML fragment
 *     call.respondText(render("tasks/_list.peb"))
 * } else {
 *     // Traditional redirect (POST-Redirect-GET)
 *     call.respondRedirect("/tasks")
 * }
 * ```
 */
fun ApplicationCall.isHtmxRequest(): Boolean = request.headers["HX-Request"] == "true"

/**
 * Configure session handling (privacy-safe anonymous IDs).
 *
 * **Privacy notes**:
 * - Session IDs are random, anonymous (no PII)
 * - Used for metrics correlation only
 * - Cookie is HttpOnly, SameSite=Strict
 * - No tracking across devices/browsers
 */
fun Application.configureSessions() {
    install(Sessions) {
        cookie<SessionData>("COMP2850_SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Strict"
            // No maxAge = session cookie (deleted when browser closes)
        }
    }
}

/**
 * HTML template for 404 error page.
 * Extracted as constant for code organization (detekt LongMethod compliance).
 */
private const val ERROR_404_HTML =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>404 - Page Not Found</title>
        <style>
            body {
                background-color: #000;
                color: #fff;
                font-family: system-ui, -apple-system, sans-serif;
                display: flex;
                align-items: center;
                justify-content: center;
                min-height: 100vh;
                margin: 0;
                padding: 1rem;
            }
            main {
                text-align: center;
            }
            h1 {
                font-size: 3rem;
                margin-bottom: 1rem;
            }
            p {
                font-size: 1.125rem;
                margin: 0.5rem 0;
            }
            a {
                color: #4A90E2;
                text-decoration: none;
            }
            a:hover {
                text-decoration: underline;
            }
            a:focus {
                outline: 3px solid #4A90E2;
                outline-offset: 2px;
            }
        </style>
    </head>
    <body>
        <main>
            <h1>404 - Page Not Found</h1>
            <p>The page you're looking for doesn't exist.</p>
            <p><a href="/tasks">Go to Task List</a></p>
        </main>
    </body>
    </html>
    """

/**
 * Configure custom error pages for better UX.
 *
 * **Error handling**:
 * - 404 Not Found: friendly error page with navigation
 * - 500 Internal Server Error: generic error page (no details exposed)
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(ERROR_404_HTML.trimIndent(), ContentType.Text.Html, status)
        }
    }
}

/**
 * Configure application routing.
 *
 * **Route organization**:
 * - Static files: `/static/...` (CSS, JS, HTMX)
 * - Health check: `/health`
 * - Task CRUD: `/tasks`, `/tasks/{id}`, etc.
 */
fun Application.configureRouting() {
    routing {
        intercept(ApplicationCallPipeline.Setup) {
            call.sessions.get<SessionData>() ?: call.sessions.set(SessionData())
            if (call.attributes.getOrNull(ReqIdKey) == null) {
                call.attributes.put(ReqIdKey, generateRequestId())
            }
            proceed()
        }

        // Static files (CSS, JS, HTMX library)
        staticResources("/static", "static")

        // Health check endpoint (for monitoring)
        configureHealthCheck()

        // Task management routes (main feature)
        configureTaskRoutes()
        // configureEditRoutes() // Week 7 feature
    }
}
