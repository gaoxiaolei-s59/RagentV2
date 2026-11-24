package org.puregxl.ragent.system.user.service;

import org.puregxl.ragent.system.user.dto.request.LoginRequest;
import org.puregxl.ragent.system.user.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    void logout();
}
