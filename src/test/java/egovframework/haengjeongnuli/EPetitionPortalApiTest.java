package egovframework.haengjeongnuli;

import static egovframework.haengjeongnuli.EPetitionApiTest.nonce;
import static egovframework.haengjeongnuli.EPetitionApiTest.signed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.ResultType;

/** 전자민원 포털: 공지·자주 묻는 질문·안내·통계, 공개 상담 공감·조회수, 취하·추가 의견·만족도, 회원 */
@SpringBootTest
class EPetitionPortalApiTest {

    static final String API = "/api/epetition/v1";

    @Autowired WebApplicationContext ctx;
    @Autowired CivilProcessService process;
    final ObjectMapper json = new ObjectMapper();

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    JsonNode call(MockHttpServletRequestBuilder r, int expect) throws Exception {
        MvcResult res = mvc().perform(r).andExpect(status().is(expect)).andReturn();
        return json.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode apiGet(String path, int expect) throws Exception {
        return call(signed("GET", API + path, null, nonce()), expect);
    }

    JsonNode apiGet(String path, int expect, String ip) throws Exception {
        return call(signed("GET", API + path, null, nonce(), ip), expect);
    }

    JsonNode apiPost(String path, String body, int expect) throws Exception {
        return call(signed("POST", API + path, body, nonce()), expect);
    }

    JsonNode apiPost(String path, String body, int expect, String ip) throws Exception {
        return call(signed("POST", API + path, body, nonce(), ip), expect);
    }

    /** 비회원 접수 → 접수번호 */
    String receive(String title, String extra) throws Exception {
        String body = "{\"kind\":\"OTHER\",\"name\":\"김민원\",\"mobile\":\"01012345678\",\"title\":\"" + title + "\","
                + "\"content\":\"내용\",\"password\":\"abc12345\",\"publicConsent\":true,\"privacyConsent\":true" + extra + "}";
        return apiPost("/complaints", body, 200).get("data").get("receiptNo").asText();
    }

    JsonNode lookup(String receiptNo) throws Exception {
        return apiPost("/complaints/lookup", "{\"receiptNo\":\"" + receiptNo + "\",\"password\":\"abc12345\"}", 200).get("data");
    }

    static long idOf(String receiptNo) {
        return Long.parseLong(receiptNo.substring(5));
    }

    @Test
    void 안내와_통계와_공지와_자주_묻는_질문() throws Exception {
        JsonNode info = apiGet("/info", 200).get("data");
        assertThat(info.get("kinds").toString()).contains("질의민원").contains("\"online\":false");
        JsonNode st = apiGet("/stats", 200).get("data");
        assertThat(st.get("months").size()).isEqualTo(12);
        assertThat(st.toString()).doesNotContain("김민원");

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String t = "설 연휴 민원실 운영 " + UUID.randomUUID().toString().substring(0, 6);
        admin.perform(post("/epetition/notices").param("title", t).param("content", "1월 28일~30일은 쉽니다.").param("pinned", "true"))
                .andExpect(status().is3xxRedirection());
        JsonNode list = apiGet("/notices?page=1&q=" + t.substring(t.length() - 6), 200).get("data");
        assertThat(list.get("total").asInt()).isEqualTo(1);
        long nid = list.get("items").get(0).get("id").asLong();
        assertThat(list.get("items").get(0).get("pinned").asBoolean()).isTrue();
        assertThat(apiGet("/notices/" + nid, 200).get("data").get("content").asText()).contains("쉽니다");
        apiGet("/notices/" + nid, 200); // 같은 사람이 다시 열면 조회수를 더 세지 않는다
        assertThat(apiGet("/notices/" + nid, 200, "198.51.100.9").get("data").get("views").asInt()).isEqualTo(2);

        admin.perform(post("/epetition/faqs").param("category", "민원 신청").param("question", "첨부파일은 몇 개까지?")
                .param("answer", "3개, 하나에 5MB까지입니다.").param("sortOrder", "1").param("use", "true"));
        admin.perform(post("/epetition/faqs").param("category", "민원 신청").param("question", "숨긴 질문")
                .param("answer", "안 보여야 함").param("sortOrder", "2"));
        String faqs = apiGet("/faqs", 200).get("data").toString();
        assertThat(faqs).contains("첨부파일은 몇 개까지?").doesNotContain("숨긴 질문");

        // 일반 직원은 쓰지 못한다
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(post("/epetition/notices").param("title", "x").param("content", "y"))
                .andExpect(status().isForbidden());
        admin.perform(post("/epetition/notices/" + nid + "/delete"));
        apiGet("/notices/" + nid, 404);
    }

    @Test
    void 공개_상담_공감과_조회수와_비슷한_상담() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String a = receive("공감 시험 A " + tag, "");
        String b = receive("공감 시험 B " + tag, "");
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        for (String no : new String[] {a, b}) {
            assertThat(process.complete(idOf(no), ResultType.ANSWER, "답변", null, "문자", TestUsers.admin()).ok()).isTrue();
            admin.perform(post("/civil/" + idOf(no) + "/publish").param("on", "true"));
        }
        JsonNode d = apiGet("/consultations/" + idOf(a), 200, "192.0.2.10").get("data");
        assertThat(d.get("views").asInt()).isEqualTo(1);
        assertThat(d.get("liked").asBoolean()).isFalse();
        assertThat(d.get("related").toString()).contains("공감 시험 B");
        apiGet("/consultations/" + idOf(a), 200, "192.0.2.10");
        assertThat(apiGet("/consultations/" + idOf(a), 200, "192.0.2.11").get("data").get("views").asInt()).isEqualTo(2);

        assertThat(apiPost("/consultations/" + idOf(a) + "/like", null, 200, "192.0.2.10").get("data").get("added").asBoolean()).isTrue();
        JsonNode again = apiPost("/consultations/" + idOf(a) + "/like", null, 200, "192.0.2.10").get("data");
        assertThat(again.get("added").asBoolean()).isFalse();
        assertThat(again.get("likes").asInt()).isEqualTo(1);
        assertThat(apiGet("/consultations/" + idOf(a), 200, "192.0.2.10").get("data").get("liked").asBoolean()).isTrue();

        JsonNode byLikes = apiGet("/consultations?page=1&sort=likes&kind=OTHER&q=" + tag, 200).get("data");
        assertThat(byLikes.get("total").asInt()).isEqualTo(2);
        assertThat(byLikes.get("items").get(0).get("title").asText()).contains("공감 시험 A");
        assertThat(apiGet("/consultations?page=1&kind=QUERY&q=" + tag, 200).get("data").get("total").asInt()).isZero();
        // 공개 안 된 글에는 공감할 수 없다
        String c = receive("미공개 " + tag, "");
        apiPost("/consultations/" + idOf(c) + "/like", null, 404);
    }

