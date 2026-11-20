package egovframework.haengjeongnuli.reg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import egovframework.haengjeongnuli.reg.RegDef.Scope;

/**
 * 행정누리 대장 목록. 칸을 더하거나 대장을 새로 만들 때는 여기만 고치면 된다 (데이터는 칸 이름으로 저장하므로 표를 바꿀 필요 없음).
 * 법령 안내는 요지만 적었다. 기관 규정·최신 법령은 국가법령정보센터에서 확인할 것.
 */
public final class RegCatalog {

    private static final Map<String, RegDef> ALL = new LinkedHashMap<>();

    private static RegDef reg(String code, String group, String name, Scope scope, String guide) {
        RegDef d = new RegDef(code, group, name, scope, guide);
        ALL.put(code, d);
        return d;
    }

    static {
        String G1 = "청렴·감사";
        reg("gift", G1, "선물·금품 신고", Scope.SELF, "직무와 관련해 받은 선물·금품은 소속 기관장에게 신고하고, 돌려주거나 기관이 처리합니다 (청탁금지법). 가액 기준은 기관 행동강령을 따릅니다.")
                .f("receivedOn|받은 날|D|rl").f("giver|준 사람(단체)|T|rp").f("item|물품|T|rt").f("value|추정 가액(원)|N|la")
                .f("relation|직무 관련성|S|r|있음,없음,모름").f("action|처리|S|l|반환,기관 귀속,폐기,신고만").f("memo|경위|L|")
                .st("신고,검토 중,처리 완료*");
        reg("lecture", G1, "외부강의 등 신고", Scope.SELF, "사례금을 받는 외부강의·회의 등은 마친 날부터 10일 안에 서면으로 신고합니다 (청탁금지법 제10조). 국가·지방자치단체가 요청한 강의는 신고 대상이 아닙니다. 상한을 넘은 사례금은 신고하고 바로 돌려줍니다.")
                .f("title|강의 주제|T|r").f("host|요청 기관|T|rl").f("endOn|마친 날|D|rld+10").f("hours|시간|N|").f("fee|사례금(원)|N|la")
                .f("place|장소|T|").f("memo|비고|L|").st("작성,신고 완료*,반려");
        reg("conflict", G1, "사적이해관계자 신고·회피", Scope.SELF, "직무관련자가 사적이해관계자임을 안 날부터 14일 안에 서면으로 신고하고 회피를 신청합니다 (공직자의 이해충돌 방지법 제5조).")
                .f("knownOn|안 날|D|rld+14").f("duty|담당 직무|T|rt").f("party|직무관련자|T|rp").f("relation|관계|S|rl|가족,과거 2년 내 재직 법인,30% 이상 지분 법인,기타")
                .f("memo|내용|L|").st("신고,회피 결정,계속 수행 결정,처리 완료*");
        reg("sidejob", G1, "겸직 허가", Scope.SELF, "공무원이 영리 업무가 아닌 다른 직무를 겸하려면 미리 소속 기관장의 허가를 받습니다 (국가공무원법·지방공무원법, 복무규정). 허가 기간이 끝나면 다시 받습니다.")
                .f("job|겸직 내용|T|rt").f("org|겸직 기관|T|rl").f("from|시작일|D|l").f("to|종료일|D|ld").f("hours|월 시간|N|").f("pay|보수(월, 원)|N|")
                .st("신청,허가,불허*,종료*");
        reg("audit", G1, "감사 지적사항 조치", Scope.DEPT, "감사에서 지적된 사항과 조치 결과를 관리합니다. 조치 기한을 넘기지 않도록 기한 알림이 뜹니다.")
                .f("auditName|감사명|T|rl").f("finding|지적 내용|T|rt").f("kind|처분 종류|S|l|시정,주의,개선,권고,통보,징계·문책").f("dueOn|조치 기한|D|ld")
                .f("amount|회수·추징액(원)|N|a").f("action|조치 결과|L|").st("미조치,조치 중,조치 완료*");
        reg("whistle", G1, "공익신고·부패신고 접수", Scope.SELF, "신고자의 신분은 절대 밝히지 않습니다 (공익신고자 보호법·부패방지권익위법). 이 대장은 관리자와 접수자만 볼 수 있습니다.")
                .f("receivedOn|접수일|D|rl").f("subject|신고 요지|T|rt").f("reporter|신고자|T|p").f("contact|연락처|P|p").f("dueOn|처리 기한|D|ld")
                .f("result|처리 결과|L|").st("접수,조사 중,이첩*,종결*");

        String G2 = "개인정보·보안";
        reg("pfile", G2, "개인정보파일 대장", Scope.ALL, "개인정보파일을 운용하려면 이름·근거·목적·항목·보유 기간 등을 등록합니다 (개인정보 보호법 제32조). 바뀌면 다시 등록합니다.")
                .f("name|개인정보파일 이름|T|rt").f("basis|운영 근거|T|rl").f("purpose|목적|T|r").f("items|기록 항목|L|r").f("count|정보주체 수|N|l")
                .f("retention|보유 기간|T|rl").f("dept|담당 부서|T|l").st("운영,변경 중,파기*");
        reg("pprovide", G2, "개인정보 제공 기록", Scope.DEPT, "다른 기관·사람에게 개인정보를 제공하면 받는 자·근거·항목·방법을 기록하고 보관합니다.")
                .f("providedOn|제공일|D|rl").f("receiver|받는 자|T|rt").f("basis|법적 근거|T|rl").f("items|제공 항목|T|r").f("count|건수|N|")
                .f("how|방법|S||문서,전자우편,시스템 연계,직접 열람").st("기록,완료*");
        reg("ptrust", G2, "개인정보 처리 위탁", Scope.ALL, "개인정보 처리를 맡기면 위탁 업무와 수탁자를 공개하고, 수탁자를 교육·감독합니다 (개인정보 보호법 제26조).")
                .f("vendor|수탁자|T|rt").f("work|위탁 업무|T|rl").f("from|계약 시작|D|").f("to|계약 끝|D|ld").f("check|점검일|D|l").f("memo|점검 결과|L|")
                .st("위탁 중,점검 필요,종료*");
        reg("pdestroy", G2, "개인정보 파기 기록", Scope.DEPT, "보유 기간이 지난 개인정보는 지체 없이 파기하고 그 기록을 남깁니다 (개인정보 보호법 제21조).")
                .f("doneOn|파기일|D|rl").f("target|파기 대상|T|rt").f("count|건수|N|l").f("how|방법|S|rl|전자 영구 삭제,분쇄,소각,전문업체 위탁").f("witness|확인자|T|")
                .st("기록*");
        reg("cctv", G2, "영상정보 열람·제공", Scope.DEPT, "영상정보처리기기(CCTV) 영상을 열람하거나 제공하면 목적·요청자·일시를 기록합니다. 정보주체 외 사람이 나오면 가리고 제공합니다.")
                .f("reqOn|요청일|D|rl").f("requester|요청자|T|rp").f("purpose|목적|T|rt").f("camera|카메라·위치|T|l").f("span|영상 일시|T|")
                .f("decision|결정|S|l|열람,사본 제공,거부").st("요청,결정 완료*");
        reg("usb", G2, "보안 USB(이동식 저장매체) 관리", Scope.ALL, "이동식 저장매체는 등록해 쓰고, 분실하면 바로 보안 담당에게 알립니다.")
                .f("serial|관리번호|T|rt").f("holder|사용자|T|rl").f("kind|종류|S|l|보안 USB,외장 하드,기타").f("issuedOn|지급일|D|").f("checkOn|다음 점검일|D|ld")
                .st("사용 중,반납*,분실*,폐기*");
        reg("key", G2, "열쇠 관리", Scope.DEPT, "사무실·금고·차량 열쇠 대여와 반납을 적습니다.")
                .f("keyName|열쇠|T|rt").f("borrower|빌린 사람|T|rl").f("outAt|빌린 날|D|l").f("dueOn|반납 예정일|D|ld").st("대여 중,반납*");
        reg("pass", G2, "출입증 발급", Scope.ALL, "청사 출입증 발급·회수를 관리합니다. 기간이 끝난 출입증은 회수합니다.")
                .f("holder|받은 사람|T|rtp").f("org|소속|T|l").f("cardNo|출입증 번호|T|l").f("to|유효 기간 끝|D|ld").st("발급,회수*,분실*");
        reg("secdoc", G2, "비밀·대외비 문서 관리", Scope.DEPT, "비밀·대외비 문서는 접수·생산·열람·파기를 기록하고, 보호 기간이 끝나면 재분류하거나 파기합니다 (보안업무규정).")
                .f("docNo|문서번호|T|rl").f("title|제목|T|rt").f("grade|등급|S|rl|II급 비밀,III급 비밀,대외비").f("keepTo|보호 기간 끝|D|ld").f("keeper|관리자|T|")
                .st("보관 중,재분류*,파기*");
        reg("software", G2, "소프트웨어 라이선스", Scope.ALL, "정품 소프트웨어 보유 수량과 만료일을 관리합니다.")
                .f("product|제품|T|rt").f("qty|수량|N|l").f("vendor|공급자|T|").f("expireOn|만료일|D|ld").f("cost|구입액(원)|N|a").st("사용 중,갱신 필요,만료*");
        reg("pc", G2, "전산 장비 대장", Scope.ALL, "PC·노트북·프린터 등 전산 장비의 사용자와 내용연수를 관리합니다.")
                .f("assetNo|자산번호|T|rl").f("model|모델|T|rt").f("user|사용자|T|l").f("boughtOn|구입일|D|").f("lifeTo|내용연수 끝|D|ld").st("사용 중,수리 중,불용*");

        String G3 = "총무·관리";
        reg("seal", G3, "관인·직인 날인", Scope.DEPT, "관인을 찍은 문서를 기록합니다 (행정업무의 운영 및 혁신에 관한 규정의 관인 관리).")
                .f("usedOn|날인일|D|rl").f("doc|문서|T|rt").f("seal|관인|S|rl|기관 관인,기관장 직인,부서 관인").f("count|매수|N|").f("by|요청자|T|l").st("날인*");
        reg("stamp", G3, "수입인지·증지 수불", Scope.DEPT, "수입증지·인지의 받은 수와 쓴 수를 기록합니다.")
                .f("on|날짜|D|rl").f("kind|종류|S|rt|수입증지,수입인지,우표").f("inQty|받은 수|N|l").f("outQty|쓴 수|N|l").f("amount|금액(원)|N|a").st("기록*");
        reg("mail", G3, "등기우편 발송", Scope.DEPT, "등기·내용증명 발송 내역과 배달 결과를 관리합니다.")
                .f("sentOn|보낸 날|D|rl").f("to|받는 사람|T|rp").f("doc|문서|T|rt").f("regNo|등기번호|T|l").f("fee|요금(원)|N|a")
                .st("발송,배달 완료*,반송");
        reg("parcel", G3, "택배 수령", Scope.ALL, "청사로 온 택배·우편물을 받아 두고 본인에게 건넵니다.")
                .f("arrivedOn|도착일|D|rl").f("toWhom|받을 사람|T|rt").f("sender|보낸 곳|T|l").f("place|보관 위치|T|").st("보관 중,전달*");
        reg("press", G3, "보도자료·언론 대응", Scope.ALL, "보도자료 배포와 언론 보도 대응을 기록합니다.")
                .f("on|날짜|D|rl").f("title|제목|T|rt").f("kind|구분|S|rl|보도자료,해명자료,언론 문의,오보 대응").f("media|매체|T|").f("memo|내용|L|")
                .st("준비,배포·대응*");
        reg("event", G3, "행사 지원", Scope.ALL, "기관 행사 일정·장소·예산·준비 상황을 관리합니다.")
                .f("name|행사명|T|rt").f("on|일자|D|rld").f("place|장소|T|l").f("budget|예산(원)|N|a").f("people|참석 예정|N|").f("memo|준비 사항|L|")
                .st("계획,준비 중,끝남*");
        reg("committee", G3, "위원회 위원 관리", Scope.ALL, "각종 위원회 위원의 임기를 관리합니다. 임기가 끝나기 전에 위촉·연임을 준비하세요.")
                .f("committee|위원회|T|rl").f("member|위원|T|rt").f("role|구분|S|l|위원장,부위원장,위원,간사").f("from|임기 시작|D|").f("to|임기 끝|D|ld")
                .f("contact|연락처|P|p").st("재임,해촉*,임기 만료*");
        reg("volunteer", G3, "자원봉사자 활동", Scope.ALL, "자원봉사자 활동 시간을 기록합니다 (실적은 1365 자원봉사포털에도 등록).")
                .f("name|이름|T|rtp").f("on|활동일|D|rl").f("hours|시간|N|l").f("work|활동 내용|T|l").st("기록,포털 등록 완료*");
        reg("vehicleFine", G3, "관용차 과태료·범칙금", Scope.ALL, "관용차에 부과된 과태료와 운전자 부담 여부를 관리합니다.")
                .f("on|위반일|D|rl").f("car|차량번호|T|rl").f("driver|운전자|T|t").f("kind|위반|T|").f("amount|금액(원)|N|a").f("dueOn|납부 기한|D|ld")
                .st("부과,운전자 납부*,기관 납부*");

        String G4 = "민원·현장";
        reg("streetlight", G4, "가로등·보안등 고장", Scope.ALL, "가로등·보안등 고장 신고를 받아 보수 결과까지 관리합니다.")
                .f("on|신고일|D|rl").f("place|위치|T|rt").f("lampNo|등 번호|T|l").f("reporter|신고자 연락처|P|p").f("dueOn|보수 목표일|D|ld").st("접수,보수 중,보수 완료*");
        reg("illegalAd", G4, "불법 광고물 정비", Scope.DEPT, "불법 현수막·벽보 등의 정비 실적을 기록합니다 (옥외광고물법).")
                .f("on|정비일|D|rl").f("place|위치|T|rt").f("kind|종류|S|l|현수막,벽보,전단,입간판,기타").f("qty|수량|N|l").f("fine|과태료(원)|N|a").st("정비*,과태료 부과*");
        reg("dumping", G4, "쓰레기 무단투기 단속", Scope.DEPT, "무단투기 적발과 과태료 부과를 관리합니다 (폐기물관리법).")
                .f("on|적발일|D|rl").f("place|장소|T|rt").f("violator|위반자|T|p").f("fine|과태료(원)|N|la").f("dueOn|의견 제출 기한|D|d").st("적발,사전 통지,부과*,불부과*");
        reg("roadDig", G4, "도로 점용·굴착 허가", Scope.DEPT, "도로 점용·굴착 허가와 원상복구 확인을 관리합니다 (도로법).")
                .f("applicant|신청인|T|rtp").f("place|위치|T|rl").f("purpose|목적|T|").f("from|시작|D|").f("to|끝|D|ld").f("fee|점용료(원)|N|a")
                .st("신청,허가,복구 확인*,불허*");
        reg("facilityCheck", G4, "시설물 안전점검", Scope.ALL, "기관이 관리하는 시설물의 점검 결과와 다음 점검일을 관리합니다 (시설물안전법).")
                .f("facility|시설물|T|rt").f("grade|안전등급|S|l|A,B,C,D,E").f("checkedOn|점검일|D|l").f("nextOn|다음 점검일|D|ld").f("memo|조치 사항|L|")
                .st("정상,보수 필요,보수 완료*");
        reg("park", G4, "공원·놀이시설 점검", Scope.DEPT, "공원·어린이놀이시설을 점검합니다 (어린이놀이시설 안전관리법: 월 1회 이상 안전점검).")
                .f("place|시설|T|rt").f("on|점검일|D|rl").f("nextOn|다음 점검일|D|ld").f("result|결과|S|l|이상 없음,경미 보수,사용 중지").st("완료*,보수 필요");
        reg("complaintCall", G4, "특이민원 대응 기록", Scope.DEPT, "폭언·폭행 등 위법행위를 한 민원인 대응을 기록합니다. 직원 보호 조치(녹음 안내·분리)를 함께 적으세요 (민원처리법).")
                .f("on|발생일|D|rl").f("kind|유형|S|rlt|폭언,폭행,협박,성희롱,반복 방문,기타").f("person|민원인|T|p").f("staff|대응 직원|T|").f("action|조치|L|")
                .st("기록,고발,종결*");
        reg("recording", G4, "민원 응대 녹음 관리", Scope.DEPT, "민원 응대 녹음 파일의 열람 요청과 파기를 기록합니다.")
                .f("on|녹음일|D|rl").f("subject|건|T|rt").f("keepTo|보관 기한|D|ld").f("viewer|열람자|T|").st("보관,열람,파기*");

        String G5 = "주민·복지";
        reg("tongban", G5, "통·반장 관리", Scope.DEPT, "통장·반장 임기와 연락처를 관리합니다 (지방자치단체 통·반 설치 조례).")
                .f("area|통·반|T|rl").f("name|이름|T|rtp").f("contact|연락처|P|p").f("from|임기 시작|D|").f("to|임기 끝|D|ld").st("재임,해촉*,임기 만료*");
        reg("residents", G5, "주민자치회 위원", Scope.DEPT, "주민자치회 위원 위촉과 임기를 관리합니다.")
                .f("name|이름|T|rtp").f("role|직책|S|l|회장,부회장,간사,위원").f("from|임기 시작|D|").f("to|임기 끝|D|ld").st("재임,해촉*,임기 만료*");
        reg("seniorCenter", G5, "경로당 관리", Scope.DEPT, "경로당 운영비 지원과 점검을 관리합니다.")
                .f("name|경로당|T|rt").f("head|회장|T|lp").f("members|회원 수|N|l").f("support|연 지원액(원)|N|a").f("checkOn|다음 점검일|D|ld").st("운영,휴관,폐지*");
        reg("scholar", G5, "장학금 지급", Scope.DEPT, "장학생 선발과 지급을 관리합니다.")
                .f("name|학생|T|rtp").f("school|학교|T|l").f("kind|구분|S|l|성적,저소득,특기,기타").f("amount|지급액(원)|N|la").f("paidOn|지급일|D|").st("선발,지급*,취소*");
        reg("youth", G5, "청년 지원사업 신청", Scope.DEPT, "청년 지원사업 신청과 선정 결과를 관리합니다.")
                .f("program|사업|T|rl").f("name|신청자|T|rtp").f("on|신청일|D|l").f("amount|지원액(원)|N|a").f("dueOn|심사 기한|D|d").st("신청,심사 중,선정*,미선정*");
        reg("jobConsult", G5, "일자리 상담", Scope.DEPT, "구직 상담과 알선 결과를 기록합니다.")
                .f("on|상담일|D|rl").f("name|구직자|T|rtp").f("want|희망 직종|T|l").f("result|결과|S|l|알선,취업,교육 연계,상담만").st("상담,취업*,종결*");
        reg("foodBank", G5, "푸드뱅크 기부·지원", Scope.DEPT, "기부받은 식품·생필품과 나눠 준 내역을 기록합니다.")
                .f("on|날짜|D|rl").f("kind|구분|S|rl|기부 접수,지원").f("party|기부자·수혜자|T|tp").f("item|물품|T|").f("value|환산액(원)|N|a").st("기록*");
        reg("legalHelp", G5, "무료 법률상담 예약", Scope.ALL, "마을 변호사·무료 법률상담 예약을 받습니다.")
                .f("on|상담일|D|rld").f("name|신청인|T|rtp").f("contact|연락처|P|p").f("topic|분야|S|l|가사,민사,형사,임대차,기타").st("예약,상담 완료*,취소*");
        reg("animal", G5, "유기동물 보호", Scope.DEPT, "구조한 유기동물을 공고하고, 공고 뒤 10일이 지나도 주인이 없으면 소유권이 지방자치단체로 넘어갑니다 (동물보호법).")
                .f("foundOn|구조일|D|rl").f("kind|동물|T|rt").f("place|구조 장소|T|l").f("noticeOn|공고일|D|d+10").f("chip|등록번호|T|").st("보호 중,주인 반환*,입양*,폐사*");
        reg("livestock", G5, "가축 방역 점검", Scope.DEPT, "축산 농가 방역 점검과 소독 실적을 기록합니다.")
                .f("farm|농가|T|rt").f("on|점검일|D|rl").f("kind|축종|S|l|소,돼지,닭·오리,기타").f("result|결과|S|l|적정,미흡,조치 명령").f("nextOn|다음 점검|D|d").st("점검*,조치 필요");

        String G6 = "안전·환경";
        reg("disaster", G6, "재난 피해 접수", Scope.ALL, "자연재난 피해 신고를 받아 조사·지원까지 관리합니다 (자연재해대책법·재난안전법).")
                .f("on|신고일|D|rl").f("name|신고자|T|p").f("place|피해 장소|T|rt").f("kind|피해|S|l|주택,농작물,시설,차량,기타").f("amount|피해액(원)|N|a").f("dueOn|조사 기한|D|d")
                .st("접수,조사 중,지원 결정*,해당 없음*");
        reg("fireWatch", G6, "산불 감시원 근무", Scope.DEPT, "산불 조심 기간 감시원 근무를 기록합니다.")
                .f("name|감시원|T|rtp").f("on|근무일|D|rl").f("area|담당 구역|T|l").f("hours|시간|N|").st("기록*");
        reg("snow", G6, "제설 자재 수불", Scope.DEPT, "염화칼슘·모래 등 제설 자재의 입고·사용을 기록합니다.")
                .f("on|날짜|D|rl").f("item|자재|S|rt|염화칼슘,소금,모래,친환경 제설제").f("inQty|입고(포)|N|l").f("outQty|사용(포)|N|l").f("place|장소|T|").st("기록*");
        reg("shelter", G6, "대피시설 점검", Scope.ALL, "민방위 대피시설·무더위 쉼터 점검을 기록합니다.")
                .f("name|시설|T|rt").f("kind|종류|S|l|민방위 대피시설,무더위 쉼터,한파 쉼터,임시 주거시설").f("capacity|수용 인원|N|l").f("nextOn|다음 점검일|D|ld")
                .st("정상,보수 필요,지정 해제*");
        reg("pollution", G6, "환경오염 신고", Scope.DEPT, "소음·악취·대기·수질 오염 신고를 받아 조치합니다.")
                .f("on|신고일|D|rl").f("kind|구분|S|rl|소음,악취,대기,수질,토양,기타").f("place|장소|T|rt").f("reporter|신고자 연락처|P|p").f("dueOn|처리 기한|D|d")
                .st("접수,현장 확인,행정처분*,종결*");

        String G7 = "재정·계약";
        reg("subsidy", G7, "보조금 교부·정산", Scope.DEPT, "보조사업자에게 보조금을 교부하고 정산 보고를 받습니다 (보조금법·지방보조금법). 정산 기한을 넘기지 않게 관리합니다.")
                .f("project|보조사업|T|rt").f("grantee|보조사업자|T|rl").f("amount|교부액(원)|N|la").f("grantedOn|교부일|D|").f("settleOn|정산 기한|D|ld")
                .f("returned|반환액(원)|N|").st("교부,정산 중,정산 완료*,반환 명령");
        reg("arrears", G7, "세외수입 체납", Scope.DEPT, "과태료·사용료 등 세외수입 체납과 독촉을 관리합니다 (지방행정제재·부과금법).")
                .f("payer|체납자|T|rtp").f("kind|수입 종류|T|l").f("amount|체납액(원)|N|la").f("since|납기|D|").f("dueOn|다음 독촉일|D|d").st("체납,독촉,분납 중,완납*,결손*");
        reg("fine", G7, "과태료 부과", Scope.DEPT, "과태료 부과 전 사전 통지하고 10일 이상 의견 제출 기간을 줍니다 (질서위반행위규제법 제16조).")
                .f("party|당사자|T|rtp").f("law|위반 법령|T|l").f("amount|금액(원)|N|la").f("noticeOn|사전 통지일|D|d+10").f("memo|의견·감경|L|")
                .st("사전 통지,부과,납부*,이의 제기,취소*");
        reg("warranty", G7, "하자보수 보증", Scope.ALL, "계약 목적물의 하자담보책임 기간과 보증금을 관리합니다 (지방계약법). 기간 끝나기 전에 하자 검사를 합니다.")
                .f("contract|계약명|T|rt").f("vendor|업체|T|l").f("bond|보증금(원)|N|a").f("to|담보 기간 끝|D|ld").f("memo|하자 내역|L|").st("담보 중,하자 보수 요청,기간 만료*");
        reg("supervise", G7, "공사 감독 일지", Scope.DEPT, "공사 감독 내용과 공정률을 날마다 기록합니다.")
                .f("project|공사명|T|rt").f("on|날짜|D|rl").f("progress|공정률(%)|N|l").f("weather|날씨|T|").f("memo|감독 내용|L|r").st("기록*");
        reg("rentFee", G7, "공유재산 사용료 부과", Scope.DEPT, "공유재산 사용·대부료 부과와 납부를 관리합니다.")
                .f("property|재산|T|rt").f("user|사용자|T|lp").f("amount|사용료(원)|N|la").f("dueOn|납부 기한|D|d").st("부과,납부*,체납");

        String G8 = "법무";
        reg("lawsuit", G8, "소송 사건", Scope.ALL, "기관이 당사자인 소송의 기일과 진행을 관리합니다.")
                .f("caseNo|사건번호|T|rl").f("title|사건명|T|rt").f("court|법원|T|l").f("counsel|소송수행자·대리인|T|").f("nextOn|다음 기일|D|ld").f("amount|소가(원)|N|a")
                .st("1심,항소심,상고심,승소*,패소*,일부 승소*,취하*");
        reg("advice", G8, "법률 자문", Scope.ALL, "고문 변호사 자문 요청과 회신을 관리합니다.")
                .f("on|요청일|D|rl").f("subject|자문 사항|T|rt").f("lawyer|변호사|T|l").f("dueOn|회신 희망일|D|d").f("answer|회신 요지|L|").st("요청,회신*");
        reg("objection", G8, "이의신청·행정심판", Scope.ALL, "처분에 대한 이의신청은 처분을 받은 날부터 30일 안에 할 수 있고, 기관은 14일 안에 결과를 알립니다 (행정기본법 제36조). 행정심판 청구 기간도 함께 안내하세요.")
                .f("on|접수일|D|rld+14").f("party|신청인|T|rtp").f("decision|대상 처분|T|l").f("kind|구분|S|l|이의신청,행정심판,진정").f("result|결과|L|")
                .st("접수,검토 중,인용*,기각*,각하*");
        reg("ordinance", G8, "자치법규 입법예고", Scope.ALL, "조례·규칙 제정·개정 입법예고 기간과 의견을 관리합니다 (행정절차법: 자치법규 입법예고는 20일 이상).")
                .f("name|자치법규|T|rt").f("from|예고 시작|D|l").f("to|예고 끝|D|ld").f("opinions|제출 의견 수|N|l").f("memo|반영 결과|L|")
                .st("준비,예고 중,의회 제출,공포*");
    }

    private RegCatalog() {}

    public static RegDef get(String code) {
        return code == null ? null : ALL.get(code);
    }

    public static List<RegDef> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
    }

    public static Map<String, List<RegDef>> byGroup() {
        Map<String, List<RegDef>> m = new LinkedHashMap<>();
        ALL.values().forEach(d -> m.computeIfAbsent(d.getGroup(), k -> new ArrayList<>()).add(d));
        return m;
    }
}
