package egovframework.haengjeongnuli.approval.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 전자결재 서비스 */
public interface ApprovalService {

    enum Result { OK, NOT_FOUND, FORBIDDEN, NOT_YOUR_TURN, BAD_STATE }

    /** 입력값을 확인한다. 문제가 있으면 입력칸 이름 → 안내 문구 (상신이면 더 엄격하게) */
    Map<String, String> validate(ApprovalForm form, boolean submit, LoginUser me);

    /** 새 문서를 임시저장하거나 상신하고 문서 번호(DOC_ID)를 돌려준다 */
    long create(ApprovalForm form, boolean submit, List<MultipartFile> files, LoginUser me) throws IOException;

    /** 임시저장 문서를 고치거나 상신한다 */
    Result update(long docId, ApprovalForm form, boolean submit, List<MultipartFile> files,
                  List<Long> removeFileIds, LoginUser me) throws IOException;

    /** 결재선·붙임까지 채운 문서. 없으면 null */
    ApprovalDocVO get(long docId);

    List<ApprovalDocVO> list(ApprovalSearchVO search);

    int count(ApprovalSearchVO search);

    /** 문서함별 건수 (wait, prog, done, back, recv, temp) */
    Map<String, Integer> boxCounts(LoginUser me);

    Result approve(long docId, String opinion, LoginUser me);

    Result reject(long docId, String opinion, LoginUser me);

    /** 아무도 결재하지 않은 진행 문서를 임시저장으로 되돌린다 */
    Result withdraw(long docId, LoginUser me);

    /** 수신 부서에서 접수한다 */
    Result receive(long docId, LoginUser me);

    Result deleteDraft(long docId, LoginUser me);

    /** 읽을 수 있는 문서의 붙임만 돌려준다 */
    ApprovalFileVO fileForDownload(long fileId, LoginUser me);

    /** 결재선에 넣을 수 있는 직원 (사용 중인 계정, 본인 제외) */
    List<LoginUser> approverCandidates(LoginUser me);
}
