package org.puregxl.ragent.system.user.controller;


import org.springframework.context.annotation.Profile;

import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.framework.convention.Result;
import org.puregxl.ragent.framework.web.Results;
import org.puregxl.ragent.system.user.dto.response.UserResponse;
import org.puregxl.ragent.system.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Profile("mysql")
public class UserController {

    private final UserService userService;

    @GetMapping("/api/user")
    public Result<UserResponse> queryUserBaseInformation(){
        return Results.success(userService.currentUser());
    }
}
