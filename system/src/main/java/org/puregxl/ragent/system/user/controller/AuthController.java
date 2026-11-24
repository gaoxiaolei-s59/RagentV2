package org.puregxl.ragent.system.user.controller;

import org.springframework.context.annotation.Profile;

import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.system.user.dto.request.LoginRequest;
import org.puregxl.ragent.system.user.dto.response.LoginResponse;
import org.puregxl.ragent.system.user.service.AuthService;
import org.puregxl.ragent.framework.convention.Result;
import org.puregxl.ragent.framework.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Profile("mysql")
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口
     * @param request 用户名和密码
     * @return 登录凭证及用户概要
     */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@jakarta.validation.Valid @RequestBody LoginRequest request) {
        return Results.success(authService.login(request));
    }


    /**
     * 用户退出登录。
     * 显式注销当前 Sa-Token 会话，确保请求头与 Cookie 两侧登录态都被统一清理。
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Results.success();
    }


}
