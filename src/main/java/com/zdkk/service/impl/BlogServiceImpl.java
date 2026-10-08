package com.zdkk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.dto.ScrollResult;
import com.zdkk.dto.UserDTO;
import com.zdkk.entity.Blog;
import com.zdkk.entity.Follow;
import com.zdkk.entity.User;
import com.zdkk.mapper.BlogMapper;
import com.zdkk.service.IBlogService;
import com.zdkk.service.IFollowService;
import com.zdkk.service.IUserService;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.SystemConstants;
import com.zdkk.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = query().eq("id", id).one();
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return;
        }
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return;
        }
        Long userId = userDTO.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        records.forEach(this::isBlogLiked);
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).toList();
        Map<Long, UserDTO> userDTOMap = userService.query().in("id", ids).list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toMap(UserDTO::getId, user -> user));
        List<UserDTO> userDTOS = ids.stream().map(userDTOMap::get).toList();
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long userId) {
        Page<Blog> page = query().eq("user_id", userId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean flag = save(blog);
        if (!flag) {
            return Result.fail("博客保存失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        if (follows != null && !follows.isEmpty()) {
            for (Follow follow : follows) {
                Long userId = follow.getUserId();
                stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + userId, blog.getId().toString(), System.currentTimeMillis());
            }
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        // 1. 滚动分页查询
        // 1.1 如果 lastId 为 0，则从最新数据开始查询
        // 1.2 如果 lastId 不为 0，则从 lastId 之后的数据开始查询，offset 含义为需跳过的数据数量
        // ZRANGE key min max BYSCORE REV WITHSCORES LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, lastId, offset, 20);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int cnt = 1;

        // 2. 解析数据: 获取本次查询的最小时间及其blog数量，作为下一次查询的参数，用于跳过重复数据
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (time == minTime) {
                cnt++;
            } else {
                minTime = time;
                cnt = 1;
            }
        }
        cnt = minTime == lastId ? cnt : cnt + offset;
        Map<Long, Blog> map = query().in("id", ids).list()
                .stream().map(blog -> BeanUtil.copyProperties(blog, Blog.class))
                .collect(Collectors.toMap(Blog::getId, blog -> blog));
        List<Blog> list = ids.stream().map(map::get).toList();
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(list);
        scrollResult.setOffset(cnt);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
