package egovframework.haengjeongnuli.approval.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalFileVO;
import egovframework.haengjeongnuli.approval.service.ApprovalLineVO;
import egovframework.haengjeongnuli.approval.service.ApprovalProxyVO;
import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;

@Mapper
public interface ApprovalMapper {

    long nextDocId();
    long nextLineId();
    long nextFileId();

    void insertDoc(ApprovalDocVO doc);
    int updateDocContent(ApprovalDocVO doc);
    ApprovalDocVO selectDoc(@Param("docId") long docId);

    /** 상태를 바꾼다. 현재 상태가 fromStatus 일 때만 (바뀐 행 수) */
    int updateDocStatus(@Param("docId") long docId, @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus, @Param("at") LocalDateTime at);

    int updateDocNumber(@Param("docId") long docId, @Param("year") int year, @Param("seq") int seq,
                        @Param("docNo") String docNo);


    int markReceived(@Param("docId") long docId, @Param("by") String by, @Param("at") LocalDateTime at,
                     @Param("receiptNo") String receiptNo);
    int markDeleted(@Param("docId") long docId, @Param("drafterId") String drafterId);

    List<ApprovalDocVO> selectList(ApprovalSearchVO search);
    int selectCount(ApprovalSearchVO search);

    void insertLine(ApprovalLineVO line);
    void deleteLines(@Param("docId") long docId);
    List<ApprovalLineVO> selectLines(@Param("docId") long docId);
    /** 결재할 차례인 칸을 처리한다. 차례·결재자가 맞을 때만 (바뀐 행 수) */
    int actLine(@Param("lineId") long lineId, @Param("approverId") String approverId,
                @Param("status") String status, @Param("opinion") String opinion, @Param("at") LocalDateTime at);
    /** 대결: 결재권자(approverId) 칸을 직무대리자가 처리한다 (바뀐 행 수) */
    int actLineAsProxy(@Param("lineId") long lineId, @Param("approverId") String approverId,
                       @Param("status") String status, @Param("opinion") String opinion, @Param("at") LocalDateTime at,
                       @Param("byId") String byId, @Param("byName") String byName, @Param("byPosition") String byPosition);
    int setLineStatus(@Param("docId") long docId, @Param("stepNo") int stepNo, @Param("status") String status);
    void resetLines(@Param("docId") long docId);
    /** 첫 결재자가 아직 처리하지 않았을 때만 회수한다 (바뀐 행 수) */
    int withdrawFirst(@Param("docId") long docId);

    long nextProxyId();
    void insertProxy(ApprovalProxyVO p);
    List<ApprovalProxyVO> selectProxies(@Param("loginId") String loginId, @Param("since") LocalDate since);
    int countActiveProxy(@Param("ownerId") String ownerId, @Param("proxyLoginId") String proxyLoginId,
                         @Param("today") LocalDate today);
    List<String> activeProxyIds(@Param("ownerId") String ownerId, @Param("today") LocalDate today);
    int countOverlapProxy(@Param("ownerId") String ownerId, @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);
    int cancelProxy(@Param("proxyId") long proxyId, @Param("ownerId") String ownerId);

    void insertFile(ApprovalFileVO file);
    List<ApprovalFileVO> selectFiles(@Param("docId") long docId);
    ApprovalFileVO selectFile(@Param("fileId") long fileId);
    int deleteFile(@Param("fileId") long fileId, @Param("docId") long docId);
}
