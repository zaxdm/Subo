package com.peru.expresdm.backend

import com.peru.expresdm.backend.ai.Gemini
import com.peru.expresdm.backend.api.Services
import com.peru.expresdm.backend.api.module
import com.peru.expresdm.backend.auth.Tokens
import com.peru.expresdm.backend.db.Database
import com.peru.expresdm.backend.store.AlertStore
import com.peru.expresdm.backend.store.Catalog
import com.peru.expresdm.backend.store.FeedbackStore
import com.peru.expresdm.backend.store.RouteStore
import com.peru.expresdm.backend.store.UserStore
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("main")
    val config = Config.load()

    val database = Database.connect(config)
    database.migrate()

    val routes = RouteStore(database)
    routes.seedIfEmpty(Catalog.ROUTES)

    val services = Services(
        config = config,
        database = database,
        users = UserStore(database),
        alerts = AlertStore(database),
        routes = routes,
        feedback = FeedbackStore(database),
        tokens = Tokens(config.jwtSecret),
        gemini = Gemini(config.geminiApiKey)
    )

    if (!config.isAssistantEnabled) {
        log.warn("Sin GEMINI_API_KEY: el asistente queda desactivado y la moderación deja pasar los textos")
    }

    // 0.0.0.0: también atiende al teléfono por la red local, no solo a esta PC.
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(services)
    }.start(wait = true)
}
