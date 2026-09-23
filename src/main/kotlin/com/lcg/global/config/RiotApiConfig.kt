package com.lcg.global.config

import com.lcg.domain.riot.client.RiotHttpClient
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lcg.riot", name = ["enabled"], havingValue = "true")
class RiotApiConfig {
    @Bean(destroyMethod = "close")
    fun riotApiClient(properties: RiotApiProperties): RiotHttpClient {
        properties.validate()
        val http = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(3))
            .callTimeout(Duration.ofSeconds(5))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        return RiotHttpClient(http, properties.apiKey)
    }
}
