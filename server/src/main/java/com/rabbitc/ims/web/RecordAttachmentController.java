package com.rabbitc.ims.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitc.ims.security.RequirePermission;
import com.rabbitc.ims.ai.AiConfiguration;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.RecordAttachmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 受目标记录权限保护的业务附件上传、查看和下载入口。 */
@RestController
@RequestMapping("/api/models/{model}/{recordId}/attachments")
public class RecordAttachmentController {
    private final RecordAttachmentService attachments;
    private final ObjectMapper json;
    private final AiConfiguration ai;

    public RecordAttachmentController(RecordAttachmentService attachments, ObjectMapper json, AiConfiguration ai) {
        this.ai = ai;
        this.attachments = attachments;
        this.json = json;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable String model, @PathVariable String recordId) {
        return attachments.list(UserContext.require(), model, recordId);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String model, @PathVariable String recordId,
                                           @PathVariable String attachmentId) {
        return attachments.download(UserContext.require(), model, recordId, attachmentId);
    }

    @DeleteMapping("/{attachmentId}")
    public Map<String, Boolean> detach(@PathVariable String model, @PathVariable String recordId,
                                       @PathVariable String attachmentId) {
        attachments.detach(UserContext.require(), model, recordId, attachmentId);
        return Map.of("ok", true);
    }

    @PostMapping
    @RequirePermission("ai:use")
    public Map<String, Object> upload(@PathVariable String model, @PathVariable String recordId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "{}") String values,
                                      @RequestParam String aiModelId,
                                      @RequestParam(defaultValue = "READ_ONLY") ToolAccess toolAccess) {
        try {
            Map<String, Object> updates = json.readValue(values, new TypeReference<>() {});
            var user = UserContext.require();
            ToolAccess required = updates == null || updates.isEmpty() ? ToolAccess.ADD : ToolAccess.ADD_EDIT;
            ai.requireMcpAccess(user, aiModelId, toolAccess, required);
            return attachments.upload(user, model, recordId, file, updates);
        } catch (JsonProcessingException error) {
            throw ApiException.badRequest("记录更新字段必须是 JSON 对象");
        }
    }
}
