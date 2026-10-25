package com.ruoyi.education.course.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.authorization.TeacherAnnouncementAccess;
import com.ruoyi.education.course.authorization.TeachingClassAuthorizationPort;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementConcurrentUpdateException;
import com.ruoyi.education.course.exception.AnnouncementException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementPersistenceException;
import com.ruoyi.education.course.exception.AnnouncementStateConflictException;
import com.ruoyi.education.course.exception.AnnouncementValidationException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;

/**
 * 教师公告的 M0 业务服务。
 *
 * <p>本批只实现公告本身的创建、编辑、发布和归档，不生成公告通知。
 * 所有写操作在服务端事务内完成，并通过条件更新检查实际影响行数。
 * 操作者身份只从 {@link SecurityUtils#getUserId()} 取得，不接受前端
 * 传入的教师 ID。</p>
 */
@Service
public class CourseAnnouncementService
{
    private final CourseAnnouncementMapper announcementMapper;

    private final TeachingClassAuthorizationPort authorizationPort;

    private final Clock clock;

    @Autowired
    public CourseAnnouncementService(CourseAnnouncementMapper announcementMapper,
            ObjectProvider<TeachingClassAuthorizationPort> authorizationPortProvider)
    {
        this(announcementMapper, authorizationPortProvider.getIfAvailable(), Clock.systemUTC());
    }

