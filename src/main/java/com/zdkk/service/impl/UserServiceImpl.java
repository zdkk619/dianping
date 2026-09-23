package com.zdkk.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.LoginFormDTO;
import com.zdkk.dto.Result;
import com.zdkk.dto.UserDTO;
import com.zdkk.entity.User;
import com.zdkk.mapper.UserMapper;
import com.zdkk.service.IUserService;
import com.zdkk.utils.RegexUtils;
import com.zdkk.utils.SystemConstants;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        log.info(phone);
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }

        // 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码到会话
        session.setAttribute("code", code);
        session.setAttribute("phone", phone);

        // 发送验证码
        // TODO: 发送验证码
        log.info("发送验证码：{}给{}", code, phone);

        // 返回结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if (loginForm == null) {
            return Result.fail("参数不能为空");
        }
        if (loginForm.getPhone() == null || loginForm.getPhone().isEmpty()) {
            return Result.fail("手机号不能为空");
        }
        if (loginForm.getCode() == null && loginForm.getPassword() == null) {
            return Result.fail("密码或验证码不能为空");
        }

        String phone = loginForm.getPhone();
        User user = query().eq("phone", phone).one();

        if (loginForm.getPassword() != null && !loginForm.getPassword().isEmpty()) {
            // 校验密码
            if (user == null) {
                return Result.fail("用户不存在");
            }
            if (!user.getPassword().equals(loginForm.getPassword()))
                return Result.fail("密码错误");
        } else {
            // 校验验证码
            if (loginForm.getCode() == null || loginForm.getCode().isEmpty()) {
                return Result.fail("验证码不能为空");
            }
            if (!loginForm.getCode().equals(session.getAttribute("code"))) {
                return Result.fail("验证码不正确");
            }
            if (!loginForm.getPhone().equals(session.getAttribute("phone"))) {
                return Result.fail("手机号与验证码不匹配");
            }
            if (user == null) {
                user = createUserWithPhone(phone);
            }
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setIcon(user.getIcon());
        userDTO.setNickName(user.getNickName());
        userDTO.setId(user.getId());
        session.setAttribute("user", userDTO);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        log.info("创建用户：{}", user);
        save(user);
        return user;
    }


}
