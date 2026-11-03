package com.rabbitc.ims.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码存储：PBKDF2-HMAC-SHA256 单向散列 + 每用户随机盐，只用 JDK 自带能力，不引第三方加密库。
 *
 * <h2>为什么是「哈希」而不是「加密」</h2>
 * 密码落库用的是<b>不可逆</b>的单向散列，不是可逆的对称/非对称加密。区别很关键：
 * <ul>
 *     <li>加密需要一把密钥，密钥泄露 = 全部密码泄露；哈希没有密钥，泄露的只是散列值；</li>
 *     <li>系统里没有任何地方需要"取回原密码"，因此也不应该具备这个能力；</li>
 *     <li>校验时是「用同样的盐和迭代次数再算一遍，比较结果」，而不是「解密后比对明文」。</li>
 * </ul>
 *
 * <h2>存储格式</h2>
 * <pre>pbkdf2$迭代次数$Base64盐$Base64散列</pre>
 * 例如 {@code pbkdf2$210000$k3Y...$9fQ...}。三个要点：
 * <ul>
 *     <li><b>盐随记录存</b>：即使两个人密码相同，散列值也不同，彩虹表失效；</li>
 *     <li><b>迭代次数随记录存</b>：以后调高 {@link #ITERATIONS} 不会让老密码失效，
 *         用户下次登录时由 {@link #needsUpgrade(String)} 判定并静默升级；</li>
 *     <li><b>算法名随记录存</b>：将来换成 Argon2 之类，可以按前缀分派，平滑迁移。</li>
 * </ul>
 *
 * <h2>参数选择</h2>
 * PBKDF2-HMAC-SHA256 取 210,000 次迭代，对齐 OWASP《Password Storage Cheat Sheet》
 * 对 PBKDF2-SHA256 的推荐值。单次校验约几十毫秒，对登录接口可以接受；
 * 对撞库攻击者来说，这个成本会被放大到无法承受。
 * <p>
 * 比较使用 {@link MessageDigest#isEqual}，是常量时间比较，避免通过响应时间差逐字节猜散列。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class PasswordHasher {

    /** JDK 内置的 PBKDF2 实现名。 */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 当前迭代次数；调高即可，旧哈希仍可验证。 */
    private static final int ITERATIONS = 210_000;

    /** 派生密钥长度（bit）。 */
    private static final int KEY_LENGTH = 256;

    /** 盐长度（字节）。 */
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成密码散列。
     *
     * @param raw 明文密码
     * @return 可落库的散列串，格式 {@code pbkdf2$迭代次数$盐$散列}
     * @throws IllegalArgumentException 密码为空
     */
    public String hash(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        // 每个密码独立生成盐：盐不需要保密，但必须唯一
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(raw.toCharArray(), salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /**
     * 校验密码。
     *
     * @param raw    明文密码
     * @param stored 落库的散列串
     * @return 匹配返回 {@code true}；格式不合法、盐损坏等情况一律返回 {@code false}
     */
    public boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            // 用记录里自带的迭代次数重算，而不是用当前常量——这是老密码能被验证的原因
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(raw.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 判断一条已存储的散列是否需要用当前参数重算。
     * <p>
     * 调用时机只有一处：登录校验通过之后、拿到明文密码的那一刻。此时可以无感升级，
     * 不需要用户改密码、也不需要额外的迁移脚本。
     *
     * @param stored 落库的散列串
     * @return 需要升级返回 {@code true}
     */
    public boolean needsUpgrade(String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            // 未知格式：说明是历史数据，登录成功后统一重算
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * 用 PBKDF2 派生密钥。
     *
     * @param password   明文密码
     * @param salt       盐
     * @param iterations 迭代次数
     * @return 派生出的密钥字节
     */
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码散列计算失败", e);
        }
    }
}
