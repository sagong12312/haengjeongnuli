package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.security.AllowedNetworkFilter;

@SpringBootTest
class AllowedNetworkTest {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestUsers.mvcAs(ctx, TestUsers.general());
    }

    private static RequestPostProcessor from(String ip) {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }

    @Test
    void 이_PC와_집_내부망은_접속된다() throws Exception {
        mvc.perform(get("/").with(from("127.0.0.1"))).andExpect(status().isOk());
        mvc.perform(get("/").with(from("192.168.0.23"))).andExpect(status().isOk());
        mvc.perform(get("/login").with(from("10.0.0.5"))).andExpect(status().isOk());
    }

    @Test
    void 인터넷에서_오는_접속은_막힌다() throws Exception {
        mvc.perform(get("/").with(from("8.8.8.8")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("접속할 수 없습니다")));
        mvc.perform(get("/login").with(from("203.0.113.7"))).andExpect(status().isForbidden());
        mvc.perform(get("/css/app.css").with(from("2001:db8::1"))).andExpect(status().isForbidden());
    }

    @Test
    void 헤더로_주소를_속여도_막힌다() throws Exception {
        mvc.perform(get("/").with(from("8.8.8.8")).header("X-Forwarded-For", "127.0.0.1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 설정으로_이_PC만_허용할_수_있다() {
        AllowedNetworkFilter onlyThisPc = new AllowedNetworkFilter("127.0.0.1/32,::1/128");
        assertThat(onlyThisPc.isAllowed("127.0.0.1")).isTrue();
        assertThat(onlyThisPc.isAllowed("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(onlyThisPc.isAllowed("192.168.0.23")).isFalse();
        assertThat(onlyThisPc.isAllowed("fe80::1%eth0")).isFalse();
        assertThat(onlyThisPc.isAllowed(null)).isFalse();
    }
}
