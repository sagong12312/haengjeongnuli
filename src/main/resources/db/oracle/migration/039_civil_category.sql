-- 민원 분야 (한 번만 실행). 예전 민원은 비어 있으며 화면에는 '기타'로 보인다
ALTER TABLE CIVIL_COMPLAINT ADD (CATEGORY VARCHAR2(20 CHAR));
