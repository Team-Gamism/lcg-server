package com.lcg.global.config

import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import team.themoment.datagsm.sdk.oauth.http.OkHttpClientImpl
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lcg.datagsm", name = ["enabled"], havingValue = "true")
class DataGsmConfig {
    @Bean(destroyMethod = "close")
    fun dataGsmOAuthClient(properties: DataGsmProperties): DataGsmOAuthClient {
        properties.validate()
        val http = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(3))
            .callTimeout(Duration.ofSeconds(5))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        return DataGsmOAuthClient.builder(properties.clientId, properties.clientSecret)
            .httpClient(OkHttpClientImpl(http))
            .build()
    }
}
