package com.ruoyi.education.course.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.course.exception.AnnouncementException;

/**
 * 将公告业务异常转换为若依统一响应体中的业务码。
 *
 * <p>前端按 HTTP 200 + {@code code} 处理 400/403/404/409/503；
 * 不使用另建 Controller，也不改变若依其他异常的既有行为。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnnouncementExceptionHandler
{
    @ExceptionHandler(AnnouncementException.class)
    public AjaxResult handleAnnouncementException(AnnouncementException e)
    {
        return AjaxResult.error(e.getCode(), e.getMessage());
    }
}
