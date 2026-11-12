// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api

import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.CoercionAction
import com.fasterxml.jackson.databind.cfg.CoercionInputShape
import com.fasterxml.jackson.databind.type.LogicalType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import wiki.liberty.application.PageActor
import wiki.liberty.domain.MAX_COMPARISON_OUTPUT_BYTES
import wiki.liberty.domain.PageError
import wiki.liberty.domain.PageFailure
import java.security.MessageDigest
import java.util.UUID

@ConfigurationProperties("wiki.http")
data class PageHttpSettings(
    val operatorToken: String = "",
    val licenseUrl: String = "",
    val licenseTitle: String = "",
)

@Component
class PageHttp(private val settings: PageHttpSettings, private val json: ObjectMapper) {
    fun actor(request: HttpServletRequest): PageActor {
        val header = request.getHeader("Authorization") ?: return PageActor.ANONYMOUS
        if (settings.operatorToken.isBlank() || !header.startsWith("Bearer ", ignoreCase = true) ||
            !MessageDigest.isEqual(header.substring(7).toByteArray(), settings.operatorToken.toByteArray())
        ) {
            throw InvalidCredentials()
        }
        return PageActor.OPERATOR
    }

    fun comparisonResponse(value: Any): ResponseEntity<ByteArray> {
        // The engine bounds the input and diff before formatting. The wire limit also covers
        // adapter-specific output such as REST sections, Action markup and JSON escaping.
        val encoded = json.writeValueAsBytes(value)
        if (encoded.size > MAX_COMPARISON_OUTPUT_BYTES) throw PageFailure(PageError.COMPARISON_TOO_LARGE)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(encoded)
    }
}

class InvalidCredentials : RuntimeException()

fun HttpServletRequest.requestId(): UUID = getAttribute("wiki.requestId") as UUID

@Component
class PageHeaders : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val id = UUID.randomUUID()
        request.setAttribute("wiki.requestId", id)
        response.setHeader("X-Request-Id", id.toString())
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Content-Security-Policy", "default-src 'self'; base-uri 'none'; frame-ancestors 'none'")
        if (request.servletPath.startsWith("/api/") || request.servletPath.startsWith("/w/")) {
            response.setHeader("Cache-Control", "no-store")
        }
        chain.doFilter(request, response)
    }
}

@Configuration
class PageJsonConfiguration {
    @Bean
    fun jsonLimits() = Jackson2ObjectMapperBuilderCustomizer { builder ->
        builder.postConfigurer { mapper ->
            mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            mapper.factory.setStreamReadConstraints(
                StreamReadConstraints.builder()
                    .maxDocumentLength(7_340_032)
                    .maxStringLength(1_048_576)
                    .maxNestingDepth(16)
                    .build(),
            )
        }
    }
}
