-- 이미 만든 Oracle / Tibero DB에 지출결의(전자결재 ↔ 예산 집행) 연결 컬럼을 추가한다 (한 번만 실행)

-- 지출결의 (전자결재 문서 종류 '지출결의'): 어느 예산에서, 얼마를, 누구에게, 어떻게 지급하는지
ALTER TABLE APPROVAL_DOC ADD (BUDGET_ID NUMBER(19));
ALTER TABLE APPROVAL_DOC ADD (PAY_AMOUNT NUMBER(15));
ALTER TABLE APPROVAL_DOC ADD (PAYEE VARCHAR2(200));
ALTER TABLE APPROVAL_DOC ADD (PAY_METHOD VARCHAR2(20));
-- 결재가 끝난 지출결의로 생긴 집행
ALTER TABLE FIN_TXN ADD (DOC_ID NUMBER(19));
