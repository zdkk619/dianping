package com.zdkk.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zdkk.dto.LoginFormDTO;
import com.zdkk.dto.Result;
import com.zdkk.entity.User;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logout(String token);
}
