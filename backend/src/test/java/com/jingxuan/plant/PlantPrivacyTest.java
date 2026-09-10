package com.jingxuan.plant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 公开端姓名策略（V4 下一步开发计划 §5.1）：花名优先，未设置时按配置展示真名或掩码。 */
class PlantPrivacyTest {

    @Test
    void nicknameWinsOverRealName() {
        assertEquals("青禾", PlantPrivacy.publicName("张三", "青禾", true));
        assertEquals("青禾", PlantPrivacy.publicName("张三", "  青禾  ", false));
    }

    @Test
    void fallsBackToRealNameWhenNicknameMissing() {
        assertEquals("张三", PlantPrivacy.publicName("张三", null, true));
        assertEquals("张三", PlantPrivacy.publicName("张三", "   ", true));
    }

    @Test
    void fallsBackToMaskWhenPrivacyEnabled() {
        assertEquals("张*", PlantPrivacy.publicName("张三", null, false));
        assertEquals("欧**娜", PlantPrivacy.displayName("欧阳娜娜", false));
        assertEquals("*", PlantPrivacy.displayName("李", false));
    }

    @Test
    void blankNameStaysBlank() {
        assertEquals(null, PlantPrivacy.displayName(null, false));
        assertEquals("  ", PlantPrivacy.displayName("  ", false));
    }
}
