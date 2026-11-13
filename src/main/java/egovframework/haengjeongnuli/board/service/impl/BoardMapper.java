package egovframework.haengjeongnuli.board.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;

/** 게시판 SQL (egovframework/mapper/board/BoardMapper.xml) */
@Mapper
public interface BoardMapper {

    long nextId();

    int insertPost(BoardPostVO vo);

    List<BoardPostVO> selectPostList(@Param("boardType") String boardType, @Param("q") String q,
                                     @Param("limit") int limit);

    int selectPostCount(@Param("boardType") String boardType, @Param("q") String q);

    long nextFileId();

    int insertFile(AttachFileVO file);

    List<AttachFileVO> selectFilesByPostIds(@Param("postIds") List<Long> postIds);

    AttachFileVO selectFile(@Param("fileId") long fileId);

    int increaseDownloadCount(@Param("fileId") long fileId);
}
