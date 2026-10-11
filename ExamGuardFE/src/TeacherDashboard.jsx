import { useState } from 'react'
import QuestionBank from './QuestionBank'
import ExamManagement from './ExamManagement'

export default function TeacherDashboard({ token }) {
  const [tab, setTab] = useState('questions')
  return <div className="admin-layout">
    <aside className="sidebar"><div><span className="side-label">GIẢNG VIÊN</span><button className={tab === 'questions' ? 'active' : ''} onClick={() => setTab('questions')}><span className="nav-dot" />Ngân hàng câu hỏi</button><button className={tab === 'exams' ? 'active' : ''} onClick={() => setTab('exams')}><span className="nav-dot" />Quản lý bài thi</button></div><p>Soạn câu hỏi, cấu hình bài thi và giao bài cho các lớp phụ trách.</p></aside>
    <main className="workspace">{tab === 'questions' ? <QuestionBank token={token} /> : <ExamManagement token={token} />}</main>
  </div>
}
