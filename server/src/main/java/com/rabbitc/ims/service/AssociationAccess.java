package com.rabbitc.ims.service;

import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.model.Asset;
import com.rabbitc.ims.model.Department;
import com.rabbitc.ims.model.Employee;
import com.rabbitc.ims.model.Ticket;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.support.Ids;
import com.rabbitc.ims.web.ApiException;
import com.rabbitc.ipage.enums.SortType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.FieldDescriptor;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 业务关联选项和保存校验共用同一个可选范围。 */
@Component
public class AssociationAccess {
    private final ModelSession sql;

    public AssociationAccess(ModelSession sql) {
        this.sql = sql;
    }

    public Map<String, String> options(LoginUser user, Class<?> owner, FieldDescriptor field) {
        Class<?> target = field.relatedClass();
        if (!controlled(owner) || unrestricted(user)) {
            return sql.options(target);
        }
        ModelMeta meta = ModelMeta.of(target);
        Query query = scoped(user, target).orderBy(meta.idField().name(), SortType.ASC);
        Map<String, String> options = new LinkedHashMap<>();
        for (Object row : sql.selectList(query)) {
            options.put(String.valueOf(meta.idValue(row)), meta.display(row));
        }
        return options;
    }

    public void validate(LoginUser user, Class<?> owner, FieldDescriptor field, String id) {
        if (!controlled(owner) || id == null) {
            return;
        }
        Class<?> target = field.relatedClass();
        Query query = scoped(user, target).where(ModelMeta.of(target).idField().name(), id);
        if (sql.count(query) == 0) {
            throw ApiException.forbidden("所选「" + field.text() + "」不存在或不在可选范围");
        }
    }

    public void assignDepartment(LoginUser user, ModelMeta meta, Object entity) {
        if (!controlled(meta.modelClass()) || unrestricted(user) || user.dataScope() != DataScope.DEPT) {
            return;
        }
        if (user.deptId() == null) {
            throw ApiException.forbidden("当前账号未分配部门");
        }
        FieldDescriptor field = meta.field("deptId").orElseThrow();
        if (field.read(entity) == null) {
            field.write(entity, user.deptId());
        }
    }

    public Map<String, String> departments(LoginUser user) {
        if (unrestricted(user)) {
            return sql.options(Department.class);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Department department : sql.<Department>selectList(scoped(user, Department.class))) {
            result.put(department.id, department.name);
        }
        return result;
    }

    private Query scoped(LoginUser user, Class<?> target) {
        Query query = Query.of(target, sql.dialect());
        if (unrestricted(user)) {
            return query;
        }
        String department = user.deptId() == null ? Ids.NO_MATCH : user.deptId();
        if (target == Department.class) {
            return query.where("id", department);
        }
        if (user.dataScope() == DataScope.DEPT) {
            return query.where("deptId", department);
        }
        return target == User.class ? query.where("id", user.id()) : query.where("createBy", user.id());
    }

    private static boolean controlled(Class<?> type) {
        return type == Ticket.class || type == Employee.class || type == Asset.class;
    }

    private static boolean unrestricted(LoginUser user) {
        return user.isAdmin() || user.dataScope() == null || user.dataScope() == DataScope.ALL;
    }
}
