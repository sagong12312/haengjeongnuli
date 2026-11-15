package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.dept.service.DeptService;
import egovframework.haengjeongnuli.dept.service.DeptVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class DeptTest {

    @Autowired WebApplicationContext ctx;
    @Autowired DeptService depts;
    @Autowired UserService users;

    private static String uniq(String p) {
        return p + UUID.randomUUID().toString().substring(0, 6);
    }

    private DeptVO find(String name) {
        return depts.tree().stream().filter(d -> d.getDeptName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void 관리자만_부서를_관리하고_조직도_순서로_보인다() throws Exception {
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/admin/depts")).andExpect(status().isForbidden());
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String bureau = uniq("자치행정국"), team = uniq("총무과");
        admin.perform(post("/admin/depts").param("deptName", bureau))
                .andExpect(flash().attribute("message", containsString("만들었습니다")));
        admin.perform(post("/admin/depts").param("deptName", team).param("parentId", find(bureau).getDeptId().toString()));
        admin.perform(post("/admin/depts").param("deptName", " " + bureau + " "))
                .andExpect(flash().attribute("message", containsString("이미 있는 부서")));

        DeptVO t = find(team);
        assertThat(t.getParentName()).isEqualTo(bureau);
        assertThat(t.getDepth()).isEqualTo(1);
        int bi = depts.tree().indexOf(find(bureau)), ti = depts.tree().stream().map(DeptVO::getDeptName).toList().indexOf(team);
        assertThat(depts.tree().stream().map(DeptVO::getDeptName).toList().indexOf(bureau)).isEqualTo(ti - 1);
        assertThat(bi).isLessThanOrEqualTo(ti);

        // 자기 하위 부서를 상위로 둘 수 없다
        admin.perform(post("/admin/depts/" + find(bureau).getDeptId()).param("parentId", t.getDeptId().toString()))
                .andExpect(flash().attribute("message", containsString("하위 부서를 상위 부서로 둘 수 없습니다")));
        admin.perform(get("/admin/depts")).andExpect(content().string(containsString(team)));
    }

    @Test
    void 직원이_있거나_하위_부서가_있으면_폐지할_수_없다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String parent = uniq("복지국"), dept = uniq("복지과");
        depts.create(parent, null, null, 0);
        depts.create(dept, find(parent).getDeptId(), "1234567", 1);
        String id = uniq("d");
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("부서원");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Dept-member-7!");
        LoginUser u = users.create(f);

        admin.perform(post("/admin/depts/" + find(dept).getDeptId() + "/use").param("value", "false"))
                .andExpect(flash().attribute("message", containsString("사용 중인 직원 계정이 있습니다")));
        admin.perform(post("/admin/depts/" + find(parent).getDeptId() + "/use").param("value", "false"))
                .andExpect(flash().attribute("message", containsString("하위 부서가 있습니다")));

        users.setEnabled(u.getUserId(), false);
        admin.perform(post("/admin/depts/" + find(dept).getDeptId() + "/use").param("value", "false"))
                .andExpect(flash().attribute("message", containsString("폐지했습니다")));
        assertThat(depts.isActive(dept)).isFalse();
        assertThat(depts.activeNames()).doesNotContain(dept);

        // 폐지한 부서로는 계정을 만들 수 없다
        admin.perform(post("/admin/users").param("loginId", uniq("x")).param("userName", "새직원").param("role", "GENERAL")
                        .param("deptName", dept).param("password", "New-member-77!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("지금 쓰는 부서에서 고르세요")));

        admin.perform(post("/admin/depts/" + find(dept).getDeptId() + "/use").param("value", "true"));
        assertThat(depts.isActive(dept)).isTrue();
    }
}
