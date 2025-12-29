package com.lycosoft.ratelimit.example.webflux.router;

import com.lycosoft.ratelimit.example.webflux.handler.ApiHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Functional routing configuration.
 * 
 * <p>Rate limits are applied via the RateLimitWebFilter.
 */
@Configuration
public class FunctionalRoutes {
    
    @Bean
    public RouterFunction<ServerResponse> apiRoutes(ApiHandler handler) {
        return route()
            .GET("/reactive/data", handler::getData)
            .GET("/reactive/stream", handler::getStream)
            .POST("/reactive/process", handler::processData)
            .build();
    }
}
