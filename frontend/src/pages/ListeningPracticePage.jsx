import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import listeningApi from '../api/listeningApi';

export default function ListeningPracticePage() {
  const [parts, setParts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [mockLoading, setMockLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    listeningApi.getAllParts()
      .then(res => setParts(res.data?.data || []))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  const startMockTest = async () => {
    setMockLoading(true);
    try {
      const res = await listeningApi.assembleMockTest();
      const mockParts = res.data?.data || [];
      if (mockParts.length > 0) {
        const partIds = mockParts.map(p => p.partId).join(',');
        navigate(`/listening/exam?mode=mock-test&parts=${partIds}`);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setMockLoading(false);
    }
  };

  const partGroups = [1, 2, 3, 4].map(num => ({
    number: num,
    label: num === 1 ? 'Part 1 — Everyday Conversation' :
           num === 2 ? 'Part 2 — Everyday Monologue' :
           num === 3 ? 'Part 3 — Academic Discussion' :
                       'Part 4 — Academic Lecture',
    items: parts.filter(p => p.partNumber === num)
  }));

  if (loading) return (
    <div className="listening-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  return (
    <div className="listening-page">
      <div className="listening-content">
        <div className="listening-header">
          <div>
            <h1>Listening Practice</h1>
            <p className="subtitle">Select a part to practice or start a full mock test</p>
          </div>
          <button
            className="btn btn-primary btn-lg"
            onClick={startMockTest}
            disabled={mockLoading}
            id="start-mock-test-btn"
          >
            {mockLoading ? 'Preparing...' : 'Start Mock Test (40 Questions)'}
          </button>
        </div>

        {partGroups.map(group => (
          <div key={group.number} className="listening-section">
            <h2 className="listening-section-title">{group.label}</h2>
            <div className="listening-cards-grid">
              {group.items.map(part => (
                <div
                  key={part.partId}
                  className="card card-clickable listening-part-card"
                  onClick={() => navigate(`/listening/exam?mode=practice&parts=${part.partId}`)}
                >
                  <div className="listening-part-meta">
                    <span className={`badge badge-part${group.number}`}>Part {group.number}</span>
                    <span className="listening-duration">
                      {Math.floor((part.durationSeconds || 0) / 60)} min
                    </span>
                  </div>
                  <h3>{part.title}</h3>
                  <p className="listening-topic">{part.topic}</p>
                  <div className="listening-part-footer">
                    <span>{part.questionCount} questions</span>
                    <span className="card-action">Practice &rarr;</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
