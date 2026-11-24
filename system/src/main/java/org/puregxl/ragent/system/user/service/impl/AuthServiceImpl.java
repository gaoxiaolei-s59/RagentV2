package org.puregxl.ragent.system.user.service.impl;

import org.springframework.context.annotation.Profile;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.system.user.entity.UserDO;
import org.puregxl.ragent.system.user.mapper.UserMapper;
import org.puregxl.ragent.system.user.dto.request.LoginRequest;
import org.puregxl.ragent.system.user.dto.response.LoginResponse;
import org.puregxl.ragent.system.user.service.AuthService;
import org.puregxl.ragent.framework.exception.ClientException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 校验登录凭据并管理 Sa-Token 会话。
 */
@Service
@RequiredArgsConstructor
@Profile("mysql")
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    /**
     * 用户登录
     * @param request 用户名和密码
     * @return 登录凭证及用户概要
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        String userName = request.getUserName();
        String password = request.getPassword();

        if (StrUtil.isBlank(userName) || StrUtil.isBlank(password)) {
            throw new ClientException("用户名或者密码不能为空");
        }
        UserDO user = findByUsername(userName);
        if (user == null || !Objects.equals(password, user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }

        if (user.getId() == null) {
            throw new ClientException("用户信息异常");
        }

        StpUtil.login(user.getId());
        saveUserSession(user);

        LoginResponse response = new LoginResponse();
        response.setToken(StpUtil.getTokenValue());
        response.setUserId(user.getId());
        response.setAvatar(user.getAvatar());
        response.setRole(user.getRole());
        return response;
    }

    /**
     * 执行用户登出。
     * 这里显式调用 Sa-Token 的登出逻辑，统一清理当前登录会话，
     * 避免前端仅删除 localStorage 后，浏览器残留 Cookie 仍然让受保护接口继续放行。
     */
    @Override
    public void logout() {
        StpUtil.logout();
    }


    /**
     * 根据用户名查询用户实体
     * @param username 登录用户名
     * @return 正常状态的用户，不存在时返回 null
     */
    private UserDO findByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
        );
    }

    /**
     * 保存登录用户信息到 Sa-Token Session。
     * 当前会话保存在内存，仅写入基础字段，便于后续接入持久化存储。
     */
    private void saveUserSession(UserDO user) {
        SaSession session = StpUtil.getSession();
        session.set("userId", user.getId());
        session.set("userName", user.getUsername());
        session.set("avatar", user.getAvatar());
        session.set("role", user.getRole());
    }


}
