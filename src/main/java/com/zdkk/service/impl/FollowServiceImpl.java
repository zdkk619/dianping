package com.zdkk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.dto.UserDTO;
import com.zdkk.entity.Follow;
import com.zdkk.mapper.FollowMapper;
import com.zdkk.service.IFollowService;
import com.zdkk.service.IUserService;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean flag) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.USER_FOLLOW_KEY + userId;
        if (flag) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            stringRedisTemplate.opsForSet().add(key, id.toString());
            save(follow);
        } else {
            stringRedisTemplate.opsForSet().remove(key, id.toString());
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.USER_FOLLOW_KEY + userId;
        Boolean isFollowed = stringRedisTemplate.opsForSet().isMember(key, id.toString());
//        Follow follow = getOne(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
        return Result.ok(BooleanUtil.isTrue(isFollowed));
    }

    @Override
    public Result commonFollowers(Long id) {
        Long userId = UserHolder.getUser().getId();
        String myKey = RedisConstants.USER_FOLLOW_KEY + userId;
        String otherKey = RedisConstants.USER_FOLLOW_KEY + id;
        Set<String> commonMembers = stringRedisTemplate.opsForSet().intersect(myKey, otherKey);
        if (commonMembers == null || commonMembers.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> list = commonMembers.stream().map(Long::valueOf).toList();
        List<UserDTO> userDTOS = userService.listByIds(list)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();

        return Result.ok(userDTOS);
    }
}
