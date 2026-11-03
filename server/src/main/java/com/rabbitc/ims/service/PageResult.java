package com.rabbitc.ims.service;

import java.util.List;
import java.util.Map;

/**
 * 分页结果。
 *
 * @param rows  当前页数据
 * @param total 总行数
 * @param page  当前页码
 * @param size  每页条数
 * @param pages 总页数
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public record PageResult(List<Map<String, Object>> rows, long total, int page, int size, int pages) {

    /**
     * 构造分页结果并计算总页数。
     *
     * @param rows  当前页数据
     * @param total 总行数
     * @param page  当前页码
     * @param size  每页条数
     * @return 分页结果
     */
    public static PageResult of(List<Map<String, Object>> rows, long total, int page, int size) {
        int pages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResult(rows, total, page, size, pages);
    }
}
