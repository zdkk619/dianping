package com.zdkk.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zdkk.dto.Result;
import com.zdkk.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);
}
