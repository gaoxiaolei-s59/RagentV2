package org.puregxl.ragent.system.user.service;

import org.puregxl.ragent.system.user.dto.response.UserResponse;

/** 当前登录用户查询。 */
public interface UserService {
    UserResponse currentUser();
}
