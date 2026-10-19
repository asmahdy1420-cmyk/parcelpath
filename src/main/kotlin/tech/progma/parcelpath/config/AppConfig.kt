package tech.progma.parcelpath.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AppConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("ParcelPath API")
                .version("0.1.0")
                .description("Shipment tracking with an explicit state machine and idempotent carrier webhooks.")
                .license(License().name("MIT")),
        )
}
