import BatchUploadPage from '../components/batch-upload/BatchUploadPage';
import { resumeUploadAdapter } from '../api/batchUpload';
import { JOB_DIRECTION_OPTIONS } from '../constants/jobDirections';
import { RESUME_FILE_POLICY } from '../utils/batchUpload';

export default function UploadPage({ onBack }: { onBack: () => void }) {
  return <BatchUploadPage
    entityLabel="简历"
    policy={RESUME_FILE_POLICY}
    adapter={resumeUploadAdapter}
    categoryOptions={JOB_DIRECTION_OPTIONS}
    categoryLabel="求职方向"
    categoryRequired
    backLabel="返回简历库"
    onBack={onBack}
  />;
}
