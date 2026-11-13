package egovframework.haengjeongnuli.hr.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import egovframework.haengjeongnuli.main.web.MainController;
import jakarta.servlet.http.HttpSession;

/** 인사: 내 인사정보, 오늘 근태, 복무 신청 */
@Controller
public class HrController {

    @GetMapping("/hr")
    public String index(HttpSession session, Model model) {
        model.addAttribute("working", MainController.isWorking(session));
        model.addAttribute("inTime", session.getAttribute("inTime"));
        model.addAttribute("outTime", session.getAttribute("outTime"));
        return "hr/index";
    }
}
