package com.zdkk.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.entity.VoucherOrder;
import com.zdkk.mapper.VoucherOrderMapper;
import com.zdkk.service.ISeckillVoucherService;
import com.zdkk.service.IVoucherOrderService;
import com.zdkk.service.IVoucherService;
import com.zdkk.service.VoucherOrderTXService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class VoucherOrderTXServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderTXService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;


    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户 {} 已经购买过该优惠券", userId);
            return;
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("优惠券库存不足");
            return;
        }
        save(voucherOrder);
    }
}
