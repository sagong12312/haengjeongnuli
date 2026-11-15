package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.approval.service.impl.DispatchMapper;
import egovframework.haengjeongnuli.cmm.LoginUser;

/** 시행문 발송: 결재가 끝난 발신 문서를 수신자별로 보낸 기록 */
@Service
public class DispatchService {

    private final DispatchMapper mapper;

    public DispatchService(DispatchMapper mapper) {
        this.mapper = mapper;
    }

    public List<DispatchVO> list(long docId) {
        return mapper.selectByDoc(docId);
    }

    /** 발송을 기록한다. 문제가 있으면 안내 문구 */
    @Transactional
    public String record(ApprovalDocVO d, String receiver, String method, String note, LoginUser me) {
        if (!d.canDispatch(me)) {
            return "기안자나 결재선에 든 기안 부서 직원만 발송을 기록할 수 있습니다.";
        }
        if (receiver == null || !d.getReceiverList().contains(receiver.trim())) {
            return "수신자를 목록에서 고르세요.";
        }
        if (method == null || !DispatchVO.METHODS.contains(method)) {
            return "발송 방법을 고르세요.";
        }
        if (note != null && note.trim().length() > 500) {
            return "비고는 500자 이내로 적으세요.";
        }
        DispatchVO v = new DispatchVO();
        v.setDispatchId(mapper.nextId());
        v.setDocId(d.getDocId());
        v.setReceiver(receiver.trim());
        v.setMethod(method);
        v.setNote(note == null || note.isBlank() ? null : note.trim());
        v.setDispatchedBy(me.getLoginId());
        v.setDispatchedName(me.getUserName());
        v.setDispatchedAt(LocalDateTime.now());
        mapper.insert(v);
        return null;
    }
}
