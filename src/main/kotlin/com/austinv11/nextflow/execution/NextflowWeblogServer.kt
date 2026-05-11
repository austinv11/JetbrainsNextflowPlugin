package com.austinv11.nextflow.execution.console

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class NextflowWeblogServer {
    private val logger = Logger.getInstance(NextflowWeblogServer::class.java)
    private var server: HttpServer? = null
    private val objectMapper = ObjectMapper()

    var port: Int = 0
        private set

    var onEventReceived: ((JsonNode) -> Unit)? = null

    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            port = server?.address?.port ?: 0
            server?.createContext("/", object : HttpHandler {
                override fun handle(exchange: HttpExchange) {
                    if (exchange.requestMethod == "POST") {
                        try {
                            val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
                            val json = objectMapper.readTree(requestBody)
                            onEventReceived?.invoke(json)
                        } catch (e: Exception) {
                            logger.error("Failed to parse weblog event", e)
                        }

                        val response = "OK"
                        exchange.sendResponseHeaders(200, response.length.toLong())
                        exchange.responseBody.use { it.write(response.toByteArray()) }
                    } else {
                        exchange.sendResponseHeaders(405, -1) // Method Not Allowed
                    }
                }
            })
            server?.executor = Executors.newFixedThreadPool(1)
            server?.start()
            logger.info("Nextflow Weblog Server started on port $port")
        } catch (e: Exception) {
            logger.error("Failed to start Nextflow Weblog Server", e)
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}
