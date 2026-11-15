package egovframework.haengjeongnuli.dept.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.dept.service.DeptService;

/** 시스템 관리 › 부서(조직도) 관리 (관리자 전용, /admin/** ) */
@Controller
@RequestMapping("/admin/depts")
public class AdminDeptController {

    private final DeptService depts;
    private final AuditService audit;

    public AdminDeptController(DeptService depts, AuditService audit) {
        this.depts = depts;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tree", depts.tree());
        model.addAttribute("activeDepts", depts.tree().stream().filter(d -> d.isActive()).toList());
        return "user/admin-depts";
    }

    @PostMapping
    public String create(@RequestParam(required = false) String deptName, @RequestParam(required = false) Long parentId,
                         @RequestParam(required = false) String deptCode, @RequestParam(required = false) Integer sortOrder,
                         RedirectAttributes ra) {
        DeptService.Result r = depts.create(deptName, parentId, deptCode, sortOrder);
        if (r.ok()) {
            audit.record(AuditAction.DEPT_CREATE, deptName.trim(), null);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/admin/depts";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @RequestParam(required = false) Long parentId,
                         @RequestParam(required = false) String deptCode, @RequestParam(required = false) Integer sortOrder,
                         RedirectAttributes ra) {
        DeptService.Result r = depts.update(id, parentId, deptCode, sortOrder);
        if (r.ok()) {
            audit.record(AuditAction.DEPT_UPDATE, "부서 " + id, null);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/admin/depts";
    }

    @PostMapping("/{id}/use")
    public String use(@PathVariable long id, @RequestParam boolean value, RedirectAttributes ra) {
        DeptService.Result r = value ? depts.reopen(id) : depts.close(id);
        if (r.ok()) {
            audit.record(value ? AuditAction.DEPT_REOPEN : AuditAction.DEPT_CLOSE, "부서 " + id, null);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/admin/depts";
    }
}
