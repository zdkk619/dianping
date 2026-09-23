package com.zdkk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdkk.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