    @Test
    void 처리_전_취하와_추가_의견_처리_뒤_만족도() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String no = receive("추가 의견 시험 " + tag, "");
        JsonNode d = lookup(no);
        String token = d.get("actionToken").asText();
        assertThat(d.get("canWithdraw").asBoolean()).isTrue();
        assertThat(d.get("canRate").asBoolean()).isFalse();
        apiPost("/complaints/addendum", "{\"token\":\"bad\",\"text\":\"x\"}", 401);
        apiPost("/complaints/addendum", "{\"token\":\"" + token + "\",\"text\":\"\"}", 422);
        for (int i = 1; i <= 5; i++) {
            assertThat(apiPost("/complaints/addendum", "{\"token\":\"" + token + "\",\"text\":\"사진 위치는 " + i + "번 출구입니다.\"}", 200)
                    .get("data").get("count").asInt()).isEqualTo(i);
        }
        assertThat(apiPost("/complaints/addendum", "{\"token\":\"" + token + "\",\"text\":\"여섯 번째\"}", 409).get("error").asText()).isEqualTo("too_many");
        d = lookup(no);
        assertThat(d.get("canAddendum").asBoolean()).isFalse();
        assertThat(d.get("steps").toString()).contains("민원인 추가 의견").contains("1번 출구");
        assertThat(process.history(idOf(no)).stream().filter(h -> "ADDENDUM".equals(h.getAction())).count()).isEqualTo(5);

        // 만족도: 처리 전에는 안 되고, 처리 뒤 한 번만
        apiPost("/complaints/satisfaction", "{\"token\":\"" + token + "\",\"score\":5}", 409);
        assertThat(process.complete(idOf(no), ResultType.ACCEPT, "고쳤습니다.", null, "문자", TestUsers.admin()).ok()).isTrue();
        token = lookup(no).get("actionToken").asText();
        apiPost("/complaints/satisfaction", "{\"token\":\"" + token + "\",\"score\":9}", 422);
        apiPost("/complaints/satisfaction", "{\"token\":\"" + token + "\",\"score\":4,\"comment\":\"빨리 고쳐 주셔서 고맙습니다.\"}", 200);
        apiPost("/complaints/satisfaction", "{\"token\":\"" + token + "\",\"score\":1}", 409);
        d = lookup(no);
        assertThat(d.get("canRate").asBoolean()).isFalse();
        assertThat(d.get("satisfaction").get("score").asInt()).isEqualTo(4);
        assertThat(apiGet("/stats", 200).get("data").get("satisfactionCount").asInt()).isGreaterThanOrEqualTo(1);
        // 처리 뒤에는 취하할 수 없다
        apiPost("/complaints/withdraw", "{\"token\":\"" + token + "\"}", 409);

