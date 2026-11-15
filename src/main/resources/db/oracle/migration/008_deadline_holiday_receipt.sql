-- 이미 만든 Oracle / Tibero DB에 민원 처리기한 시각, 선거일·임시공휴일, 기록물 등록번호, 접수등록번호를 추가한다 (한 번만 실행)

-- 민원 처리기한 시각 (처리기간 5일 이하는 시간 단위로 계산: 민원처리법 제19조)
ALTER TABLE CIVIL_COMPLAINT ADD (DEADLINE_AT TIMESTAMP);

-- 선거일·임시공휴일 (관공서의 공휴일에 관한 규정 제2조 제10호의2, 제11호). 법정 공휴일은 프로그램이 계산한다.
CREATE TABLE HOLIDAY_EXTRA (
    HOLIDAY_DATE  DATE           NOT NULL,
    HOLIDAY_NAME  VARCHAR2(100)  NOT NULL,
    CREATED_BY    VARCHAR2(50),
    CREATED_AT    TIMESTAMP,
    CONSTRAINT PK_HOLIDAY_EXTRA PRIMARY KEY (HOLIDAY_DATE)
);

-- 처리과별 연도별 기록물 등록일련번호 (생산·접수 등록번호가 함께 쓴다)
CREATE TABLE DOC_REG_COUNTER (
    DEPT_NAME  VARCHAR2(100)  NOT NULL,
    REG_YEAR   NUMBER(4)      NOT NULL,
    LAST_SEQ   NUMBER(10)     NOT NULL,
    CONSTRAINT PK_DOC_REG_COUNTER PRIMARY KEY (DEPT_NAME, REG_YEAR)
);

-- 수신 문서 접수등록번호
ALTER TABLE APPROVAL_DOC ADD (RECEIPT_NO VARCHAR2(130));
