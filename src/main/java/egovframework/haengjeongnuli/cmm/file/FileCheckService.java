package egovframework.haengjeongnuli.cmm.file;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * 첨부파일 정합성 점검: DB 정보와 저장 폴더의 실제 파일을 맞춰 본다.
 * <ul>
 *   <li>없는 파일: DB에는 있는데 폴더에 없거나 크기가 다른 파일 (백업에서 되살려야 한다)</li>
 *   <li>남는 파일: 폴더에는 있는데 DB에 없는 파일 (저장 중 오류 등). 올리는 중일 수 있으니 하루 지난 것만 지운다</li>
 * </ul>
 */
@Service
public class FileCheckService {

    public record Missing(String source, long id, String name, String stored, String problem) {}

    public record Orphan(String stored, long size, LocalDateTime modified, boolean removable) {}

    public record Result(int registered, int onDisk, List<Missing> missing, List<Orphan> orphans, long diskBytes) {}

    private final FileCheckMapper mapper;
    private final FileStorage storage;

    public FileCheckService(FileCheckMapper mapper, FileStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    public Result check() throws IOException {
        Map<String, long[]> disk = storage.listStored();
        List<Map<String, Object>> rows = mapper.selectAll();
        Set<String> known = new HashSet<>();
        List<Missing> missing = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String stored = (String) r.get("stored");
            known.add(stored);
            long[] f = disk.get(stored);
            long size = ((Number) r.get("size")).longValue();
            String problem = f == null ? "폴더에 파일이 없습니다" : (size > 0 && f[0] != size ? "크기가 다릅니다 (DB " + size + " / 파일 " + f[0] + ")" : null);
            if (problem != null) {
                missing.add(new Missing((String) r.get("source"), ((Number) r.get("id")).longValue(), (String) r.get("name"), stored, problem));
            }
        }
        long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
        List<Orphan> orphans = new ArrayList<>();
        long bytes = 0;
        for (Map.Entry<String, long[]> e : disk.entrySet()) {
            bytes += e.getValue()[0];
            if (!known.contains(e.getKey())) {
                orphans.add(new Orphan(e.getKey(), e.getValue()[0],
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(e.getValue()[1]), ZoneId.systemDefault()), e.getValue()[1] < cutoff));
            }
        }
        return new Result(rows.size(), disk.size(), missing, orphans, bytes);
    }

    /** 하루 넘게 DB에 등록되지 않은 남는 파일을 지운다. 지운 수 */
    public int removeOrphans() throws IOException {
        int n = 0;
        for (Orphan o : check().orphans()) {
            if (o.removable()) {
                storage.deleteQuietly(o.stored());
                n++;
            }
        }
        return n;
    }
}
