package com.rabbitc.ims.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitc.ims.config.ImsProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 无状态令牌：{@code base64url(payload).base64url(hmac)}。
 * <p>
 * payload 只放三样东西：用户主键、令牌版本、过期时间。不放角色与权限——
 * 那些在每次请求时从库里读，改了权限立即生效，不必等旧令牌过期。
 * 令牌版本与用户表上的 {@code tokenVersion} 比对，改密码或强制下线时自增即可全部作废。
 * <p>
 * 用户主键是 UUID 字符串，直接作为 JSON 字符串放进 {@code uid} 字段；
 * 校验时用 {@code asText()} 读回，不做任何数值转换——这也是主键改成 UUID 后
 * 唯一需要小心的地方：旧代码里的 {@code asLong()} 会把 UUID 解析成 0。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class TokenService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long ttlSeconds;

    /**
     * 构造令牌服务。
     *
     * @param mapper      JSON 序列化器
     * @param properties  应用配置
     */
    public TokenService(ObjectMapper mapper, ImsProperties properties) {
        this.mapper = mapper;
        this.secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(1, properties.getTokenTtlHours()) * 3600L;
    }

    /**
     * 令牌载荷。
     *
     * @param userId  用户主键
     * @param version 令牌版本
     */
    public record Claim(String userId, int version) {
    }

    /**
     * 签发令牌。
     *
     * @param userId  用户主键（UUID）
     * @param version 当前令牌版本
     * @return 令牌
     */
    public String issue(String userId, int version) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("uid", userId);
        payload.put("ver", version);
        payload.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
        try {
            String encoded = ENCODER.encodeToString(mapper.writeValueAsBytes(payload));
            return encoded + "." + ENCODER.encodeToString(sign(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("签发令牌失败", e);
        }
    }

    /**
     * 校验令牌。
     *
     * @param token 令牌
     * @return 载荷；签名错误、格式不对或已过期时为空
     */
    public Optional<Claim> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            byte[] signature = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), signature)) {
                return Optional.empty();
            }
            JsonNode payload = mapper.readTree(DECODER.decode(parts[0]));
            if (payload.path("exp").asLong() < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            String userId = payload.path("uid").asText("");
            if (userId.isBlank()) {
                // uid 缺失：令牌被截断或来自更早的格式，直接拒绝
                return Optional.empty();
            }
            return Optional.of(new Claim(userId, payload.path("ver").asInt()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("令牌签名失败", e);
        }
    }
}
