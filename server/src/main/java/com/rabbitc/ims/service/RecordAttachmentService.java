package com.rabbitc.ims.service;

import com.rabbitc.ims.model.RecordAttachment;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.web.ApiException;
import com.rabbitc.ims.web.FileController;
import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 文件关联和可选业务字段更新共用一笔事务及当前用户的记录权限。 */
@Service
public class RecordAttachmentService {
    private final ModelSession sql;
    private final CrudService crud;
    private final FileController files;
    private final AuditService audit;

    public RecordAttachmentService(ModelSession sql, CrudService crud, FileController files, AuditService audit) {
        this.sql = sql;
        this.crud = crud;
        this.files = files;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(LoginUser user, String model, String recordId) {
        Query<RecordAttachment> query = forRecord(model, recordId);
        crud.get(user, model, recordId);
        return sql.<RecordAttachment>selectList(query).stream().map(RecordAttachmentService::view).toList();
    }

    public ResponseEntity<byte[]> download(LoginUser user, String model, String recordId, String attachmentId) {
        Query<RecordAttachment> query = forRecord(model, recordId);
        crud.get(user, model, recordId);
        return files.downloadLinkedFile(require(query, attachmentId).fileId);
    }

    public void detach(LoginUser user, String model, String recordId, String attachmentId) {
        Query<RecordAttachment> query = forRecord(model, recordId);
        sql.transaction(tx -> {
            crud.requireEditableRecord(user, model, recordId);
            var attachment = require(query, attachmentId);
            if (tx.delete(attachment) == 0) throw ApiException.notFound("附件关联不存在");
            audit.record(user, "解除关联", "业务附件", attachment.id, attachment.name);
        });
    }

    public Map<String, Object> upload(LoginUser user, String model, String recordId, MultipartFile file,
                                      Map<String, Object> values) {
        return change(user, model, recordId, values, () -> {
            var stored = files.uploadFor(user, file, true);
            return metadata((String) stored.get("id"), (String) stored.get("name"),
                    (String) stored.get("contentType"), ((Number) stored.get("size")).longValue());
        });
    }

    public Map<String, Object> attach(LoginUser user, String model, String recordId, String fileId,
                                      Map<String, Object> values) {
        return change(user, model, recordId, values, () -> {
            var file = files.chatAttachment(user, fileId);
            return metadata(file.id, file.name, file.contentType, file.size);
        });
    }

    private Map<String, Object> change(LoginUser user, String model, String recordId, Map<String, Object> values,
                                       java.util.function.Supplier<RecordAttachment> source) {
        forRecord(model, recordId);
        Map<String, Object> result = new LinkedHashMap<>();
        sql.transaction(tx -> {
            crud.requireEditableRecord(user, model, recordId);
            var attachment = source.get();
            attachment.id = UUID.nameUUIDFromBytes((model + ":" + recordId + ":" + attachment.fileId)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            if ("ticket".equals(model)) attachment.ticketId = recordId;
            else attachment.assetId = recordId;
            if (tx.findById(RecordAttachment.class, attachment.id).isEmpty()) {
                try {
                    tx.insert(attachment);
                } catch (CheckerException error) {
                    throw ApiException.badRequest("无法保存附件关联");
                }
                audit.record(user, "关联", "业务附件", attachment.id, attachment.name);
            }
            if (values != null && !values.isEmpty()) result.put("record", crud.update(user, model, recordId, values));
            result.putAll(view(attachment));
            result.put("model", model);
            result.put("recordId", recordId);
        });
        return result;
    }

    private static RecordAttachment metadata(String fileId, String name, String contentType, Long size) {
        var attachment = new RecordAttachment();
        attachment.fileId = fileId;
        attachment.name = name;
        attachment.contentType = contentType;
        attachment.size = size;
        return attachment;
    }

    private RecordAttachment require(Query<RecordAttachment> query, String id) {
        return sql.<RecordAttachment>selectOne(query.where("id", id))
                .orElseThrow(() -> ApiException.notFound("附件关联不存在"));
    }

    private static Query<RecordAttachment> forRecord(String model, String recordId) {
        String field = switch (model) {
            case "ticket" -> "ticketId";
            case "asset" -> "assetId";
            default -> throw ApiException.badRequest("目前支持关联工单和资产的附件");
        };
        return Query.of(RecordAttachment.class).where(field, recordId);
    }

    private static Map<String, Object> view(RecordAttachment item) {
        return Map.of("id", item.id, "name", item.name, "contentType", item.contentType, "size", item.size);
    }
}
