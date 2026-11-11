package org.puregxl.ragent.testfixture;

import org.puregxl.ragent.framework.exception.ServiceException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅由集成测试显式导入，正式应用不暴露人为抛错接口。 */
@TestConfiguration(proxyBeanMethods = false)
@RestController
public class TestErrorConfiguration {
    @GetMapping("/api/rag/business-error")
    public void businessError() { throw new ServiceException("测试业务异常"); }
}
