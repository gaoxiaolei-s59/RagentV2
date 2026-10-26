package org.puregxl.ragent.infra.model;

import org.springframework.util.ClassUtils;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按 provider 建立客户端索引，允许业务实现替换内置客户端，无需复用默认 Bean 名。 */
public final class ProviderClientRegistry {
    private ProviderClientRegistry() {}

    /**
     * 自定义实现优先于内置实现，且不受 Bean 注册顺序影响。
     * 同一 provider 存在两个自定义实现时拒绝启动，避免不确定地选中某个客户端。
     * 使用用户类识别内置实现，兼容 Spring 创建的 CGLIB 代理。
     */
    public static <T> Map<String, T> index(List<T> clients, Function<T, String> provider,
                                          Class<?>... defaultTypes) {
        Set<Class<?>> defaults = Arrays.stream(defaultTypes).collect(Collectors.toSet());
        Map<String, T> result = new LinkedHashMap<>();
        for (T client : clients) {
            String key = provider.apply(client);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("模型客户端 provider 不能为空");
            }
            T previous = result.get(key);
            if (previous == null) {
                result.put(key, client);
                continue;
            }
            boolean oldDefault = defaults.contains(ClassUtils.getUserClass(previous));
            boolean newDefault = defaults.contains(ClassUtils.getUserClass(client));
            if (oldDefault && !newDefault) {
                result.put(key, client);
            } else if (oldDefault == newDefault) {
                throw new IllegalStateException("同一 provider 配置了多个模型客户端: " + key);
            }
        }
        return result;
    }
}
