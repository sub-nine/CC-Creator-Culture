package com.sub9.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("공통 전역 예외 처리기")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("성공 응답을 공통 JSON 형식으로 직렬화한다")
    void when_api_response_is_returned_then_serializes_success_contract() throws Exception {
        mockMvc.perform(get("/success").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("요청 성공"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @DisplayName("비즈니스 예외를 도메인 오류 응답으로 변환한다")
    void when_business_exception_is_thrown_then_returns_domain_error_response() throws Exception {
        mockMvc.perform(get("/business").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_0001"))
                .andExpect(jsonPath("$.message").value("이미 등록된 상품입니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("서비스 이용 불가 예외를 공통 503 응답으로 변환한다")
    void when_service_is_unavailable_then_returns_service_unavailable_response() throws Exception {
        mockMvc.perform(get("/service-unavailable").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0009"))
                .andExpect(jsonPath("$.message").value("서비스를 일시적으로 사용할 수 없습니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("요청 본문 검증 실패를 필드 오류 응답으로 변환한다")
    void when_request_body_validation_fails_then_returns_field_error_response() throws Exception {
        mockMvc.perform(post("/body-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
                .andExpect(jsonPath("$.errors[0].name").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("메서드 검증에서도 요청 본문의 실제 필드명을 보존한다")
    void when_method_validation_contains_body_errors_then_preserves_nested_field_name() throws Exception {
        mockMvc.perform(post("/combined-validation")
                        .param("size", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
                .andExpect(jsonPath("$.errors[*].name").value(hasItem("이름은 필수입니다.")))
                .andExpect(content().string(not(containsString("\"arg0\":\"이름은 필수입니다.\""))));
    }

    @Test
    @DisplayName("바인딩 검증 실패를 필드 오류 응답으로 변환한다")
    void when_binding_validation_fails_then_returns_field_error_response() throws Exception {
        mockMvc.perform(get("/bind-validation").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
                .andExpect(jsonPath("$.errors[0].nickname").value("닉네임은 필수입니다."));
    }

    @Test
    @DisplayName("메서드 파라미터 검증 실패를 실제 파라미터명으로 변환한다")
    void when_method_parameter_validation_fails_then_uses_parameter_name() throws Exception {
        mockMvc.perform(get("/method-validation")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
                .andExpect(jsonPath("$.errors[0].size").value("크기는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("응답값 검증 실패를 서버 내부 오류로 변환한다")
    void when_return_value_validation_fails_then_returns_internal_server_error() {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        when(exception.isForReturnValue()).thenReturn(true);

        var response = new GlobalExceptionHandler().handleHandlerMethodValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("COMMON_0001");
        assertThat(response.getBody().getErrors()).isEmpty();
    }

    @Test
    @DisplayName("여러 파라미터 사이의 검증 실패를 request 오류로 변환한다")
    void when_cross_parameter_validation_fails_then_returns_request_error() {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        MessageSourceResolvable crossParameterError = mock(MessageSourceResolvable.class);
        when(exception.getParameterValidationResults()).thenReturn(List.of());
        when(exception.getCrossParameterValidationResults()).thenReturn(List.of(crossParameterError));
        when(crossParameterError.getDefaultMessage()).thenReturn("요청값 조합이 올바르지 않습니다.");

        var response = new GlobalExceptionHandler().handleHandlerMethodValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("COMMON_0003");
        assertThat(response.getBody().getErrors())
                .containsExactly(Map.of("request", "요청값 조합이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("제약 조건 위반은 상세 정보를 숨기고 서버 오류로 변환한다")
    void when_constraint_is_violated_then_hides_details() {
        ConstraintViolationException exception =
                new ConstraintViolationException("internal service parameter detail", Set.of());

        var response = new GlobalExceptionHandler().handleConstraintViolationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("COMMON_0001");
        assertThat(response.getBody().getMessage()).isEqualTo("서버 내부 오류가 발생했습니다.");
        assertThat(response.getBody().getErrors()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON을 잘못된 요청 응답으로 변환한다")
    void when_json_is_malformed_then_returns_bad_request_response() throws Exception {
        mockMvc.perform(post("/body-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("요청 파라미터 타입 불일치를 잘못된 요청 응답으로 변환한다")
    void when_request_parameter_type_mismatches_then_returns_bad_request_response() throws Exception {
        mockMvc.perform(get("/number")
                        .param("size", "invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("필수 요청 파라미터 누락을 잘못된 요청 응답으로 변환한다")
    void when_required_parameter_is_missing_then_returns_bad_request_response() throws Exception {
        mockMvc.perform(get("/required-parameter").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("필수 요청 헤더 누락을 잘못된 요청 응답으로 변환한다")
    void when_required_header_is_missing_then_returns_bad_request_response() throws Exception {
        mockMvc.perform(get("/required-header").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("없는 리소스를 공통 404 응답으로 변환한다")
    void when_resource_is_not_found_then_returns_not_found_response() throws Exception {
        mockMvc.perform(get("/missing-resource").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0004"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드를 405 응답으로 변환한다")
    void when_http_method_is_not_supported_then_returns_method_not_allowed_response() throws Exception {
        mockMvc.perform(get("/body-validation").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0005"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type을 415 응답으로 변환한다")
    void when_content_type_is_not_supported_then_returns_unsupported_media_type_response() throws Exception {
        mockMvc.perform(post("/body-validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0006"));
    }

    @Test
    @DisplayName("예상하지 못한 예외의 내부 메시지를 노출하지 않는다")
    void when_unexpected_exception_occurs_then_hides_internal_message() throws Exception {
        mockMvc.perform(get("/unexpected").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("sensitive internal detail"))));
    }

    @RestController
    static class TestController {

        @GetMapping("/success")
        ApiResponse<Map<String, Long>> success() {
            return ApiResponse.success(Map.of("id", 1L));
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(TestErrorCode.PRODUCT_ALREADY_EXISTS);
        }

        @GetMapping("/service-unavailable")
        void serviceUnavailable() {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }

        @PostMapping("/body-validation")
        void bodyValidation(@Valid @RequestBody ValidationRequest request) {
        }

        @PostMapping("/combined-validation")
        void combinedValidation(
                @Valid @RequestBody ValidationRequest request,
                @RequestParam("size")
                @Min(value = 1, message = "크기는 1 이상이어야 합니다.") int size) {
        }

        @GetMapping("/bind-validation")
        void bindValidation() throws BindException {
            BindException exception = new BindException(new Object(), "request");
            exception.addError(new FieldError(
                    "request", "nickname", "닉네임은 필수입니다."));
            throw exception;
        }

        @GetMapping("/method-validation")
        void methodValidation(
                @RequestParam("size")
                @Min(value = 1, message = "크기는 1 이상이어야 합니다.") int size) {
        }

        @GetMapping("/number")
        void number(@RequestParam("size") int size) {
        }

        @GetMapping("/required-parameter")
        void requiredParameter(@RequestParam("name") String name) {
        }

        @GetMapping("/required-header")
        void requiredHeader(@RequestHeader("X-Request-Id") String requestId) {
        }

        @GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-resource", "not found");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("sensitive internal detail");
        }
    }

    record ValidationRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    enum TestErrorCode implements ErrorCode {
        PRODUCT_ALREADY_EXISTS;

        @Override
        public String code() {
            return "PRODUCT_0001";
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String message() {
            return "이미 등록된 상품입니다.";
        }
    }
}
