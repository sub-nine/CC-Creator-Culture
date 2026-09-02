package com.sub9.productservice.product.support;

import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.productservice.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** 컨트롤러 테스트 클래스 */
@WebMvcTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
public abstract class AbstractControllerTest {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected JsonMapper jsonMapper;
}
