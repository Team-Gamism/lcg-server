package com.lcg.global.redis

import org.springframework.stereotype.Component

@Component
class RedisKeys(private val properties: EnvironmentProperties) {
    fun cache(name: String, id: String): String = key("cache", name, id)

    fun sessionNamespace(): String = "lcg:${properties.environment}:session:v1"

    private fun key(namespace: String, name: String, id: String): String {
        require(name.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid Redis key name" }
        require(id.isNotBlank()) { "Redis key id must not be blank" }
        return "lcg:${properties.environment}:$namespace:v1:$name:$id"
    }
}