    CourseAnnouncementService(CourseAnnouncementMapper announcementMapper,
            TeachingClassAuthorizationPort authorizationPort, Clock clock)
    {
        this.announcementMapper = Objects.requireNonNull(announcementMapper, "announcementMapper");
        this.authorizationPort = authorizationPort;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseAnnouncement createDraft(Long bindingId, String title, String body,
            LocalDateTime opensAt, LocalDateTime closesAt)
    {
        validateBindingId(bindingId);
        validateDraftFields(title, body, opensAt, closesAt);

        Long userId = currentUserId();
        LocalDateTime now = now();
        TeacherAnnouncementAccess access = requireAccess(bindingId, userId, now);
        requireCanPublish(access);

        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setBindingId(bindingId);
        announcement.setTitle(title);
        announcement.setBody(body);
        announcement.setStatus(AnnouncementStatus.DRAFT);
        announcement.setOpensAt(opensAt);
        announcement.setClosesAt(closesAt);
        announcement.setCreatedByUserId(userId);
        announcement.setCreatedAt(now);

        int rows = announcementMapper.insertDraft(announcement);
        if (rows != 1 || announcement.getId() == null)
        {
            throw persistenceFailure("公告草稿创建失败");
        }

        CourseAnnouncement saved = announcementMapper.selectById(announcement.getId());
        if (saved == null
                || !bindingId.equals(saved.getBindingId())
                || saved.getStatus() != AnnouncementStatus.DRAFT
                || !userId.equals(saved.getCreatedByUserId())
                || saved.getPublishedByUserId() != null)
        {
            throw persistenceFailure("公告草稿创建结果不一致");
        }
        return saved;
    }

    /**
     * 编辑草稿。创建者可以编辑自己的草稿；其他教师必须具备 canPublish。
     * 已发布和已归档公告不能通过本方法覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public CourseAnnouncement updateDraft(Long bindingId, Long announcementId,
            String title, String body, LocalDateTime opensAt, LocalDateTime closesAt)
    {
        validateBindingId(bindingId);
        validateAnnouncementId(announcementId);
        validateDraftFields(title, body, opensAt, closesAt);

        Long userId = currentUserId();
        LocalDateTime now = now();
        TeacherAnnouncementAccess access = requireAccess(bindingId, userId, now);
        CourseAnnouncement announcement = loadOwned(bindingId, announcementId);

        if (!access.authority().canPublish() && !userId.equals(announcement.getCreatedByUserId()))
        {
            throw new AnnouncementAccessDeniedException();
        }
        if (announcement.getStatus() != AnnouncementStatus.DRAFT)
        {
            throw stateConflict("只有草稿可以编辑");
        }

        announcement.setTitle(title);
        announcement.setBody(body);
        announcement.setOpensAt(opensAt);
        announcement.setClosesAt(closesAt);

        int rows = announcementMapper.updateDraft(announcement);
        if (rows != 1)
        {
            return resolveUpdateZeroRows(bindingId, announcementId);
        }

        CourseAnnouncement saved = announcementMapper.selectById(announcementId);
        if (saved == null
                || !bindingId.equals(saved.getBindingId())
                || saved.getStatus() != AnnouncementStatus.DRAFT)
        {
            throw persistenceFailure("公告草稿更新结果不一致");
        }
        return saved;
    }

    /**
     * 发布草稿。已处于 PUBLISHED 时在身份、绑定和权限校验通过后幂等返回；
     * 已归档后再发布仍为状态冲突。条件更新 0 行时重新读取区分幂等、
     * 不存在/跨绑定、非法状态和真实并发冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public CourseAnnouncement publish(Long bindingId, Long announcementId)
    {
        validateBindingId(bindingId);
        validateAnnouncementId(announcementId);

        Long userId = currentUserId();
        LocalDateTime now = now();
        TeacherAnnouncementAccess access = requireAccess(bindingId, userId, now);
        requireCanPublish(access);

        CourseAnnouncement announcement = loadOwned(bindingId, announcementId);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED)
        {
            requirePublishedFields(announcement);
            return announcement;
        }
        if (announcement.getStatus() != AnnouncementStatus.DRAFT)
        {
            throw stateConflict("只有草稿可以发布；已归档公告不能再次发布");
        }
        if (announcement.getPublishedByUserId() != null)
        {
            throw stateConflict("草稿的发布人状态异常");
        }

        int rows = announcementMapper.publishDraft(announcementId, bindingId, userId, now);
        if (rows != 1)
        {
            return resolvePublishZeroRows(bindingId, announcementId);
        }

        CourseAnnouncement saved = announcementMapper.selectById(announcementId);
        if (saved == null
                || saved.getStatus() != AnnouncementStatus.PUBLISHED
                || !userId.equals(saved.getPublishedByUserId())
                || saved.getOpensAt() == null)
        {
            throw persistenceFailure("公告发布结果不一致");
        }
        return saved;
    }

    /**
     * 归档已发布公告。已处于 ARCHIVED 时在身份、绑定和权限校验通过后幂等返回；
     * 草稿归档仍为状态冲突。条件更新 0 行时重新读取区分幂等、
     * 不存在/跨绑定、非法状态和真实并发冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public CourseAnnouncement archive(Long bindingId, Long announcementId)
    {
        validateBindingId(bindingId);
        validateAnnouncementId(announcementId);

        Long userId = currentUserId();
        LocalDateTime now = now();
        TeacherAnnouncementAccess access = requireAccess(bindingId, userId, now);
        requireCanPublish(access);

        CourseAnnouncement announcement = loadOwned(bindingId, announcementId);
        if (announcement.getStatus() == AnnouncementStatus.ARCHIVED)
        {
            requirePublishedFields(announcement);
            return announcement;
        }
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED)
        {
            throw stateConflict("只有已发布公告可以归档");
        }
        requirePublishedFields(announcement);

        int rows = announcementMapper.archivePublished(announcementId, bindingId);
        if (rows != 1)
        {
            return resolveArchiveZeroRows(bindingId, announcementId);
        }

        CourseAnnouncement saved = announcementMapper.selectById(announcementId);
        if (saved == null || saved.getStatus() != AnnouncementStatus.ARCHIVED)
        {
            throw persistenceFailure("公告归档结果不一致");
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public CourseAnnouncement getTeacherAnnouncement(Long bindingId, Long announcementId)
    {
        validateBindingId(bindingId);
        validateAnnouncementId(announcementId);

        Long userId = currentUserId();
        TeacherAnnouncementAccess access = requireAccess(bindingId, userId, now());
        CourseAnnouncement announcement = loadOwned(bindingId, announcementId);

        if (announcement.getStatus() == AnnouncementStatus.DRAFT
                && !access.authority().canPublish()
                && !userId.equals(announcement.getCreatedByUserId()))
        {
            throw new AnnouncementAccessDeniedException();
        }
        return announcement;
    }

    @Transactional(readOnly = true)
    public List<CourseAnnouncement> listTeacherAnnouncements(Long bindingId, AnnouncementStatus status)
    {
        validateBindingId(bindingId);

        Long userId = currentUserId();
        TeacherAnnouncementAccess access = requireAccess(bindingId, userId, now());
        return announcementMapper.selectTeacherVisibleByBindingId(
                bindingId, status, userId, access.authority().canPublish());
    }

    private TeacherAnnouncementAccess requireAccess(Long bindingId, Long userId, LocalDateTime at)
    {
        if (authorizationPort == null)
        {
            throw new AnnouncementAuthorizationUnavailableException("教学班授权服务尚未接入");
        }

        TeacherAnnouncementAccess access;
        try
        {
            access = authorizationPort.resolve(bindingId, userId, at);
        }
        catch (AnnouncementException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw new AnnouncementAuthorizationUnavailableException("教学班授权查询失败", e);
        }

        if (access == null
                || access.bindingId() == null
                || !access.bindingId().equals(bindingId)
                || access.teachingClassId() == null
                || access.authority() == null)
        {
            throw new AnnouncementAuthorizationUnavailableException("教学班授权结果不完整");
        }
        if (!access.authority().bindingActive()
                || !access.authority().teachingClassActive()
                || !access.authority().currentTeacher())
        {
            throw new AnnouncementAccessDeniedException();
        }
        return access;
    }

    private void requireCanPublish(TeacherAnnouncementAccess access)
    {
        if (!access.authority().canPublish())
        {
            throw new AnnouncementAccessDeniedException();
        }
    }

    private CourseAnnouncement loadOwned(Long bindingId, Long announcementId)
    {
        CourseAnnouncement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null || !bindingId.equals(announcement.getBindingId()))
        {
            // 其他教学班的公告按不存在处理，避免泄露对象存在性。
            throw new AnnouncementNotFoundException();
        }
        return announcement;
    }

    private CourseAnnouncement resolveUpdateZeroRows(Long bindingId, Long announcementId)
    {
        CourseAnnouncement current = reloadOwned(bindingId, announcementId);
        if (current.getStatus() != AnnouncementStatus.DRAFT)
        {
            throw stateConflict("只有草稿可以编辑");
        }
        throw concurrentUpdate();
    }

    private CourseAnnouncement resolvePublishZeroRows(Long bindingId, Long announcementId)
    {
        CourseAnnouncement current = reloadOwned(bindingId, announcementId);
        if (current.getStatus() == AnnouncementStatus.PUBLISHED)
        {
            requirePublishedFields(current);
            return current;
        }
        if (current.getStatus() != AnnouncementStatus.DRAFT)
        {
            throw stateConflict("只有草稿可以发布；已归档公告不能再次发布");
        }
        if (current.getPublishedByUserId() != null)
        {
            throw stateConflict("草稿的发布人状态异常");
        }
        throw concurrentUpdate();
    }

    private CourseAnnouncement resolveArchiveZeroRows(Long bindingId, Long announcementId)
    {
        CourseAnnouncement current = reloadOwned(bindingId, announcementId);
        if (current.getStatus() == AnnouncementStatus.ARCHIVED)
        {
            requirePublishedFields(current);
            return current;
        }
        if (current.getStatus() != AnnouncementStatus.PUBLISHED)
        {
            throw stateConflict("只有已发布公告可以归档");
        }
        requirePublishedFields(current);
        throw concurrentUpdate();
    }

    private CourseAnnouncement reloadOwned(Long bindingId, Long announcementId)
    {
        // 0 行更新后的分类必须使用当前读，避免 REPEATABLE READ 快照读返回旧状态。
        CourseAnnouncement current = announcementMapper.selectByIdForUpdate(announcementId);
        if (current == null || !bindingId.equals(current.getBindingId()))
        {
            throw new AnnouncementNotFoundException();
        }
        return current;
    }

    private void requirePublishedFields(CourseAnnouncement announcement)
    {
        if (announcement.getPublishedByUserId() == null || announcement.getOpensAt() == null)
        {
            throw stateConflict("已发布公告的发布信息不完整");
        }
    }

    private Long currentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("获取用户ID异常", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private LocalDateTime now()
    {
        return LocalDateTime.now(clock);
    }

    private void validateBindingId(Long bindingId)
    {
        if (bindingId == null)
        {
            throw new AnnouncementValidationException("bindingId 不能为空");
        }
    }

    private void validateAnnouncementId(Long announcementId)
    {
        if (announcementId == null)
        {
            throw new AnnouncementValidationException("announcementId 不能为空");
        }
    }

    private void validateDraftFields(String title, String body,
            LocalDateTime opensAt, LocalDateTime closesAt)
    {
        if (title == null || title.isBlank())
        {
            throw new AnnouncementValidationException("公告标题不能为空");
        }
        if (title.codePointCount(0, title.length()) > 200)
        {
            throw new AnnouncementValidationException("公告标题不能超过 200 个字符");
        }
        if (body == null || body.isBlank())
        {
            throw new AnnouncementValidationException("公告正文不能为空");
        }
        if (closesAt != null && (opensAt == null || !closesAt.isAfter(opensAt)))
        {
            throw new AnnouncementValidationException("关闭时间必须晚于开始时间");
        }
    }

    private AnnouncementStateConflictException stateConflict(String message)
    {
        return new AnnouncementStateConflictException(message);
    }

    private AnnouncementConcurrentUpdateException concurrentUpdate()
    {
        return new AnnouncementConcurrentUpdateException();
    }

    private AnnouncementPersistenceException persistenceFailure(String message)
    {
        return new AnnouncementPersistenceException(message);
    }
}
