package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.property.PropertyService;
import egovframework.haengjeongnuli.property.PropertyVO;
import egovframework.haengjeongnuli.vehicle.VehicleService;
import egovframework.haengjeongnuli.vehicle.VehicleVO;

/** 공유재산 대장·사용허가, 관용차량 운행일지·주유 */
@SpringBootTest
class PropertyVehicleTest {

    @Autowired WebApplicationContext ctx;
    @Autowired PropertyService property;
    @Autowired VehicleService vehicles;

    @Test
    void 사용료가_하한보다_적으면_근거를_적고_기간은_겹치지_않는다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        MockMvc staff = TestUsers.mvcAs(ctx, TestUsers.general());
        String name = "주민센터 부지 " + UUID.randomUUID().toString().substring(0, 4);
        staff.perform(post("/property").param("propKind", "토지").param("propClass", "행정재산").param("name", name))
                .andExpect(flash().attribute("message", containsString("직원과 관리자만")));
        admin.perform(post("/property").param("propKind", "토지").param("propClass", "행정재산").param("name", name)
                        .param("location", "중앙동 1").param("area", "1,200.5").param("appraised", "100,000,000")
                        .param("acquiredOn", "2010-03-01").param("deptName", "민원봉사과"))
                .andExpect(flash().attribute("message", containsString("공유재산 대장에 올렸습니다")));
        PropertyVO p = property.list(null, name, false).get(0);
        assertThat(p.getUseType()).isEqualTo("사용허가");
        assertThat(p.getMinAnnualFee()).isEqualTo(1_000_000L);

        LocalDate s = LocalDate.now(), e = s.plusYears(1);
        String url = "/property/" + p.getPropId() + "/use";
        admin.perform(post(url).param("userName", "○○협동조합").param("purpose", "주차장").param("startDate", s.toString())
                        .param("endDate", e.toString()).param("annualFee", "500,000"))
                .andExpect(flash().attribute("message", containsString("1천분의 10")));
        admin.perform(post(url).param("userName", "○○협동조합").param("purpose", "주차장").param("startDate", s.toString())
                        .param("endDate", e.toString()).param("annualFee", "500,000").param("feeNote", "조례 제○조 50% 감면"))
                .andExpect(flash().attribute("message", containsString("사용허가를 기록했습니다")));
        admin.perform(post(url).param("userName", "다른단체").param("purpose", "행사").param("startDate", s.plusDays(10).toString())
                        .param("endDate", s.plusDays(20).toString()).param("annualFee", "2,000,000"))
                .andExpect(flash().attribute("message", containsString("같은 기간에 이미")));
        // 진행 중이면 처분할 수 없다
        admin.perform(post("/property/" + p.getPropId() + "/dispose").param("on", s.toString()).param("reason", "매각"))
                .andExpect(flash().attribute("message", containsString("먼저 끝내세요")));
        // 관리부서 직원(민원봉사과)은 사용허가를 끝낼 수 있다
        long useId = property.uses(p.getPropId()).get(0).getUseId();
        MockMvc counter = TestUsers.mvcAs(ctx, TestUsers.counter());
        counter.perform(post("/property/uses/" + useId + "/end").param("note", "반환"))
                .andExpect(flash().attribute("message", containsString("끝난 것으로")));
        admin.perform(post("/property/" + p.getPropId() + "/dispose").param("on", s.toString()).param("reason", "매각(문서 2026-1)"))
                .andExpect(flash().attribute("message", containsString("처분을 기록")));
        assertThat(property.get(p.getPropId()).isHeld()).isFalse();
        assertThat(admin.perform(get("/property.csv?all=true")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(name);
    }

    @Test
    void 운행일지는_주행거리가_이어지고_주유는_리터를_적는다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        MockMvc staff = TestUsers.mvcAs(ctx, TestUsers.general());
        String plate = (10 + (int) (Math.random() * 89)) + "허" + (1000 + (int) (Math.random() * 8999));
        admin.perform(post("/vehicles").param("plateNo", "ABC").param("model", "x"))
                .andExpect(flash().attribute("message", containsString("12가3456")));
        admin.perform(post("/vehicles").param("plateNo", plate).param("model", "아반떼").param("vehicleKind", "승용").param("fuel", "휘발유")
                        .param("deptName", "총무과").param("acquiredOn", "2024-01-01").param("odometer", "10,000")
                        .param("inspectionDue", LocalDate.now().plusDays(10).toString()))
                .andExpect(flash().attribute("message", containsString("차량을 등록했습니다")));
        VehicleVO v = vehicles.list(false).stream().filter(x -> x.getPlateNo().equals(plate)).findFirst().orElseThrow();
        assertThat(v.isInspectionSoon()).isTrue();
        String url = "/vehicles/" + v.getVehicleId();
        LocalDateTime t = LocalDateTime.now().minusHours(5).withSecond(0).withNano(0);

        // 일반 직원도 운행일지를 적는다, 거리는 이어져야 한다
        staff.perform(post(url + "/log").param("purpose", "출장").param("destination", "도청").param("startAt", t.toString())
                        .param("endAt", t.plusHours(2).toString()).param("startKm", "9,000").param("endKm", "9,050"))
                .andExpect(flash().attribute("message", containsString("마지막 기록(10,000km)")));
        staff.perform(post(url + "/log").param("purpose", "출장").param("destination", "도청").param("startAt", t.toString())
                        .param("endAt", t.plusHours(2).toString()).param("startKm", "10,000").param("endKm", "10,085"))
                .andExpect(flash().attribute("message", containsString("85km")));
        staff.perform(post(url + "/log").param("purpose", "출장2").param("destination", "군청").param("startAt", t.plusHours(1).toString())
                        .param("endAt", t.plusHours(3).toString()).param("startKm", "10,085").param("endKm", "10,100"))
                .andExpect(flash().attribute("message", containsString("같은 시간에")));
        assertThat(vehicles.get(v.getVehicleId()).getOdometer()).isEqualTo(10_085);

        // 주유는 차량 담당 부서만, 리터가 필요하다
        staff.perform(post(url + "/cost").param("kind", "주유").param("doneOn", LocalDate.now().toString()).param("amount", "60,000"))
                .andExpect(flash().attribute("message", containsString("차량 담당 부서")));
        admin.perform(post(url + "/cost").param("kind", "주유").param("doneOn", LocalDate.now().toString()).param("amount", "60,000"))
                .andExpect(flash().attribute("message", containsString("주유량")));
        admin.perform(post(url + "/cost").param("kind", "주유").param("doneOn", LocalDate.now().toString()).param("amount", "60,000")
                        .param("liters", "35.5").param("vendor", "○○주유소"))
                .andExpect(flash().attribute("message", containsString("주유 기록")));
        String page = admin.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("85km").contains("60,000원").contains("○○주유소");
        String list = admin.perform(get("/vehicles")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(list).contains(plate).contains("35.5");
        assertThat(admin.perform(get(url + "/logs.csv?reason=test")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("도청");
    }
}
