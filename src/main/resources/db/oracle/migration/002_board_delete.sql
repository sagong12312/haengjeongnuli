-- 이미 schema.sql 로 테이블을 만든 Oracle / Tibero DB에 게시물 삭제 기능 컬럼을 추가한다 (한 번만 실행)
ALTER TABLE BOARD_POST ADD (
    WRITER_ID   VARCHAR2(50),
    DELETED_YN  CHAR(1) DEFAULT 'N' NOT NULL,
    DELETED_AT  TIMESTAMP,
    DELETED_BY  VARCHAR2(50)
);
