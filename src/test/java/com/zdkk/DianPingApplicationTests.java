package com.zdkk;

import com.zdkk.service.IShopService;
import com.zdkk.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopServiceImpl;
    @Test
    public void testLogicExpire() {
        shopServiceImpl.saveShopToRedis(1L, 20L);
    }
}
