package interview.guide.modules.resume.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.export.PdfExportService;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.mapper.InterviewMapper;
import interview.guide.infrastructure.mapper.ResumeMapper;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.resume.model.ResumeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历历史文件下载")
class ResumeHistoryServiceTest {

    @Mock
    private ResumePersistenceService resumePersistenceService;
    @Mock
    private InterviewPersistenceService interviewPersistenceService;
    @Mock
    private PdfExportService pdfExportService;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private FileStorageService fileStorageService;

    private ResumeHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ResumeHistoryService(resumePersistenceService, interviewPersistenceService,
            pdfExportService, new ObjectMapper(), resumeMapper, interviewMapper, fileStorageService);
    }

    @Test
    @DisplayName("管理员下载原始简历返回文件内容与原始文件名")
    void downloadResumeFileForAdmin() {
        byte[] content = {1, 2, 3};
        ResumeEntity resume = resume("resumes/2026/09/28/abc_resume.pdf", "application/pdf");
        when(resumePersistenceService.requireById(1L)).thenReturn(resume);
        when(fileStorageService.downloadFile("resumes/2026/09/28/abc_resume.pdf")).thenReturn(content);

        ResumeHistoryService.ResumeFile result = service.downloadResumeFileForAdmin(1L);

        assertThat(result.content()).isEqualTo(content);
        assertThat(result.filename()).isEqualTo("resume.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("存储键缺失时下载简历抛出业务异常")
    void rejectDownloadWhenStorageKeyMissing() {
        ResumeEntity resume = resume("  ", "application/pdf");
        when(resumePersistenceService.requireById(1L)).thenReturn(resume);

        assertThatThrownBy(() -> service.downloadResumeFileForAdmin(1L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.STORAGE_DOWNLOAD_FAILED.getCode()));

        verifyNoInteractions(fileStorageService);
    }

    private ResumeEntity resume(String storageKey, String contentType) {
        ResumeEntity entity = new ResumeEntity();
        entity.setId(1L);
        entity.setUserId(1L);
        entity.setOriginalFilename("resume.pdf");
        entity.setStorageKey(storageKey);
        entity.setContentType(contentType);
        return entity;
    }
}
