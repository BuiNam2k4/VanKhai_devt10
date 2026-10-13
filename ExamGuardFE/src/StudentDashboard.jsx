import { useEffect, useState } from 'react'
import StudentExams from './StudentExams'
import ExamSessionView from './ExamSessionView'
import ClassDirectory from './ClassDirectory'

function sessionFromHash(hash) { return hash.match(/^#\/session\/([1-9]\d*)$/)?.[1] || null }

export default function StudentDashboard({ token }) {
  const [route, setRoute] = useState(() => window.location.hash)
  const sessionId = sessionFromHash(route)
  useEffect(() => {
    const changed = () => setRoute(window.location.hash)
    window.addEventListener('hashchange', changed)
    return () => window.removeEventListener('hashchange', changed)
  }, [])
  return sessionId
    ? <ExamSessionView key={sessionId} sessionId={sessionId} token={token} onBack={() => { window.location.hash = '' }} />
    : <div className="admin-layout"><aside className="sidebar"><div><span className="side-label">SINH VIÊN</span><button className={route !== '#/classes' ? 'active' : ''} onClick={() => { window.location.hash = '' }}><span className="nav-dot" />Bài thi được giao</button><button className={route === '#/classes' ? 'active' : ''} onClick={() => { window.location.hash = '/classes' }}><span className="nav-dot" />Lớp của tôi</button></div><p>Tra cứu lớp học, lịch thi và điều kiện tham gia.</p></aside><main className="workspace">{route === '#/classes' ? <ClassDirectory token={token} path="/student/classes" title="Lớp của tôi" description="Bạn được xem và tham gia các bài thi đã công bố cho những lớp này." /> : <StudentExams token={token} onSession={id => { window.location.hash = `/session/${id}` }} />}</main></div>
}
