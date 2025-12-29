package com.lycosoft.ratelimit.example.webflux.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Handler for functional routes.
 */
@Component
public class ApiHandler {
    
    public Mono<ServerResponse> getData(ServerRequest request) {
        Map<String, Object> response = Map.of(
            "message", "Reactive data access",
            "timestamp", LocalDateTime.now().toString(),
            "data", "Sample reactive dataset"
        );
        
        return ServerResponse.ok().bodyValue(response);
    }
    
    public Mono<ServerResponse> getStream(ServerRequest request) {
        Flux<Map<String, Object>> stream = Flux.interval(Duration.ofSeconds(1))
            .take(10)
            .map(i -> Map.of(
                "sequence", i,
                "timestamp", LocalDateTime.now().toString(),
                "data", "Streaming event #" + i
            ));
        
        return ServerResponse.ok().body(stream, Map.class);
    }
    
    public Mono<ServerResponse> processData(ServerRequest request) {
        return request.bodyToMono(Map.class)
            .flatMap(body -> {
                Map<String, Object> response = Map.of(
                    "message", "Data processed",
                    "timestamp", LocalDateTime.now().toString(),
                    "received", body
                );
                return ServerResponse.ok().bodyValue(response);
            });
    }
}
