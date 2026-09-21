package com.lcg.global.config

import jakarta.servlet.DispatcherType
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ProblemDetailFactory
import com.lcg.global.filter.RequestIdFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig(
    private val properties: SecurityProperties,
    private val problemDetailFactory: ProblemDetailFactory,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/system/ping",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                ).permitAll()
                if (properties.publicDocs) {
                    it.requestMatchers(
                        HttpMethod.GET,
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                }
                it.requestMatchers("/api/**").authenticated()
                it.anyRequest().denyAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, _ ->
                    problemDetailFactory.write(request, response, ApiErrorCode.UNAUTHENTICATED)
                }
                it.accessDeniedHandler { request, response, _ ->
                    problemDetailFactory.write(request, response, ApiErrorCode.FORBIDDEN)
                }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = properties.allowedOrigins.filter(String::isNotBlank)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Content-Type", "X-CSRF-TOKEN")
            exposedHeaders = listOf(RequestIdFilter.HEADER)
            allowCredentials = true
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }
}
