package com.ruoyi.education.course.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementPersistenceException;
import com.ruoyi.education.course.exception.AnnouncementValidationException;

class AnnouncementExceptionHandlerTest
{
    private final AnnouncementExceptionHandler handler = new AnnouncementExceptionHandler();

    @Test
    void mapsValidationExceptionToBusinessCode()
    {
        AjaxResult result = handler.handleAnnouncementException(
                new AnnouncementValidationException("公告标题不能为空"));

        assertEquals(400, codeOf(result));
        assertEquals("公告标题不能为空", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void mapsNotFoundExceptionToBusinessCode()
    {
        AjaxResult result = handler.handleAnnouncementException(new AnnouncementNotFoundException());

        assertEquals(404, codeOf(result));
        assertEquals("公告不存在或不可访问", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void mapsPersistenceFailureToControlledMessageWithoutDatabaseDetails()
    {
        DataIntegrityViolationException cause = new DataIntegrityViolationException(
                "SQL insert into edu_course_announcement failed; constraint chk_edu_announcement_window");

        AjaxResult result = handler.handleAnnouncementException(
                new AnnouncementPersistenceException("公告草稿保存失败", cause));

        assertEquals(500, codeOf(result));
        assertEquals("公告草稿保存失败", result.get(AjaxResult.MSG_TAG));
        assertFalse(result.toString().contains("edu_course_announcement"));
        assertFalse(result.toString().contains("chk_edu_announcement_window"));
        assertFalse(result.toString().contains("SQL insert"));
    }

    private int codeOf(AjaxResult result)
    {
        return ((Integer) result.get(AjaxResult.CODE_TAG)).intValue();
    }
}
