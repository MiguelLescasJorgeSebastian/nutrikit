package dev.jorgemiguel.nutrikit.shared.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Activa {@code @Cacheable}. Las cachés y su vigencia se definen en {@code spring.cache.*}. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfig {
}
