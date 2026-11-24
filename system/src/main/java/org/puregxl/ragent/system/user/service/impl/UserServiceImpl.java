package org.puregxl.ragent.system.user.service.impl;

import org.puregxl.ragent.framework.exception.ClientException;
import org.puregxl.ragent.system.user.context.UserContext;
import org.puregxl.ragent.system.user.context.UserInfoDTO;
import org.puregxl.ragent.system.user.dto.response.UserResponse;
import org.puregxl.ragent.system.user.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 查询拦截器装配的当前用户，不返回密码等数据库字段。 */
@Service
@Profile("mysql")
public class UserServiceImpl implements UserService {
    @Override
    public UserResponse currentUser() {
        UserInfoDTO user = UserContext.getUserContext();
        if (user == null) {
            throw new ClientException("当前用户信息不存在，请重新登录");
        }
        return UserResponse.builder().userName(user.getUserName()).avatar(user.getAvatar()).build();
    }
}
