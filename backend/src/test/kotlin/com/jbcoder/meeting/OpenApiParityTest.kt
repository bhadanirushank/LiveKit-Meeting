package com.jbcoder.meeting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import io.ktor.server.application.*

class OpenApiParityTest {

    companion object {
        private lateinit var config: AppConfig
        
        @JvmStatic
        @BeforeAll
        fun setup() {
            TestSecrets.setupTestProperties()
            config = AppConfig.load()
            DatabaseConfig.init(config)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            DatabaseConfig.close()
        }
    }

    private fun extractRoutes(route: Route, currentPath: String, result: MutableList<Pair<String, String>>) {
        var path = currentPath
        if (route.selector is PathSegmentConstantRouteSelector) {
            path += "/" + (route.selector as PathSegmentConstantRouteSelector).value
        } else if (route.selector is PathSegmentParameterRouteSelector) {
            path += "/{" + (route.selector as PathSegmentParameterRouteSelector).name + "}"
        }

        if (route.selector is HttpMethodRouteSelector) {
            val method = (route.selector as HttpMethodRouteSelector).method.value
            result.add(Pair(path.replace(Regex("/+"), "/"), method))
        }

        for (child in route.children) {
            extractRoutes(child, path, result)
        }
    }

    @Test
    fun `OpenAPI parity with Ktor routes`() = testApplication {
        application {
            module(config)
            
            // Wait until routing is established
            val routing = this.plugin(Routing)
            val ktorRoutes = mutableListOf<Pair<String, String>>()
            extractRoutes(routing, "", ktorRoutes)
            
            val normalizedKtorRoutes = ktorRoutes
                .filter { !it.first.contains("/metrics") && !it.first.startsWith("/health") }
                .map { Pair(it.first.removePrefix("/api/v1"), it.second) }
                .toSet()

            val mapper = ObjectMapper(YAMLFactory())
            val openApiFile = File("../docs/openapi.yaml")
            val rootNode = mapper.readTree(openApiFile)
            val pathsNode = rootNode.get("paths")

            val openApiRoutes = mutableSetOf<Pair<String, String>>()
            pathsNode.fieldNames().forEach { path ->
                pathsNode.get(path).fieldNames().forEach { method ->
                    openApiRoutes.add(Pair(path, method.uppercase()))
                }
            }

            val missingInOpenApi = normalizedKtorRoutes - openApiRoutes
            val missingInKtor = openApiRoutes - normalizedKtorRoutes

            println("Implemented routes missing from OpenAPI: ${missingInOpenApi.size}")
            if (missingInOpenApi.isNotEmpty()) {
                println("Missing in OpenAPI: $missingInOpenApi")
            }

            println("OpenAPI paths missing from implementation: ${missingInKtor.size}")
            if (missingInKtor.isNotEmpty()) {
                println("Missing in Ktor: $missingInKtor")
            }

            assertEquals(0, missingInOpenApi.size, "Found routes in implementation missing from OpenAPI")
            assertEquals(0, missingInKtor.size, "Found routes in OpenAPI missing from implementation")
        }
    }
}
