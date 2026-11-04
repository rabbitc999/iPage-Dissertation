package com.rabbitc.ims.meta;

import com.rabbitc.ims.ai.AiProperties;
import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.web.ApiException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型注册表：启动时扫描 {@code com.rabbitc.ims.model} 下所有 {@link ModelClass}，
 * 建立「前端 key ↔ Java 类 ↔ ipage 元数据」的映射。
 * <p>
 * key 取类名首字母小写（{@code Employee → employee}），只用于 URL 与前端路由，
 * 真正的表名与列名一律以 {@link ModelMeta} 为准，不在字符串上推断。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class ModelRegistry {

    /**
     * 不暴露为通用 CRUD 入口的模型：
     * <ul>
     *     <li>两张关联表由用户/角色保存逻辑统一维护，直接增删会破坏角色装配；</li>
     *     <li>界面配置有独立接口，需要按「归属用户」做行级隔离；</li>
     *     <li>文件记录的内容列是大字段，统一走 /api/files 接口，避免误把字节读进列表。</li>
     * </ul>
     */
    private static final Set<String> NOT_EXPOSED =
            Set.of("userRole", "rolePermission", "viewConfig", "sysFile", "sysSetting", "businessEvent", "aiModel", "recordAttachment");

    private final Set<String> excluded;
    private final Map<String, Class<?>> models = new LinkedHashMap<>();

    /**
     * 扫描模型类。
     */
    public ModelRegistry(AiProperties ai) {
        excluded = new LinkedHashSet<>(NOT_EXPOSED);
        if (!ai.isAvailable()) {
            excluded.add("aiMcpServer");
            excluded.add("aiSkill");
        }
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ModelClass.class));
        Set<String> keys = new LinkedHashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.rabbitc.ims.model")) {
            Class<?> modelClass = load(definition.getBeanClassName());
            keys.add(keyOf(modelClass));
            models.put(keyOf(modelClass), modelClass);
        }
        // 按 key 排序，保证菜单和接口输出稳定，便于前端做本地缓存比对
        List<String> ordered = keys.stream().sorted().toList();
        Map<String, Class<?>> sorted = new LinkedHashMap<>();
        ordered.forEach(key -> sorted.put(key, models.get(key)));
        models.clear();
        models.putAll(sorted);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("扫描到模型类但无法加载：" + className, e);
        }
    }

    /**
     * 由模型类得到前端 key。
     *
     * @param modelClass 模型类
     * @return key
     */
    public static String keyOf(Class<?> modelClass) {
        String simple = modelClass.getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /**
     * 全部模型类（含不暴露 CRUD 的关联表），用于启动时建表。
     *
     * @return 模型类集合
     */
    public Collection<Class<?>> all() {
        return List.copyOf(models.values());
    }

    /**
     * 可经通用接口访问的模型 key。
     *
     * @return key 集合
     */
    public Set<String> exposedKeys() {
        Set<String> keys = new LinkedHashSet<>(models.keySet());
        keys.removeAll(excluded);
        return keys;
    }

    /**
     * 按 key 取模型类。
     *
     * @param key 模型 key
     * @return 模型类
     */
    public Class<?> require(String key) {
        Class<?> modelClass = models.get(key);
        if (modelClass == null || excluded.contains(key)) {
            throw ApiException.notFound("未知模型：" + key);
        }
        return modelClass;
    }

    /**
     * 按 key 取 ipage 元数据。
     *
     * @param key 模型 key
     * @return 元数据
     */
    public ModelMeta requireMeta(String key) {
        return ModelMeta.of(require(key));
    }

    /**
     * 模型所属菜单分组，由表名前缀推导，新增模型不用改配置。
     *
     * @param modelClass 模型类
     * @return 分组名
     */
    public static String groupOf(Class<?> modelClass) {
        String table = ModelMeta.of(modelClass).table();
        if (table.startsWith("ai_")) {
            return "智能助手";
        }
        if (table.startsWith("sys_")) {
            return "系统管理";
        }
        return "业务数据";
    }
}
