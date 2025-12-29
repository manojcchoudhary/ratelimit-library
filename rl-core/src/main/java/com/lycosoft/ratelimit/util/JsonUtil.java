package com.lycosoft.ratelimit.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.time.Instant;

public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class,
                    (JsonSerializer<Instant>) (src, type, ctx) ->
                            new JsonPrimitive(src.toEpochMilli())
            )
            .create();
    ;

    private JsonUtil() {
    }

    public static Gson gson() {
        return GSON;
    }
}
