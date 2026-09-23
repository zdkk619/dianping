package com.zdkk.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.entity.User;
import com.zdkk.mapper.UserMapper;
import com.zdkk.service.IUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
