package egovframework.haengjeongnuli.civil.service;

import java.util.List;

/** 민원 서비스 */
public interface CivilService {

    /** 민원을 접수하고 접수번호가 채워진 민원을 돌려준다 */
    CivilComplaintVO receive(CivilForm form, String registerId);

    List<CivilComplaintVO> search(CivilSearchVO search);

    int count(CivilSearchVO search);

    /** 처리 현황 숫자 (보는 사람이 볼 수 있는 민원만 센다) */
    CivilSummaryVO summary(egovframework.haengjeongnuli.cmm.LoginUser viewer);
}
