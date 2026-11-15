package egovframework.haengjeongnuli.cmm.notification;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 알림 SQL (egovframework/mapper/cmm/NotificationMapper.xml) */
@Mapper
public interface NotificationMapper {

    int insertNotification(NotificationVO n);

    int countUnread(@Param("loginId") String loginId);

    List<NotificationVO> selectRecent(@Param("loginId") String loginId, @Param("limit") int limit);

    NotificationVO selectOne(@Param("notiId") long notiId);

    int markRead(@Param("notiId") long notiId, @Param("loginId") String loginId);

    int markAllRead(@Param("loginId") String loginId);
}
