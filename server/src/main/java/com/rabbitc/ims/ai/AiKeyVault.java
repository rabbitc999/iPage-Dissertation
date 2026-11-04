package com.rabbitc.ims.ai;

import com.rabbitc.ims.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 每次写入使用独立随机 nonce，主密钥单独保存在服务数据目录。 */
@Component
public class AiKeyVault {
    private final SecureRandom random = new SecureRandom();
    private final Path keyFile;

    public AiKeyVault(AiProperties properties) {
        keyFile = Path.of(properties.getWorkDir()).resolve("model-keys.bin").toAbsolutePath().normalize();
    }

    public byte[] encrypt(String id, String apiKey) {
        String prefix = apiKey.startsWith("sk-") ? "sk-" : "";
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, id, prefix, nonce, true)
                    .doFinal(apiKey.substring(prefix.length()).getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return (prefix + "v1:" + Base64.getEncoder().encodeToString(payload)).getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException error) {
            throw failure();
        }
    }

    public String decrypt(String id, byte[] encryptedKey) {
        String stored = new String(encryptedKey, StandardCharsets.UTF_8);
        String prefix = stored.startsWith("sk-") ? "sk-" : "";
        String version = prefix + "v1:";
        try {
            if (!stored.startsWith(version)) throw new GeneralSecurityException();
            byte[] payload = Base64.getDecoder().decode(stored.substring(version.length()));
            if (payload.length < 29) throw new GeneralSecurityException();
            var cipher = cipher(Cipher.DECRYPT_MODE, id, prefix, Arrays.copyOf(payload, 12), false);
            return prefix + new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
            throw failure();
        }
    }

    private Cipher cipher(int mode, String id, String prefix, byte[] nonce, boolean create)
            throws IOException, GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(masterKey(create), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD((id + ":" + prefix).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private synchronized byte[] masterKey(boolean create) throws IOException, GeneralSecurityException {
        if (create && Files.notExists(keyFile)) {
            Files.createDirectories(keyFile.getParent());
            byte[] key = new byte[32];
            random.nextBytes(key);
            var options = java.util.Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
            var store = Files.getFileStore(keyFile.getParent());
            var permissions = store.supportsFileAttributeView("posix")
                    ? new java.nio.file.attribute.FileAttribute<?>[]{
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                    : new java.nio.file.attribute.FileAttribute<?>[0];
            try (var channel = Files.newByteChannel(keyFile, options, permissions)) {
                var buffer = java.nio.ByteBuffer.wrap(key);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
        }
        byte[] key = Files.readAllBytes(keyFile);
        if (key.length != 32) throw new GeneralSecurityException();
        return key;
    }

    private static ApiException failure() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "无法读取或保存 AI 密钥，请管理员检查服务数据目录中的 model-keys.bin");
    }
}
