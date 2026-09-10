
package com.jingxuan.plant;

import com.jingxuan.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 植物平台主闭环集成测试（改造文档 24.2 / 35 验收场景，Testcontainers 环境运行）。 */
class PlantPlatformApiTest extends BaseApiTest {

    private Map<String, Object> json(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("公开种子与展廊数据可访问且只暴露 APPROVED")
    void publicSeedsAndGallery() {
        var provinces = publicApi.get("/api/public/plant/regions/provinces");
        provinces.assertOk();
        assertTrue(provinces.getDataNode().size() >= 34, "V10 应写入 34 个省级行政区");

        var search = publicApi.get("/api/public/plant/species/search?keyword=" + java.net.URLEncoder.encode("银杏", java.nio.charset.StandardCharsets.UTF_8));
        search.assertOk();
        boolean hit = false;
        for (var node : search.getDataNode().get("records")) {
            if ("银杏".equals(node.get("commonName").asText())) {
                hit = true;
            }
        }
        assertTrue(hit, "物种库应命中银杏种子");

        var gallery = publicApi.get("/api/public/plant/gallery");
        gallery.assertOk();
        boolean approvedVisible = false;
        boolean draftHidden = true;
        for (var node : gallery.getDataNode().get("records")) {
            String id = node.get("observationId").asText();
            if ("9002".equals(id)) {
                approvedVisible = true;
                assertTrue(node.get("featured").asBoolean(), "种子 9002 应带精选标记");
            }
            if ("9001".equals(id)) {
                draftHidden = false;
            }
        }
        assertTrue(approvedVisible, "APPROVED 记录应出现在展廊");
        assertTrue(draftHidden, "SUBMITTED/未公开记录不得出现在展廊");
    }

    @Test
    @DisplayName("学生草稿生命周期：创建/列表/无照片提交被拒/他人不可改")
    void studentDraftLifecycle() {
        var create = testStuApi.post("/api/student/plant/observations", json(
                "provinceCode", "330000", "cityCode", "330100", "districtCode", "330106",
                "locationText", "测试地点"));
        create.assertOk();
        String id = create.getDataText("id");
        assertNotNull(id);

        var list = testStuApi.get("/api/student/plant/observations?status=DRAFT");
        list.assertOk();
        boolean found = false;
        for (var node : list.getDataNode().get("records")) {
            if (id.equals(node.get("id").asText())) {
                found = true;
            }
        }
        assertTrue(found, "新草稿应出现在本人列表");

        var submit = testStuApi.post("/api/student/plant/observations/" + id + "/submit", json());
        assertTrue(submit.getCode() != 200, "无照片草稿提交必须被服务端拒绝");
        assertNotNull(submit.getMessage());
        assertTrue(submit.getMessage().contains("照片"), "拒绝信息应提示缺照片: " + submit.getMessage());

        var forbidden = studentApi.put("/api/student/plant/observations/" + id, json(
                "provinceCode", "330000", "description", "他人篡改"));
        assertTrue(forbidden.getCode() != 200, "学生不能修改他人草稿");
    }

    @Test
    @DisplayName("教师审核通过 -> 展廊/首页/地图同步更新")
    void teacherApprovalEntersGalleryAndMap() {
        var reviews = teacherApi.get("/api/teacher/plant/reviews?status=SUBMITTED");
        reviews.assertOk();
        boolean found = false;
        for (var node : reviews.getDataNode().get("records")) {
            if ("9001".equals(node.get("observationId").asText())) {
                found = true;
            }
        }
        assertTrue(found, "待审列表应包含种子 9001");

        var approve = teacherApi.post("/api/teacher/plant/reviews/9001/approve", json(
                "action", "APPROVED", "comment", "照片清晰，审核通过"));
        approve.assertOk();

        var featured = teacherApi.post("/api/teacher/plant/observations/9001/featured?featured=true", json());
        featured.assertOk();

        var gallery = publicApi.get("/api/public/plant/gallery?featured=true");
        gallery.assertOk();
        boolean inGallery = false;
        for (var node : gallery.getDataNode().get("records")) {
            if ("9001".equals(node.get("observationId").asText())) {
                inGallery = true;
                assertTrue(node.get("featured").asBoolean(), "通过后应带精选标记");
            }
        }
        assertTrue(inGallery, "审核通过后应进入精选展廊");

        var detail = publicApi.get("/api/public/plant/observations/9001");
        detail.assertOk();
        assertTrue(detail.getDataNode().get("photos").size() >= 1, "详情应含种子照片");
        assertEquals("照片清晰，审核通过", detail.getDataText("reviewComment"));

        var map = publicApi.get("/api/public/plant/map/china");
        map.assertOk();
        boolean zj = false;
        for (var node : map.getDataNode()) {
            if ("330000".equals(node.get("provinceCode").asText())) {
                zj = true;
                assertTrue(Integer.parseInt(node.get("observationCount").asText()) >= 2,
                        "浙江省统计应包含已通过的两条记录");
            }
        }
        assertTrue(zj, "中国地图应包含浙江省");
    }

    @Test
    @DisplayName("教师驳回 -> 学生可见并修改重提 -> 再审核通过")
    void teacherRejectThenResubmit() {
        var reject = teacherApi.post("/api/teacher/plant/reviews/9003/reject", json(
                "action", "REJECTED", "comment", "请补充花部特写"));
        reject.assertOk();

        var history = teacherApi.get("/api/teacher/plant/reviews/9003/history");
        history.assertOk();
        assertEquals(1, history.getDataNode().size(), "每次审核都应留历史");

        var mine = testStuApi.get("/api/student/plant/observations?status=REJECTED");
        mine.assertOk();
        boolean rejectedVisible = false;
        for (var node : mine.getDataNode().get("records")) {
            if ("9003".equals(node.get("id").asText())) {
                rejectedVisible = true;
            }
        }
        assertTrue(rejectedVisible, "驳回记录应回到学生端可见");

        var resubmit = testStuApi.post("/api/student/plant/observations/9003/submit", json());
        resubmit.assertOk();
        var approve = teacherApi.post("/api/teacher/plant/reviews/9003/approve", json(
                "action", "APPROVED", "comment", "补充后通过"));
        approve.assertOk();
    }

    @Test
    @DisplayName("师生评论/回复/置顶与 1~5 星评分")
    void communityInteraction() {
        var comment = studentApi.post("/api/community/plant/observations/9002/comments", json(
                "content", "拍得真清楚！"));
        comment.assertOk();
        String commentId = comment.getDataText("id");

        var reply = teacherApi.post("/api/community/plant/observations/9002/comments", json(
                "content", "这是值得推广的观察样本", "parentId", commentId));
        reply.assertOk();
        assertEquals(commentId, reply.getDataText("parentId"), "回复应挂到父评论");

        var teacherComment = teacherApi.post("/api/community/plant/observations/9002/comments", json(
                "content", "教师点评：建议补充叶缘特写"));
        teacherComment.assertOk();
        String teacherCommentId = teacherComment.getDataText("id");
        var pin = teacherApi.post("/api/community/plant/comments/" + teacherCommentId + "/pin?pinned=true", json());
        pin.assertOk();

        var comments = publicApi.get("/api/public/plant/observations/9002/comments");
        comments.assertOk();
        boolean pinnedVisible = false;
        for (var node : comments.getDataNode()) {
            if (teacherCommentId.equals(node.get("commentId").asText()) && node.get("isPinned").asBoolean()) {
                pinnedVisible = true;
            }
        }
        assertTrue(pinnedVisible, "置顶教师点评应公开可见");

        studentApi.post("/api/community/plant/observations/9002/rating", json("score", 5)).assertOk();
        studentApi.post("/api/community/plant/observations/9002/rating", json("score", 4)).assertOk();
        teacherApi.post("/api/community/plant/observations/9002/rating", json("score", 5)).assertOk();

        var detail = publicApi.get("/api/public/plant/observations/9002");
        detail.assertOk();
        assertEquals("2", detail.getDataText("ratingCount"), "两个用户评分后应只有一人一条");
        assertEquals("4.5", detail.getDataText("averageRating"), "平均分应为 4.5");
    }

    @Test
    @DisplayName("越权与治理：学生不可审核、匿名不可评论、管理员可下线")
    void authorizationAndGovernance() {
        assertEquals(HttpStatus.FORBIDDEN,
                studentApi.exchange(HttpMethod.GET, "/api/teacher/plant/reviews", null).getStatusCode(),
                "学生访问教师审核接口应 403");

        assertEquals(HttpStatus.UNAUTHORIZED,
                publicApi.exchange(HttpMethod.POST, "/api/community/plant/observations/9002/comments", json(
                        "content", "匿名评论")).getStatusCode(),
                "匿名评论应 401");

        var adminOffline = adminApi.post("/api/admin/plant/observations/9002/offline", json());
        adminOffline.assertOk();
        var gallery = publicApi.get("/api/public/plant/gallery");
        gallery.assertOk();
        for (var node : gallery.getDataNode().get("records")) {
            assertFalse("9002".equals(node.get("observationId").asText()),
                    "下线记录应从展廊消失");
        }
        adminApi.post("/api/admin/plant/observations/9002/online", json()).assertOk();
    }
}
