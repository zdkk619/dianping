package com.zdkk.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;

    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType";
    public static final Long CACHE_SHOP_TYPE_TTL = 30L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String VOUCHER_ORDER_KEY = "voucher:order";

    public static final String LOCK_VOUCHER_ORDER_KEY = "lock:voucher:order:";

    public static final String SECKILL_KEY = "seckill:";
    public static final String USER_KEY = "user";
    public static final String STREAM_ORDER_KEY = "stream:order";
    public static final String STREAM_ORDER_DLQ_KEY = "stream.order.dlq";  // 死信队列

    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String USER_FOLLOW_KEY = "user:follows:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
