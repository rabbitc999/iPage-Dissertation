package com.rabbitc.ims.service;

import com.rabbitc.ims.model.SysSetting;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.web.ApiException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** 清理 Logback 按日和大小滚动生成的归档；始终保留当前日志。 */
@Service
@EnableScheduling
public class LogRetentionService {
    private static final Logger LOG = LoggerFactory.getLogger(LogRetentionService.class);
    private static final String KEY = "logRetentionDays";
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 3650;
    private final ModelSession sql;
    private final AuditService audit;
    private final DataMaintenanceGate maintenance;
    private final Path logFile;
    private final Pattern archiveName;

    public LogRetentionService(ModelSession sql, AuditService audit, DataMaintenanceGate maintenance,
                               @Value("${logging.file.name}") String filename) {
        this.sql = sql;
        this.audit = audit;
        this.maintenance = maintenance;
        logFile = Path.of(filename).toAbsolutePath().normalize();
        archiveName = Pattern.compile(Pattern.quote(logFile.getFileName().toString())
                + "[.]([0-9]{4}-[0-9]{2}-[0-9]{2})[.][0-9]+[.]gz");
    }

    public record Settings(int retentionDays, String logFile) {}

    public Settings settings() {
        return new Settings(retentionDays(), logFile.toString());
    }

    public synchronized Settings save(LoginUser user, Integer days) {
        if (days == null || days < 0 || days > MAX_DAYS) {
            throw ApiException.badRequest("日志保留天数须为 0–3650 的整数，0 表示长期保留");
        }
        sql.transaction(tx -> {
            var existing = tx.<SysSetting>selectOne(Query.of(SysSetting.class).where("settingKey", KEY));
            SysSetting setting = existing.orElseGet(SysSetting::new);
            setting.settingKey = KEY;
            setting.settingValue = days.toString();
            setting.updatedAt = LocalDateTime.now();
            if (existing.isPresent()) tx.update(setting);
            else tx.insert(setting);
            audit.record(user, "修改", "日志保留策略", null, days == 0 ? "长期保留" : days + " 天");
        });
        cleanup();
        return settings();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public synchronized void cleanup() {
        var lock = maintenance.shared();
        if (!lock.tryLock()) return;
        try {
            int days = retentionDays();
            if (days < 1 || days > MAX_DAYS || !Files.isDirectory(logFile.getParent())) return;
            LocalDate cutoff = LocalDate.now().minusDays(days);
            try (var paths = Files.newDirectoryStream(logFile.getParent())) {
                for (Path path : paths) {
                    var match = archiveName.matcher(path.getFileName().toString());
                    if (!match.matches() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    try {
                        if (LocalDate.parse(match.group(1)).isBefore(cutoff)) Files.deleteIfExists(path);
                    } catch (DateTimeParseException ignored) {
                        // 只删除命名日期有效的应用日志归档。
                    } catch (IOException error) {
                        LOG.warn("无法清理日志归档 {}", path.getFileName(), error);
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            LOG.warn("运行日志保留策略失败", error);
        } finally {
            lock.unlock();
        }
    }

    private int retentionDays() {
        return sql.<SysSetting>selectOne(Query.of(SysSetting.class).where("settingKey", KEY))
                .map(setting -> Integer.parseInt(setting.settingValue)).orElse(DEFAULT_DAYS);
    }
}
