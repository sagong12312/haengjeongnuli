package egovframework.haengjeongnuli.finance.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 재정·세무 (area=fin 재정, area=tax 세무) */
@Controller
public class FinanceController {

    @GetMapping("/finance")
    public String index(@RequestParam(defaultValue = "fin") String area, Model model) {
        model.addAttribute("area", "tax".equals(area) ? "tax" : "fin");
        return "finance/index";
    }
}
