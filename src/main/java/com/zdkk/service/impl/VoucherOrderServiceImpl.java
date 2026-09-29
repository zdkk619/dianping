package com.zdkk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.VoucherOrder;
import com.zdkk.mapper.VoucherOrderMapper;
import com.zdkk.service.IVoucherOrderService;
import com.zdkk.service.VoucherOrderTXService;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.RedisIdWorker;
import com.zdkk.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

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
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private VoucherOrderTXService voucherOrderTXService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seckill-order-consumer");
        t.setDaemon(false);
        return t;
    });

    private static final String GROUP = "g1";
    private static final String CONSUMER = "c-" + UUID.randomUUID();
    private static final int MAX_RETRY = 3;
    private volatile boolean running = true;     // 停机标志

    private final Map<String, Integer> retryCount = new ConcurrentHashMap<>(); // 记录重试次数

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<Long>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @PreDestroy
    public void destroy() {                      // 优雅停机
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    @PostConstruct
    public void init() {
        // 确保消费者组存在
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    RedisConstants.STREAM_ORDER_KEY, GROUP);
        } catch (Exception e) {
            log.info("消费者组已存在或创建失败: {}", e.getMessage());
        }
        executorService.submit(() -> {
            int idleCount = 0;
            while (running) {
                try {
                    // 获取队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP, CONSUMER),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(RedisConstants.STREAM_ORDER_KEY, ReadOffset.lastConsumed())
                    );
                    if(list == null || list.isEmpty()) {
                        idleCount++;
                        if (idleCount % 5 == 0) {   // 每 5 次空闲跑一次 pending（约 10 秒）
                            handlePendingList();
                        }
                        // 未读到订单信息
                        continue;
                    }
                    handleRecord(list.getFirst());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        });
    }

    private void handlePendingList() {
        try {
            // 获取pending list中的订单信息
            List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(10),
                    StreamOffset.create(RedisConstants.STREAM_ORDER_KEY, ReadOffset.from("0"))
            );
            if(list == null || list.isEmpty()) {
                // 未读到订单信息
                return;
            }
            for (MapRecord<String, Object, Object> record : list) {
                handleRecord(record);
            }
        } catch (Exception e) {
            log.error("读取pending订单列表异常", e);
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record) {
        String id = record.getId().getValue();
        try {
            Map<Object, Object> map = record.getValue();
            VoucherOrder voucherOrder = BeanUtil.toBean(map, VoucherOrder.class);
            voucherOrderTXService.createVoucherOrder(voucherOrder);
            stringRedisTemplate.opsForStream().acknowledge(
                    RedisConstants.STREAM_ORDER_KEY, GROUP, record.getId());
            retryCount.remove(id);
        } catch (Exception e) {
            int count = retryCount.getOrDefault(id, 0) + 1;
            if (count >= MAX_RETRY) {
                log.error("消息 {} 重试 {} 次仍失败，转入死信", id, count);
                try {
                    stringRedisTemplate.opsForStream()
                            .add(RedisConstants.STREAM_ORDER_DLQ_KEY, record.getValue());
                    stringRedisTemplate.opsForStream().acknowledge(
                            RedisConstants.STREAM_ORDER_KEY, GROUP, record.getId());
                    retryCount.remove(id);
                } catch (Exception ex) {
                    log.error("消息 {} 转入死信队列失败", id, ex);
                    retryCount.remove(id);
                }
            } else {
                retryCount.put(id, count);
                log.error("消息 {} 处理失败，第 {} 次", id, count, e);
            }
        }
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 判断当前用户是否能参与秒杀
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId(RedisConstants.VOUCHER_ORDER_KEY);
        long executeResult = stringRedisTemplate.execute(SECKILL_SCRIPT,
                List.of(RedisConstants.SECKILL_KEY + voucherId,
                        RedisConstants.SECKILL_KEY + voucherId + ":" + RedisConstants.USER_KEY),
                userId.toString(),
                voucherId.toString(),
                String.valueOf(orderId),
                String.valueOf(now.atZone(ZoneId.systemDefault()).toEpochSecond()));
        if (executeResult != 0) {
            if (executeResult == 1) {
                return Result.fail("库存不足");
            } else if (executeResult == 2) {
                return Result.fail("不在秒杀时间内");
            } else if (executeResult == 3) {
                return Result.fail("用户已购买过该优惠券");
            } else {
                return Result.fail("未知错误");
            }
        }

        return Result.ok(orderId);
    }
}
