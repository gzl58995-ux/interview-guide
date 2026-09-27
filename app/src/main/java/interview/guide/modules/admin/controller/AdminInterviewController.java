package interview.guide.modules.admin.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.interview.model.InterviewDetailDTO;
import interview.guide.modules.interview.service.InterviewHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 管理员面试记录控制器
 * 平台级面试记录查询，访问路径由 AdminInterceptor 统一校验管理员身份
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/interview")
@RequiredArgsConstructor
@Tag(name = "管理员", description = "平台级数据管理（仅管理员）")
public class AdminInterviewController {

    private final InterviewHistoryService interviewHistoryService;

    /**
     * 获取任意用户的面试会话详情
     */
    @GetMapping("/sessions/{sessionId}/details")
    public Result<InterviewDetailDTO> getInterviewDetail(@PathVariable String sessionId) {
        return Result.success(interviewHistoryService.getInterviewDetailForAdmin(sessionId));
    }

    /**
     * 导出任意用户的面试报告为PDF
     */
    @GetMapping("/sessions/{sessionId}/export")
    public ResponseEntity<byte[]> exportInterviewPdf(@PathVariable String sessionId) {
        try {
            byte[] pdfBytes = interviewHistoryService.exportInterviewPdfForAdmin(sessionId);
            String filename = URLEncoder.encode("模拟面试报告_" + sessionId + ".pdf",
                StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败: sessionId={}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
