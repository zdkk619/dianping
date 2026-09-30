package com.zdkk.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zdkk.dto.Result;
import com.zdkk.entity.Follow;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long id, Boolean flag);

    Result isFollowed(Long id);

    Result commonFollowers(Long id);
}
