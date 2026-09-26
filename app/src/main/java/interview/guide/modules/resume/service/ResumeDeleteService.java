package interview.guide.modules.resume.service;

import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.resume.model.ResumeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 简历删除服务
 * 处理简历删除的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeDeleteService {
    
    private final ResumePersistenceService persistenceService;
    private final InterviewPersistenceService interviewPersistenceService;
    private final FileStorageService storageService;
    
    /**
     * 删除简历
     * 
     * @param id 简历ID
     * @throws interview.guide.common.exception.BusinessException 如果简历不存在
     */
    public void deleteResume(Long id) {
        log.info("收到删除简历请求: id={}", id);
        
        // 获取简历信息（用于删除存储文件），同时校验归属
        ResumeEntity resume = persistenceService.requireOwnedById(id);
        
        // 1. 删除存储的文件（FileStorageService 已内置存在性检查）
        try {
            storageService.deleteResume(resume.getStorageKey());
        } catch (Exception e) {
            log.warn("删除存储文件失败，继续删除数据库记录: {}", e.getMessage());
        }
        
        // 2. 删除面试会话（会自动删除面试答案）
        interviewPersistenceService.deleteSessionsByResumeId(id);
        
        // 3. 删除数据库记录（包括分析记录）
        persistenceService.deleteResume(id);
        
        log.info("简历删除完成: id={}", id);
    }
}

