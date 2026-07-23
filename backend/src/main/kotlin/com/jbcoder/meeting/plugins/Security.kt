package com.jbcoder.meeting.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jbcoder.meeting.configuration.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureSecurity(config: AppConfig) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        
        // Use anyHost() only for development, production should specify exact hosts.
        // For Phase 2, we are in local development mode.
        anyHost() 
    }
    
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "LiveKit Meeting Host"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer("jbcoder-meeting")
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("role").asString() == "HOST") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
