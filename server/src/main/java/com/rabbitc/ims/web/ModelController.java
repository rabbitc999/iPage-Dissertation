package com.rabbitc.ims.web;

import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.CrudService;
import com.rabbitc.ims.service.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通用 CRUD 接口。
 * <p>
 * 所有模型共用这一组 URL，模型 key 由 {@code /api/models/{key}} 指定：
 * <pre>
 * GET    /api/models/employee?page=1&amp;size=20&amp;kw=张&amp;f_status=ENABLED
 * GET    /api/models/employee/3f2b1c9e-8a44-4d0e-9c31-77b6f0f1a2d5
 * POST   /api/models/employee
 * PUT    /api/models/employee/3f2b1c9e-8a44-4d0e-9c31-77b6f0f1a2d5
 * DELETE /api/models/employee/3f2b1c9e-8a44-4d0e-9c31-77b6f0f1a2d5
 * POST   /api/models/employee/batch-delete  {"ids":["3f2b...","9a1c..."]}
 * </pre>
 * 主键是 UUID 字符串，路径变量按 {@link String} 接收——不要改成 {@code Long}，
 * 否则 Spring 会在类型转换阶段直接抛 400，连「记录不存在」都进不去。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api/models/{key}")
public class ModelController {

    private final CrudService crud;

    /**
     * 构造控制器。
     *
     * @param crud 通用 CRUD 服务
     */
    public ModelController(CrudService crud) {
        this.crud = crud;
    }

    /**
     * 批量删除请求体。
     *
     * @param ids 主键集合
     */
    public record BatchDeleteRequest(List<String> ids) {
    }

    /**
     * 分页查询。
     *
     * @param key    模型 key
     * @param params 查询参数
     * @return 分页结果
     */
    @GetMapping
    public PageResult page(@PathVariable String key, @RequestParam Map<String, String> params) {
        return crud.page(UserContext.require(), key, params);
    }

    /**
     * 按主键查询。
     *
     * @param key 模型 key
     * @param id  主键
     * @return 行数据
     */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String key, @PathVariable String id) {
        return crud.get(UserContext.require(), key, id);
    }

    /**
     * 新增。
     *
     * @param key  模型 key
     * @param body 请求体
     * @return 新增后的行
     */
    @PostMapping
    public Map<String, Object> create(@PathVariable String key, @RequestBody Map<String, Object> body) {
        return crud.create(UserContext.require(), key, body);
    }

    /**
     * 修改。
     *
     * @param key  模型 key
     * @param id   主键
     * @param body 请求体
     * @return 修改后的行
     */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String key, @PathVariable String id,
                                      @RequestBody Map<String, Object> body) {
        return crud.update(UserContext.require(), key, id, body);
    }

    /**
     * 删除。
     *
     * @param key 模型 key
     * @param id  主键
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String key, @PathVariable String id) {
        crud.delete(UserContext.require(), key, id);
        return Map.of("ok", true);
    }

    /**
     * 批量删除。
     *
     * @param key     模型 key
     * @param request 主键集合
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@PathVariable String key,
                                           @RequestBody BatchDeleteRequest request) {
        int deleted = crud.deleteBatch(UserContext.require(), key,
                request.ids() == null ? List.of() : request.ids());
        return Map.of("deleted", deleted);
    }
}