        // 취하: 처리 완료(취하)가 되고, 공개 상담에 게시할 수 없다
        String w = receive("취하 시험 " + tag, "");
        String wt = lookup(w).get("actionToken").asText();
        JsonNode wd = apiPost("/complaints/withdraw", "{\"token\":\"" + wt + "\",\"reason\":\"스스로 해결했습니다.\"}", 200).get("data");
        assertThat(wd.get("receiptNo").asText()).isEqualTo(w);
        JsonNode after = lookup(w);
        assertThat(after.get("result").get("type").asText()).isEqualTo("취하");
        assertThat(after.get("canWithdraw").asBoolean()).isFalse();
        assertThat(after.get("canRate").asBoolean()).isFalse();
        assertThat(process.get(idOf(w)).isDone()).isTrue();
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(post("/civil/" + idOf(w) + "/publish").param("on", "true"));
        apiGet("/consultations/" + idOf(w), 404);
        apiPost("/complaints/withdraw", "{\"token\":\"" + wt + "\"}", 409);
    }

    @Test
    void 민원_분야로_접수하고_공개_상담을_분야로_거른다() throws Exception {
        assertThat(apiGet("/info", 200).get("data").get("categories").toString()).contains("도로·교통").contains("ANIMAL");
        String tag = UUID.randomUUID().toString().substring(0, 6);
        assertThat(apiPost("/complaints", ("{\"kind\":\"OTHER\",\"category\":\"NOPE\",\"name\":\"김\",\"mobile\":\"01012345678\","
                + "\"title\":\"t\",\"content\":\"c\",\"password\":\"abc12345\",\"privacyConsent\":true}"), 422).get("error").asText())
                .isEqualTo("invalid_category");
        String road = receive("포트홀 " + tag, ",\"category\":\"ROAD\"");
        String none = receive("분야 없음 " + tag, "");
        assertThat(process.get(idOf(road)).getCategoryLabel()).isEqualTo("도로·교통");
        assertThat(process.get(idOf(none)).getCategoryLabel()).isEqualTo("기타");
        assertThat(lookup(road).get("categoryLabel").asText()).isEqualTo("도로·교통");
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        for (String no : new String[] {road, none}) {
            assertThat(process.complete(idOf(no), ResultType.ANSWER, "답변", null, "문자", TestUsers.admin()).ok()).isTrue();
            admin.perform(post("/civil/" + idOf(no) + "/publish").param("on", "true"));
        }
        JsonNode l = apiGet("/consultations?page=1&category=ROAD&q=" + tag, 200).get("data");
        assertThat(l.get("total").asInt()).isEqualTo(1);
        assertThat(l.get("items").get(0).get("categoryLabel").asText()).isEqualTo("도로·교통");
        assertThat(apiGet("/stats", 200).get("data").get("categories").toString()).contains("도로·교통");
        // 직원 목록도 분야로 거른다
        String html = admin.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/civil").param("category", "ROAD").param("q", tag))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("포트홀 " + tag).doesNotContain("분야 없음 " + tag);
    }

    @Test
    void 회원_가입_로그인_내_민원_정보_변경_탈퇴() throws Exception {
        String id = "user" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[^a-z0-9]", "a");
        String base = "{\"loginId\":\"%s\",\"password\":\"%s\",\"name\":\"이회원\",\"mobile\":\"01098765432\",\"email\":\"lee@example.com\","
                + "\"termsConsent\":true,\"privacyConsent\":true}";
        assertThat(apiPost("/members", base.formatted("1bad", "Tq7-Rivers-Blue!"), 422).get("error").asText()).isEqualTo("invalid_login_id");
        assertThat(apiPost("/members", base.formatted(id, "abcdefgh"), 422).get("error").asText()).isEqualTo("invalid_password");
        assertThat(apiPost("/members", base.formatted(id, id + "Xy9!"), 422).get("error").asText()).isEqualTo("invalid_password");
        apiPost("/members", base.formatted(id, "Tq7-Rivers-Blue!").replace("\"termsConsent\":true", "\"termsConsent\":false"), 422);
        JsonNode m = apiPost("/members", base.formatted(id, "Tq7-Rivers-Blue!"), 200).get("data");
        long mid = m.get("memberId").asLong();
        assertThat(m.get("mobile").asText()).isEqualTo("010-9876-5432");
        assertThat(m.has("hash")).isFalse();
        assertThat(apiPost("/members", base.formatted(id, "Tq7-Rivers-Blue!"), 409).get("error").asText()).isEqualTo("login_id_taken");

        String login = "{\"loginId\":\"" + id + "\",\"password\":\"%s\"}";
        assertThat(apiPost("/members/login", login.formatted("wrong-Pass-1"), 401).get("error").asText()).isEqualTo("not_matched");
        assertThat(apiPost("/members/login", "{\"loginId\":\"nobody99\",\"password\":\"x\"}", 401).get("error").asText()).isEqualTo("not_matched");
        assertThat(apiPost("/members/login", login.formatted("Tq7-Rivers-Blue!"), 200).get("data").get("memberId").asLong()).isEqualTo(mid);

        // 회원 신청: 조회 비밀번호 없이도 되고, 내 민원에 나온다
        String body = "{\"kind\":\"QUERY\",\"name\":\"이회원\",\"mobile\":\"010-9876-5432\",\"title\":\"회원 민원\",\"content\":\"내용\","
                + "\"privacyConsent\":true,\"memberId\":" + mid + "}";
        String no = apiPost("/complaints", body, 200).get("data").get("receiptNo").asText();
        apiPost("/complaints", body.replace("\"memberId\":" + mid, "\"memberId\":999999999"), 404);
        JsonNode mine = apiGet("/members/" + mid + "/complaints?page=1", 200).get("data");
        assertThat(mine.get("total").asInt()).isEqualTo(1);
        assertThat(mine.get("items").get(0).get("receiptNo").asText()).isEqualTo(no);
        JsonNode one = apiGet("/members/" + mid + "/complaints/" + no, 200).get("data");
        assertThat(one.get("title").asText()).isEqualTo("회원 민원");
        assertThat(one.get("actionToken").asText()).hasSize(32);
        // 남의 민원은 안 보인다
        String other = receive("남의 민원", "");
        apiGet("/members/" + mid + "/complaints/" + other, 404);

        JsonNode up = apiPost("/members/" + mid + "/profile", "{\"name\":\"이회원\",\"mobile\":\"\",\"email\":\"new@example.com\",\"address\":\"시청로 1\"}", 200).get("data");
        assertThat(up.get("email").asText()).isEqualTo("new@example.com");
        assertThat(up.get("mobile").isNull()).isTrue();
        apiPost("/members/" + mid + "/profile", "{\"name\":\"이회원\",\"mobile\":\"123\"}", 422);

        apiPost("/members/" + mid + "/password", "{\"currentPassword\":\"wrong\",\"newPassword\":\"New-Pass-2026!\"}", 422);
        apiPost("/members/" + mid + "/password", "{\"currentPassword\":\"Tq7-Rivers-Blue!\",\"newPassword\":\"New-Pass-2026!\"}", 200);
        apiPost("/members/login", login.formatted("Tq7-Rivers-Blue!"), 401);
        apiPost("/members/login", login.formatted("New-Pass-2026!"), 200);

        // 다섯 번 틀리면 잠긴다
        for (int i = 0; i < 5; i++) {
            apiPost("/members/login", login.formatted("wrong-Pass-1"), 401);
        }
        assertThat(apiPost("/members/login", login.formatted("New-Pass-2026!"), 423).get("error").asText()).isEqualTo("locked");

        // 탈퇴: 회원 정보는 지우고 민원은 남긴다
        apiPost("/members/" + mid + "/delete", "{\"password\":\"wrong\"}", 422);
        assertThat(apiPost("/members/" + mid + "/delete", "{\"password\":\"New-Pass-2026!\"}", 200).get("data").get("complaints").asInt()).isEqualTo(1);
        apiGet("/members/" + mid, 404);
        assertThat(process.get(idOf(no))).isNotNull();
        // 같은 아이디로 다시 가입할 수 있다
        apiPost("/members", base.formatted(id, "Tq7-Rivers-Blue!"), 200);
    }
}
