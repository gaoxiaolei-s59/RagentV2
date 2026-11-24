package org.puregxl.ragent.system.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.puregxl.ragent.system.user.controller.AuthController;
import org.puregxl.ragent.system.user.controller.UserController;
import org.puregxl.ragent.system.user.config.UserContextInterceptor;
import org.puregxl.ragent.system.user.config.UserWebMvcConfiguration;
import org.puregxl.ragent.system.user.context.UserContext;
import org.puregxl.ragent.system.user.entity.UserDO;
import org.puregxl.ragent.system.user.mapper.UserMapper;
import org.puregxl.ragent.system.user.service.impl.AuthServiceImpl;
import org.puregxl.ragent.system.user.service.impl.UserServiceImpl;
import org.puregxl.ragent.framework.web.GlobalExceptionHandler;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 登录、请求头认证、上下文清理及退出的完整 HTTP 流程。 */
@SpringBootTest(classes = UserLoginTest.App.class, properties = {
        "spring.profiles.active=mysql", "sa-token.token-name=s-token", "sa-token.is-read-cookie=false"})
class UserLoginTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuthController.class, UserController.class, AuthServiceImpl.class, UserServiceImpl.class,
            UserContextInterceptor.class, UserWebMvcConfiguration.class, GlobalExceptionHandler.class})
    static class App {
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserMapper mapper;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBeansOfType(jakarta.servlet.Filter.class).values().toArray(jakarta.servlet.Filter[]::new)).build();
        reset(mapper);
        UserDO user = new UserDO();
        user.setId("test-user"); user.setUsername("demo"); user.setPassword("demo-password");
        user.setRole("user"); user.setAvatar("/avatar.png"); user.setDeleted(0);
        when(mapper.selectOne(any())).thenReturn(user);
    }

    @Test void loginCurrentUserAndLogout() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"userName\":\"demo\",\"password\":\"demo-password\"}"))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userId").value("test-user"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
        mvc.perform(get("/api/user").header("s-token", token))
                .andExpect(jsonPath("$.data.userName").value("demo"))
                .andExpect(jsonPath("$.data.avatar").value("/avatar.png"));
        assertNull(UserContext.getUserContext());
        mvc.perform(post("/auth/logout").header("s-token", token)).andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/api/user").header("s-token", token)).andExpect(jsonPath("$.code").value("A000001"));
    }

    @Test void rejectsMissingToken() throws Exception {
        mvc.perform(get("/api/user")).andExpect(jsonPath("$.code").value("A000001"));
    }

    @Test void rejectsWrongPassword() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"userName\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test void rejectsBlankCredentials() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value("A000001"));
        verifyNoInteractions(mapper);
    }

    @Test void rejectsUnknownUser() throws Exception {
        when(mapper.selectOne(any())).thenReturn(null);
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"userName\":\"missing\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }
}
