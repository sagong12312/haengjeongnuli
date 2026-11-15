package egovframework.haengjeongnuli.cmm.file;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** DB에 등록된 모든 첨부파일의 저장 이름 (게시판·전자결재·기록물·민원, 관인) */
@Mapper
public interface FileCheckMapper {

    @Select("SELECT '게시판' AS \"source\", FILE_ID AS \"id\", ORIGINAL_NAME AS \"name\", STORED_NAME AS \"stored\", FILE_SIZE AS \"size\" FROM ATTACH_FILE "
            + "UNION ALL SELECT '전자결재', FILE_ID, ORIGINAL_NAME, STORED_NAME, FILE_SIZE FROM APPROVAL_FILE "
            + "UNION ALL SELECT '기록물', FILE_ID, ORIGINAL_NAME, STORED_NAME, FILE_SIZE FROM DOC_REGISTRY_FILE "
            + "UNION ALL SELECT '민원', FILE_ID, ORIGINAL_NAME, STORED_NAME, FILE_SIZE FROM CIVIL_FILE "
            + "UNION ALL SELECT '관인', 0, '관인 이미지', SETTING_VALUE, 0 FROM ORG_SETTING WHERE SETTING_KEY = 'sealFile' AND SETTING_VALUE IS NOT NULL")
    List<Map<String, Object>> selectAll();
}
