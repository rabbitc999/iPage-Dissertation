package com.rabbitc.ims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** 公开品牌配置缓存。写入占位符防止刷新前开始的旧请求回填旧值。 */
@Component
@ConditionalOnProperty(name = "ims.redis.enabled", havingValue = "true")
public class RedisBrandCache {
    private static final Logger LOG = LoggerFactory.getLogger(RedisBrandCache.class);
    private static final String LOADING = "loading:";
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, String>> BRAND_TYPE = new TypeReference<>() {};
    private static final DefaultRedisScript<Long> STORE_IF_CURRENT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String key;
    private final String ttlSeconds;

    public RedisBrandCache(StringRedisTemplate redis, ObjectMapper mapper,
                           @Value("${ims.redis.key-prefix}") String prefix,
                           @Value("${ims.redis.brand-ttl-seconds}") long ttlSeconds) {
        if (prefix.isBlank() || ttlSeconds < 1) {
            throw new IllegalArgumentException("Redis 缓存前缀不能为空，缓存有效期必须为正数");
        }
        this.redis = redis;
        this.mapper = mapper;
        this.key = prefix + ":public:brand:v1";
        this.ttlSeconds = Long.toString(ttlSeconds);
    }

    public Map<String, String> get(Supplier<Map<String, String>> load) {
        String reservation = LOADING + UUID.randomUUID();
        boolean reserved = false;
        try {
            String value = redis.opsForValue().get(key);
            if (value != null && !value.startsWith(LOADING)) {
                Map<String, String> cached = mapper.readValue(value, BRAND_TYPE);
                if (cached != null) {
                    return cached;
                }
            } else if (value == null) {
                reserved = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, reservation, LOAD_TIMEOUT));
            }
        } catch (DataAccessException | JsonProcessingException e) {
            LOG.debug("读取 Redis 品牌缓存失败，回退数据库", e);
        }

        Map<String, String> current = load.get();
        if (reserved) {
            try {
                redis.execute(STORE_IF_CURRENT, List.of(key), reservation, mapper.writeValueAsString(current), ttlSeconds);
            } catch (DataAccessException | JsonProcessingException e) {
                LOG.debug("写入 Redis 品牌缓存失败", e);
            }
        }
        return current;
    }

    /** 返回 Redis 是否确认了失效操作。键已经不存在也视为成功。 */
    public boolean invalidate() {
        try {
            redis.delete(key);
            return true;
        } catch (DataAccessException e) {
            LOG.debug("清理 Redis 品牌缓存失败，缓存将在有效期结束后过期", e);
            return false;
        }
    }
}
