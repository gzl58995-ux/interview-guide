package interview.guide.modules.resume.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("简历实体求职方向")
class ResumeEntityTest {

  @Test
  @DisplayName("未显式设置时默认技术类方向")
  void defaultsToTech() {
    assertThat(new ResumeEntity().getJobDirection()).isEqualTo("TECH");
  }

  @Test
  @DisplayName("可以保存其他求职方向")
  void keepsConfiguredDirection() {
    ResumeEntity resume = new ResumeEntity();
    resume.setJobDirection("SALES_BD");
    assertThat(resume.getJobDirection()).isEqualTo("SALES_BD");
  }
}
