package org.puregxl.ragent.system.user.dto.request;

import lombok.Data;

@Data
public class LoginRequest {
    /**
     * 用户名
     */
    @jakarta.validation.constraints.NotBlank(message = "用户名不能为空")
    private String userName;

    /**
     * 密码
     */
    @jakarta.validation.constraints.NotBlank(message = "密码不能为空")
    private String password;
}
