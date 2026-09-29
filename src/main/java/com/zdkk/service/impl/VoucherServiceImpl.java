package com.zdkk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.dto.SeckillVoucherDTO;
import com.zdkk.entity.Voucher;
import com.zdkk.mapper.VoucherMapper;
import com.zdkk.entity.SeckillVoucher;
import com.zdkk.service.ISeckillVoucherService;
import com.zdkk.service.IVoucherService;
import com.zdkk.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 保存秒杀优惠券到redis中
        String key = RedisConstants.SECKILL_KEY + seckillVoucher.getVoucherId();
        SeckillVoucherDTO value = new SeckillVoucherDTO();
        value.setStock(seckillVoucher.getStock());
        value.setBeginTime(seckillVoucher.getBeginTime().atZone(ZoneId.systemDefault()).toEpochSecond());
        value.setEndTime(seckillVoucher.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond());
        Map<String, Object> objectMap = BeanUtil.beanToMap(value, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(key, objectMap);
    }
}
