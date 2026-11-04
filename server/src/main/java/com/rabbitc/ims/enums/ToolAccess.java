package com.rabbitc.ims.enums;

/** 累积式 AI 工具权限。业务角色、数据范围和文件写入授权仍需独立通过。 */
public enum ToolAccess {
    READ_ONLY("仅读取"),
    ADD("可添加"),
    ADD_EDIT("可添加编辑"),
    FULL("所有权限");

    private final String label;

    ToolAccess(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean allows(ToolAccess required) {
        return ordinal() >= required.ordinal();
    }
}
