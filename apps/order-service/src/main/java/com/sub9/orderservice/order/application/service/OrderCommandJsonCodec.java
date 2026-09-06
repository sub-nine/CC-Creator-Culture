package com.sub9.orderservice.order.application.service;

import com.sub9.orderservice.order.domain.model.OrderCommandType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class OrderCommandJsonCodec {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "shippingaddress",
            "recipientname",
            "recipientphone",
            "postalcode",
            "addressline1",
            "addressline2",
            "address",
            "phone",
            "authorization",
            "token",
            "accesstoken",
            "refreshtoken",
            "password",
            "secret",
            "clientsecret",
            "credential",
            "cookie",
            "setcookie",
            "apikey",
            "jwt");

    private final JsonMapper jsonMapper;

    public String hash(UUID actorId, OrderCommandType commandType, Object hashMaterial) {
        Objects.requireNonNull(actorId, "요청자 식별자는 필수입니다.");
        Objects.requireNonNull(commandType, "주문 명령 종류는 필수입니다.");
        Objects.requireNonNull(hashMaterial, "요청 해시 대상은 필수입니다.");

        ObjectNode envelope = jsonMapper.createObjectNode();
        envelope.put("actorId", actorId.toString());
        envelope.put("commandType", commandType.name());
        envelope.set("request", toJsonNode(hashMaterial));
        String canonicalJson = write(canonicalize(envelope));
        return sha256(canonicalJson);
    }

    public String encodeResponse(Object responseBody) {
        Objects.requireNonNull(responseBody, "재응답할 응답 본문은 필수입니다.");
        JsonNode response = toJsonNode(responseBody);
        if (!response.isObject()) {
            throw new IllegalArgumentException("재응답할 응답 본문은 JSON 객체여야 합니다.");
        }
        rejectSensitiveFields(response);
        return write(canonicalize(response));
    }

    public JsonNode decodeResponse(String responsePayload) {
        try {
            return jsonMapper.readTree(responsePayload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("저장된 주문 명령 응답을 읽을 수 없습니다.", exception);
        }
    }

    private JsonNode toJsonNode(Object value) {
        try {
            return value instanceof JsonNode node ? node : jsonMapper.valueToTree(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("주문 명령 데이터를 JSON으로 변환할 수 없습니다.", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = jsonMapper.createObjectNode();
            node.propertyNames().stream()
                    .sorted()
                    .forEach(name -> sorted.set(name, canonicalize(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode ordered = jsonMapper.createArrayNode();
            node.forEach(child -> ordered.add(canonicalize(child)));
            return ordered;
        }
        return node.deepCopy();
    }

    private void rejectSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(property -> {
                if (isSensitive(property.getKey())) {
                    throw new IllegalArgumentException("민감정보가 포함된 응답은 저장할 수 없습니다.");
                }
                rejectSensitiveFields(property.getValue());
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(this::rejectSensitiveFields);
        }
    }

    private static boolean isSensitive(String fieldName) {
        String normalized = NON_ALPHANUMERIC.matcher(fieldName)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELDS.contains(normalized)
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("credential")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("address")
                || normalized.contains("phone");
    }

    private String write(JsonNode node) {
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("주문 명령 JSON을 생성할 수 없습니다.", exception);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", exception);
        }
    }
}
