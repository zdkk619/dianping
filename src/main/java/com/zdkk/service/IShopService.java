package com.zdkk.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zdkk.dto.Result;
import com.zdkk.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
public interface IShopService extends IService<Shop> {
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商铺信息
     * @param shop
     */
    Result updateShop(Shop shop);
}
