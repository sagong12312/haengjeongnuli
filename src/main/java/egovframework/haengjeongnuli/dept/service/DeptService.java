package egovframework.haengjeongnuli.dept.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.dept.service.impl.DeptMapper;

/**
 * 부서(조직도). 직원 계정·처리과·공개 범위는 부서 이름으로 이어지므로, 이름은 한 번 만들면 바꾸지 않는다.
 * 부서 이름이 바뀌면 새 부서를 만들고 직원을 옮긴 뒤 옛 부서를 폐지한다 (지난 문서는 옛 처리과명을 그대로 가진다).
 */
@Service("deptService")
public class DeptService {

    public record Result(boolean ok, String message) {}

    private final DeptMapper mapper;

    public DeptService(DeptMapper mapper) {
        this.mapper = mapper;
    }

    /** 조직도 순서(상위 부서 아래에 하위 부서)로 펼친 전체 목록 */
    public List<DeptVO> tree() {
        List<DeptVO> all = mapper.selectAll();
        Map<Long, List<DeptVO>> children = all.stream().filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(DeptVO::getParentId));
        Set<Long> ids = all.stream().map(DeptVO::getDeptId).collect(Collectors.toSet());
        List<DeptVO> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (DeptVO d : all) {
            if (d.getParentId() == null || !ids.contains(d.getParentId())) {
                walk(d, 0, children, out, seen);
            }
        }
        for (DeptVO d : all) { // 순환 참조 등으로 빠진 것
            if (!seen.contains(d.getDeptId())) {
                walk(d, 0, children, out, seen);
            }
        }
        return out;
    }

    private static void walk(DeptVO d, int depth, Map<Long, List<DeptVO>> children, List<DeptVO> out, Set<Long> seen) {
        if (!seen.add(d.getDeptId())) {
            return;
        }
        d.setDepth(depth);
        out.add(d);
        for (DeptVO c : children.getOrDefault(d.getDeptId(), List.of())) {
            walk(c, depth + 1, children, out, seen);
        }
    }

    /** 지금 쓰는 부서 이름 (조직도 순서). 화면의 부서 고르기 목록 */
    public List<String> activeNames() {
        return tree().stream().filter(DeptVO::isActive).map(DeptVO::getDeptName).toList();
    }

    public boolean isActive(String name) {
        if (name == null) {
            return false;
        }
        DeptVO d = mapper.selectByName(name.trim());
        return d != null && d.isActive();
    }

    public DeptVO get(long id) {
        return mapper.selectById(id);
    }

    @Transactional
    public Result create(String name, Long parentId, String code, Integer sortOrder) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 100) {
            return new Result(false, "부서 이름을 1~100자로 입력하세요.");
        }
        if (mapper.selectByName(n) != null) {
            return new Result(false, "이미 있는 부서 이름입니다: " + n + " (폐지한 부서면 '다시 사용'을 누르세요)");
        }
        String c = code == null || code.isBlank() ? null : code.trim();
        if (c != null && c.length() > 20) {
            return new Result(false, "부서 코드는 20자까지입니다.");
        }
        if (parentId != null) {
            DeptVO p = mapper.selectById(parentId);
            if (p == null || !p.isActive()) {
                return new Result(false, "상위 부서는 지금 쓰는 부서에서 고르세요.");
            }
        }
        DeptVO d = new DeptVO();
        d.setDeptId(mapper.nextId());
        d.setDeptName(n);
        d.setParentId(parentId);
        d.setDeptCode(c);
        d.setSortOrder(sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999)));
        d.setCreatedAt(LocalDateTime.now());
        mapper.insert(d);
        return new Result(true, "'" + n + "' 부서를 만들었습니다.");
    }

    @Transactional
    public Result update(long id, Long parentId, String code, Integer sortOrder) {
        DeptVO d = mapper.selectById(id);
        if (d == null) {
            return new Result(false, "없는 부서입니다.");
        }
        if (parentId != null) {
            DeptVO p = mapper.selectById(parentId);
            if (p == null || !p.isActive()) {
                return new Result(false, "상위 부서는 지금 쓰는 부서에서 고르세요.");
            }
            // 자기 자신이나 자기 하위 부서를 상위로 두면 조직도가 고리가 된다
            for (Long cur = parentId; cur != null; ) {
                if (cur == id) {
                    return new Result(false, "자기 자신이나 하위 부서를 상위 부서로 둘 수 없습니다.");
                }
                DeptVO x = mapper.selectById(cur);
                cur = x == null ? null : x.getParentId();
            }
        }
        String c = code == null || code.isBlank() ? null : code.trim();
        if (c != null && c.length() > 20) {
            return new Result(false, "부서 코드는 20자까지입니다.");
        }
        mapper.updateInfo(id, parentId, c, sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999)));
        return new Result(true, "'" + d.getDeptName() + "' 부서 정보를 저장했습니다.");
    }

    @Transactional
    public Result close(long id) {
        DeptVO d = mapper.selectById(id);
        if (d == null) {
            return new Result(false, "없는 부서입니다.");
        }
        if (mapper.countMembers(d.getDeptName()) > 0) {
            return new Result(false, "'" + d.getDeptName() + "'에 사용 중인 직원 계정이 있습니다. 먼저 다른 부서로 옮기세요.");
        }
        if (mapper.countActiveChildren(id) > 0) {
            return new Result(false, "'" + d.getDeptName() + "' 아래에 쓰는 하위 부서가 있습니다. 먼저 하위 부서를 옮기거나 폐지하세요.");
        }
        mapper.updateUse(id, "N");
        return new Result(true, "'" + d.getDeptName() + "' 부서를 폐지했습니다. 지난 문서·기록은 그대로 남습니다.");
    }

    @Transactional
    public Result reopen(long id) {
        DeptVO d = mapper.selectById(id);
        if (d == null) {
            return new Result(false, "없는 부서입니다.");
        }
        mapper.updateUse(id, "Y");
        return new Result(true, "'" + d.getDeptName() + "' 부서를 다시 사용합니다.");
    }
}
