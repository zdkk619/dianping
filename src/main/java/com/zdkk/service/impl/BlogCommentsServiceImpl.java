package com.zdkk.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.entity.BlogComments;
import com.zdkk.mapper.BlogCommentsMapper;
import com.zdkk.service.IBlogCommentsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
