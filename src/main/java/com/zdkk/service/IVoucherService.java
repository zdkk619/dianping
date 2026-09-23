package com.zdkk.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zdkk.dto.Result;
import com.zdkk.entity.Voucher;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
