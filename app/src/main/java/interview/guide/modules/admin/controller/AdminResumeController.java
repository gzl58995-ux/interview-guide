package interview.guide.modules.admin.controller;

import interview.guide.common.result.PageResult;
import interview.guide.common.result.Result;
import interview.guide.modules.admin.model.AdminResumeDTO;
import interview.guide.modules.admin.model.AdminResumeOwnerDTO;
import interview.guide.modules.admin.model.AdminResumeQueryRequest;
import interview.guide.modules.admin.model.AdminResumeStatsDTO;
import interview.guide.modules.admin.service.AdminResumeService;
import interview.guide.modules.resume.model.ResumeDetailDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 管理员简历控制器
 * 平台级简历管理，访问路径由 AdminInterceptor 统一校验管理员身份
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/resumes")
@RequiredArgsConstructor
@Tag(name = "管理员", description = "平台级数据管理（仅管理员）")
public class AdminResumeController {

    private final AdminResumeService adminResumeService;

    /**
     * 获取全平台所有用户的简历列表
     */
    @GetMapping
    public Result<List<AdminResumeDTO>> getAllResumes() {
        return Result.success(adminResumeService.getAllResumes());
    }

    /**
     * 按查询表单分页搜索全平台简历
     *
     * @param query 查询表单：关键词、分析状态、归属用户、排序、页码、每页条数
     */
    @GetMapping("/page")
    public Result<PageResult<AdminResumeDTO>> queryResumes(
        @Valid @ModelAttribute AdminResumeQueryRequest query) {
        return Result.success(adminResumeService.queryResumes(query));
    }

    /**
     * 获取全平台简历统计信息
     */
    @GetMapping("/statistics")
    public Result<AdminResumeStatsDTO> getStatistics() {
        return Result.success(adminResumeService.getStatistics());
    }

    /**
     * 获取拥有简历的归属用户列表，用于用户筛选下拉
     */
    @GetMapping("/owners")
    public Result<List<AdminResumeOwnerDTO>> getOwners() {
        return Result.success(adminResumeService.getOwners());
    }

    /**
     * 获取任意用户的简历详情（平台级，包含分析历史与面试记录）
     */
    @GetMapping("/{id}/detail")
    public Result<ResumeDetailDTO> getResumeDetail(@PathVariable Long id) {
        return Result.success(adminResumeService.getResumeDetail(id));
    }

    /**
     * 导出任意用户的简历分析报告为PDF
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportAnalysisPdf(@PathVariable Long id) {
        try {
            var result = adminResumeService.exportAnalysisPdf(id);
            String filename = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.pdfBytes());
        } catch (Exception e) {
            log.error("导出PDF失败: resumeId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
