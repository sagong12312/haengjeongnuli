package egovframework.haengjeongnuli.civil.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.civil.service.impl.CivilFileMapper;
import egovframework.haengjeongnuli.cmm.file.FileStorage;

/** 민원 구비서류: 형식·개수를 확인해 저장한다. 내려받기는 민원 상세를 볼 수 있는 직원만 */
@Service
public class CivilFileService {

    private final CivilFileMapper mapper;
    private final FileStorage storage;

    public CivilFileService(CivilFileMapper mapper, FileStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    /** 올릴 수 없는 이유 (없으면 null) */
    public String check(List<MultipartFile> files) {
        List<MultipartFile> real = real(files);
        if (real.size() > storage.getMaxCount()) {
            return "파일은 한 번에 " + storage.getMaxCount() + "개까지 올릴 수 있습니다.";
        }
        for (MultipartFile f : real) {
            String p = storage.check(f);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    @Transactional
    public int save(long complaintId, List<MultipartFile> files, String by) throws IOException {
        int n = 0;
        for (MultipartFile mf : real(files)) {
            String stored = storage.save(mf);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            storage.deleteQuietly(stored);
                        }
                    }
                });
            }
            CivilFileVO f = new CivilFileVO();
            f.setFileId(mapper.nextId());
            f.setComplaintId(complaintId);
            f.setOriginalName(FileStorage.cleanName(mf.getOriginalFilename()));
            f.setStoredName(stored);
            f.setFileSize(mf.getSize());
            f.setUploadedBy(by);
            f.setCreatedAt(LocalDateTime.now());
            mapper.insert(f);
            n++;
        }
        return n;
    }

    public List<CivilFileVO> list(long complaintId) {
        return mapper.selectByComplaint(complaintId);
    }

    public CivilFileVO get(long fileId) {
        return mapper.selectById(fileId);
    }

    private static List<MultipartFile> real(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }
}
