package com.jingxuan.plant;

/** 公开端姓名展示策略（V4 §109）：默认展示真实姓名，可配置掩码。 */
public final class PlantPrivacy {

    private PlantPrivacy() {}

    public static String displayName(String realName, boolean showRealName) {
        if (realName == null || realName.isBlank() || showRealName) {
            return realName;
        }
        if (realName.length() == 1) {
            return "*";
        }
        // 张三 -> 张*；欧阳娜娜 -> 欧阳*娜
        if (realName.length() == 2) {
            return realName.charAt(0) + "*";
        }
        return realName.substring(0, 1) + "*".repeat(realName.length() - 2) + realName.charAt(realName.length() - 1);
    }
}
