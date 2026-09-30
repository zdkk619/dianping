package com.zdkk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zdkk.utils.RedisConstants.*;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
//        session.setAttribute("code", code);
//        session.setAttribute("phone", phone);

        // 保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, Expiration.from(LOGIN_CODE_TTL, TimeUnit.MINUTES));
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
            if (!loginForm.getCode().equals(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone))) {
                return Result.fail("验证码不正确");
            }
            if (user == null) {
                user = createUserWithPhone(phone);
            }
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
//        session.setAttribute("user", userDTO);
        String token = UUID.randomUUID().toString();
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, Expiration.from(LOGIN_USER_TTL, TimeUnit.MINUTES));

        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
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
