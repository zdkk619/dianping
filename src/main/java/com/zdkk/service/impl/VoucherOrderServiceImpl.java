package com.zdkk.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.SeckillVoucher;
import com.zdkk.entity.VoucherOrder;
import com.zdkk.mapper.VoucherOrderMapper;
import com.zdkk.service.ISeckillVoucherService;
import com.zdkk.service.IVoucherOrderService;
import com.zdkk.service.VoucherOrderTXService;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.RedisIdWorker;
import com.zdkk.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private VoucherOrderTXService voucherOrderTXService;

    private BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<>(1024 * 1024);
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        executorService.submit(() -> {
            while (true) {
                // 获取队列中的订单信息
                VoucherOrder voucherOrder = voucherOrderQueue.take();
                // 创建锁对象
                RLock lock = redissonClient.getLock(LOCK_VOUCHER_ORDER_KEY + voucherOrder.getUserId());
                // 获取锁
                boolean isLocked = lock.tryLock();
                if (!isLocked) {
                    log.error("请勿重复下单 {}", voucherOrder.getUserId());
                    continue;
                }
                try {
                    voucherOrderTXService.createVoucherOrder(voucherOrder);
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            }
        });
    }

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>();

    {
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 判断当前用户是否能参与秒杀
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserHolder.getUser().getId();
        long executeResult = stringRedisTemplate.execute(script,
                List.of(RedisConstants.SECKILL_KEY + voucherId,
                        RedisConstants.SECKILL_KEY + voucherId + ":" + RedisConstants.USER_KEY),
                userId.toString(),
                String.valueOf(now.atZone(ZoneId.systemDefault()).toEpochSecond()));
        if (executeResult != 0) {
            if (executeResult == 1) {
                return Result.fail("库存不足");
            } else if (executeResult == 2) {
                return Result.fail("不在秒杀时间内");
            } else if (executeResult == 3) {
                return Result.fail("用户已购买过该优惠券");
            }
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdWorker.nextId(RedisConstants.VOUCHER_ORDER_KEY);
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrderQueue.add(voucherOrder);
        return Result.ok(id);
    }
}
