package com.zdkk.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.SeckillVoucher;
import com.zdkk.entity.VoucherOrder;
import com.zdkk.mapper.VoucherOrderMapper;
import com.zdkk.service.ISeckillVoucherService;
import com.zdkk.service.IVoucherOrderService;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.RedisIdWorker;
import com.zdkk.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.zdkk.utils.RedisConstants.LOCK_VOUCHER_ORDER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private RedissonClient redissonClient;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getStock() <= 0) {
            return Result.fail("优惠券已售罄");
        }
        if (now.isBefore(voucher.getBeginTime()) || now.isAfter(voucher.getEndTime())) {
            return Result.fail("优惠券抢购未开始或已结束");
        }

        Long userId = UserHolder.getUser().getId();
        String lockKey = LOCK_VOUCHER_ORDER_KEY + voucherId + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = lock.tryLock();
        if (!isLocked) {
            return Result.fail("请勿重复下单");
        }
        try {
            return  ((IVoucherOrderService) AopContext.currentProxy()).createVoucherOrder(voucherId, userId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        Long count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("用户已购买过该优惠券");
        }
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!update) {
            return Result.fail("优惠券库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdWorker.nextId(RedisConstants.VOUCHER_ORDER_KEY);
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(id);
    }
}
