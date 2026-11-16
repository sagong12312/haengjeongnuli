-- 민원 팩스 번호: 접수 때 받은 번호, 결과를 팩스로 보낸 번호 (한 번만 실행)
ALTER TABLE CIVIL_COMPLAINT ADD (FAX_NO VARCHAR2(20 CHAR), RESULT_FAX_NO VARCHAR2(20 CHAR));
