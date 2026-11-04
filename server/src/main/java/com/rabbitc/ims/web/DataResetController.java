package com.rabbitc.ims.web;

import com.rabbitc.ims.security.RequireLevel;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.DataResetService;
import com.rabbitc.ipage.meta.ModelPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 清空功能只接受超级管理员的两阶段请求。 */
@RestController
@RequestMapping("/api/system/data-reset")
@RequireLevel(ModelPermission.SUPER_ADMIN)
public class DataResetController {
    public static final String EXECUTE_PATH = "/api/system/data-reset/execute";
    private final DataResetService reset;

    public DataResetController(DataResetService reset) {
        this.reset = reset;
    }

    public record VerifyRequest(String password) {}
    public record ExecuteRequest(String token, String confirmation) {}

    @PostMapping("/verify")
    public DataResetService.Confirmation verify(@RequestBody VerifyRequest request) {
        return reset.verify(UserContext.require(), request.password());
    }

    @PostMapping("/execute")
    public Map<String, Integer> execute(@RequestBody ExecuteRequest request) {
        return Map.of("deleted", reset.execute(UserContext.require(), request.token(), request.confirmation()));
    }
}
