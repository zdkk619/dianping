package com.zdkk;

import com.zdkk.dto.Result;
import com.zdkk.entity.Shop;
import com.zdkk.service.IShopService;
import com.zdkk.service.impl.ShopServiceImpl;
import com.zdkk.utils.CacheClient;
import com.zdkk.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class DianPingApplicationTests {

    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopServiceImpl;
    @Test
    public void testLogicExpire() {
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1, shopServiceImpl.getById(1L), 20L, TimeUnit.SECONDS);
    }
}
