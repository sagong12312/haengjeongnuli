package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.ResultType;
import egovframework.haengjeongnuli.epetition.EPetitionAuthFilter;

/** 행정누리 전자민원 연동 API */
@SpringBootTest
class EPetitionApiTest {

    static final byte[] SECRET = "test-secret-for-epetition-api-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired WebApplicationContext ctx;
    @Autowired CivilProcessService process;
    final ObjectMapper json = new ObjectMapper();

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    /** 전자민원 서버가 하는 것처럼 서명해 보낸다 */
    static MockHttpServletRequestBuilder signed(String method, String path, String body, String nonce) {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String sig = EPetitionAuthFilter.sign(SECRET, method, path, ts, nonce, b);
        MockHttpServletRequestBuilder r = "POST".equals(method) ? post(path) : get(path);
        if (body != null) {
            r.contentType(MediaType.APPLICATION_JSON).content(b);
        }
        return r.header("X-EP-Timestamp", ts).header("X-EP-Nonce", nonce).header("X-EP-Signature", sig).header("X-EP-Client-IP", "203.0.113.7");
    }

    static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    JsonNode call(MockHttpServletRequestBuilder r, int expect) throws Exception {
        MvcResult res = mvc().perform(r).andExpect(status().is(expect)).andReturn();
        return json.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void 접수하고_비밀번호로_조회하고_공개_상담으로_게시한다() throws Exception {
        String title = "가로등 고장 " + UUID.randomUUID().toString().substring(0, 6);
        String body = "{\"kind\":\"OTHER\",\"name\":\"홍길동\",\"mobile\":\"010-1234-5678\",\"title\":\"" + title + "\","
                + "\"content\":\"중앙로 가로등이 꺼져 있습니다.\",\"password\":\"abc12345\",\"publicConsent\":true,\"privacyConsent\":true}";

        // 서명 없이·틀린 서명·다른 경로 서명은 막는다
        mvc().perform(post("/api/epetition/v1/complaints").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc().perform(post("/api/epetition/v1/complaints").contentType(MediaType.APPLICATION_JSON).content(body + " ")
                .header("X-EP-Timestamp", Instant.now().getEpochSecond()).header("X-EP-Nonce", nonce())
                .header("X-EP-Signature", "00")).andExpect(status().isUnauthorized());
        // 다른 곳에서 오면 막는다
        mvc().perform(signed("POST", "/api/epetition/v1/complaints", body, nonce()).with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                .andExpect(status().isForbidden());

        // 비밀번호 규칙
        assertThat(call(signed("POST", "/api/epetition/v1/complaints", body.replace("abc12345", "1234"), nonce()), 422)
                .get("error").asText()).isEqualTo("invalid_password");
        // 법정민원은 받지 않는다
        assertThat(call(signed("POST", "/api/epetition/v1/complaints", body.replace("OTHER", "LEGAL"), nonce()), 422)
                .get("error").asText()).isEqualTo("invalid_kind");

        String n = nonce();
        JsonNode ok = call(signed("POST", "/api/epetition/v1/complaints", body, n), 200);
        String receiptNo = ok.get("data").get("receiptNo").asText();
        assertThat(receiptNo).matches("\\d{4}-\\d{6}");
        assertThat(ok.get("data").has("complaintId")).isFalse();
        // 같은 논스로 다시 보내면 막는다 (재전송)
        call(signed("POST", "/api/epetition/v1/complaints", body, n), 401);

        // 조회: 틀린 비밀번호, 없는 번호는 같은 답
        String look = "{\"receiptNo\":\"" + receiptNo + "\",\"password\":\"%s\"}";
        assertThat(call(signed("POST", "/api/epetition/v1/complaints/lookup", look.formatted("wrong999"), nonce()), 401)
                .get("error").asText()).isEqualTo("not_matched");
        assertThat(call(signed("POST", "/api/epetition/v1/complaints/lookup", "{\"receiptNo\":\"1999-999999\",\"password\":\"abc12345\"}", nonce()), 401)
                .get("error").asText()).isEqualTo("not_matched");
        JsonNode d = call(signed("POST", "/api/epetition/v1/complaints/lookup", look.formatted("abc12345"), nonce()), 200).get("data");
        assertThat(d.get("title").asText()).isEqualTo(title);
        assertThat(d.get("statusLabel").asText()).isEqualTo("접수");
        assertThat(d.get("steps").get(0).get("step").asText()).isEqualTo("접수");

        // 처리 완료 → 결과가 보이고, 게시하면 공개 상담 목록에 이름을 가려 나온다
        long id = process.get(Long.parseLong(receiptNo.substring(5))) != null ? Long.parseLong(receiptNo.substring(5)) : -1;
        assertThat(process.complete(id, ResultType.ANSWER, "가로등을 고쳤습니다.", null, "문자", TestUsers.admin()).ok()).isTrue();
        d = call(signed("POST", "/api/epetition/v1/complaints/lookup", look.formatted("abc12345"), nonce()), 200).get("data");
        assertThat(d.get("result").get("text").asText()).isEqualTo("가로등을 고쳤습니다.");
        assertThat(d.toString()).doesNotContain("admin-t"); // 담당 직원 아이디는 보내지 않는다

        assertThat(call(signed("GET", "/api/epetition/v1/consultations/" + id, null, nonce()), 404).get("error").asText()).isEqualTo("not_found");
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/civil/" + id + "/publish").param("on", "true"));
        String tag = title.substring(title.length() - 6);
        JsonNode list = call(signed("GET", "/api/epetition/v1/consultations?page=1&q=" + tag, null, nonce()), 200).get("data");
        assertThat(list.get("total").asInt()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("name").asText()).isEqualTo("홍*동");
        assertThat(call(signed("GET", "/api/epetition/v1/consultations/" + id, null, nonce()), 200).get("data").get("result").get("text").asText())
                .isEqualTo("가로등을 고쳤습니다.");

        // 다섯 번 틀리면 잠긴다 (맞는 비밀번호도 잠시 안 됨)
        for (int i = 0; i < 5; i++) {
            call(signed("POST", "/api/epetition/v1/complaints/lookup", look.formatted("wrong999"), nonce()), 401);
        }
        assertThat(call(signed("POST", "/api/epetition/v1/complaints/lookup", look.formatted("abc12345"), nonce()), 423).get("error").asText())
                .isEqualTo("locked");

        // 직원 화면 경로는 이 연동 인증으로 열리지 않는다
        mvc().perform(signed("GET", "/civil", null, nonce())).andExpect(status().is3xxRedirection());
    }
}
