package com.lycosoft.ratelimit.storage.redis;

import java.util.Set;

public final class LuaScripts {
    public static final String TOKEN_BUCKET = "token_bucket_consume.lua";
    public static final String SLIDING_WINDOW = "sliding_window_consume.lua";

    private LuaScripts() {} // Prevent instantiation

    public static Set<String> WHITELISTED_SCRIPTS = Set.of(TOKEN_BUCKET, SLIDING_WINDOW);

}
