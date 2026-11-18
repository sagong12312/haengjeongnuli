-- 보안 프로그램: 강제·권고 나누기, 대상(직원 PC / 민원인 PC / 둘 다), 설치 확인 주소 (한 번만 실행)
-- 강제(REQUIRED_YN='Y')를 설치하지 않으면 행정누리·전자민원을 쓰지 못한다.
ALTER TABLE SECURITY_APP ADD (TARGET VARCHAR2(10 CHAR) DEFAULT 'STAFF' NOT NULL, CHECK_URL VARCHAR2(300 CHAR));
