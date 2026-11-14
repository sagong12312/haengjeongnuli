package egovframework.haengjeongnuli.board.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.board.service.BoardCommentVO;
import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.board.service.BoardSearchVO;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;

/** 게시판 SQL (egovframework/mapper/board/BoardMapper.xml) */
@Mapper
public interface BoardMapper {

    long nextId();

    int insertPost(BoardPostVO vo);

    List<BoardPostVO> selectPostList(BoardSearchVO search);

    int selectPostCount(BoardSearchVO search);

    int increaseViewCount(@Param("postId") long postId);

    long nextCommentId();

    int insertComment(BoardCommentVO comment);

    List<BoardCommentVO> selectComments(@Param("postId") long postId);

    BoardCommentVO selectComment(@Param("commentId") long commentId);

    int markCommentDeleted(@Param("commentId") long commentId, @Param("deletedBy") String deletedBy,
                           @Param("now") java.time.LocalDateTime now);

    long nextFileId();

    int insertFile(AttachFileVO file);

    List<AttachFileVO> selectFilesByPostIds(@Param("postIds") List<Long> postIds);

    AttachFileVO selectFile(@Param("fileId") long fileId);

    int increaseDownloadCount(@Param("fileId") long fileId);

    BoardPostVO selectPost(@Param("postId") long postId);

    int markDeleted(@Param("postId") long postId, @Param("deletedBy") String deletedBy,
                    @Param("now") java.time.LocalDateTime now);

    int updatePost(BoardPostVO vo);

    int deleteFile(@Param("fileId") long fileId, @Param("postId") long postId);
}
