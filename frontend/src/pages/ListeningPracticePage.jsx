import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import listeningApi from '../api/listeningApi';

const TOPICS = [
  { value: 'ACCOMMODATION', label: 'Accommodation / Booking' },
  { value: 'EDUCATION', label: 'Campus / Education' },
  { value: 'CULTURE', label: 'Culture / Museum' },
  { value: 'SCIENCE', label: 'Science / Technology' },
  { value: 'ENVIRONMENT', label: 'Environment / Nature' },
];

const PARTS = [
  { value: 1, label: 'Part 1 (Easy Dialogue)', desc: 'Everyday social transaction (2 speakers)', time: '3 mins' },
  { value: 2, label: 'Part 2 (Medium Monologue)', desc: 'Everyday context monologue (1 speaker)', time: '4 mins' },
  { value: 3, label: 'Part 3 (Hard Dialogue)', desc: 'Academic/Educational discussion (2-4 speakers)', time: '5 mins' },
  { value: 4, label: 'Part 4 (Hard Monologue)', desc: 'Academic lecture / monologue (1 speaker)', time: '5 mins' },
];

export default function ListeningPracticePage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('ai'); // 'ai' or 'curated'
  
  // AI generate states
  const [selectedTopic, setSelectedTopic] = useState('ACCOMMODATION');
  const [selectedPart, setSelectedPart] = useState(1);
  const [generateLoading, setGenerateLoading] = useState(false);
  const [error, setError] = useState('');

  // Curated list states
  const [parts, setParts] = useState([]);
  const [curatedLoading, setCuratedLoading] = useState(false);
  const [mockLoading, setMockLoading] = useState(false);

  useEffect(() => {
    if (activeTab === 'curated') {
      setCuratedLoading(true);
      listeningApi.getAllParts()
        .then(res => setParts(res.data?.data || []))
        .catch(err => console.error(err))
        .finally(() => setCuratedLoading(false));
    }
  }, [activeTab]);

  const startMockTest = async () => {
    setMockLoading(true);
    setError('');
    try {
      const res = await listeningApi.assembleMockTest();
      const mockParts = res.data?.data || [];
      if (mockParts.length > 0) {
        const partIds = mockParts.map(p => p.partId).join(',');
        navigate(`/listening/exam?mode=mock-test&parts=${partIds}`);
      } else {
        setError('No listening parts available to assemble a mock test.');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to assemble mock test');
      console.error(err);
    } finally {
      setMockLoading(false);
    }
  };

  const generateAiMockTest = async () => {
    setMockLoading(true);
    setError('');
    try {
      const res = await listeningApi.generateMockTest(selectedTopic);
      const mockParts = res.data?.data || [];
      if (mockParts.length > 0) {
        const partIds = mockParts.map(p => p.partId).join(',');
        navigate(`/listening/exam?mode=mock-test&parts=${partIds}`);
      } else {
        setError('Failed to generate AI Mock Test.');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to generate AI Mock Test');
      console.error(err);
    } finally {
      setMockLoading(false);
    }
  };

  const handleGenerate = async () => {
    setGenerateLoading(true);
    setError('');
    try {
      const res = await listeningApi.generatePart(selectedPart, selectedTopic);
      const partId = res.data?.data?.partId;
      if (partId) {
        navigate(`/listening/exam?mode=practice&parts=${partId}`);
      } else {
        throw new Error('No partId returned from AI generator');
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to generate listening test. Please try again.');
    } finally {
      setGenerateLoading(false);
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

  return (
    <div className="listening-page">
      <div className="listening-content">
        <div className="listening-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <button className="btn-back" onClick={() => navigate('/dashboard')} id="back-to-dashboard">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
              Home
            </button>
            <h1>Listening Practice</h1>
            <p className="subtitle">Practice your listening skills with AI-generated or curated tests</p>
          </div>
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button
              className="btn btn-outline"
              onClick={startMockTest}
              disabled={mockLoading}
              id="start-mock-test-btn"
              style={{ padding: '0.75rem 1.5rem', height: 'fit-content' }}
            >
              {mockLoading ? 'Preparing...' : 'Curated Mock Test'}
            </button>
            <button
              className="btn btn-primary"
              onClick={generateAiMockTest}
              disabled={mockLoading}
              id="generate-ai-mock-btn"
              style={{ padding: '0.75rem 1.5rem', height: 'fit-content' }}
            >
              {mockLoading ? 'Generating...' : 'Generate AI Mock Test'}
            </button>
          </div>
        </div>

        {/* Tab Container */}
        <div className="tab-container" style={{
          display: 'flex',
          gap: '1rem',
          marginBottom: '2rem',
          borderBottom: '1px solid var(--border-color)',
          paddingBottom: '0.5rem'
        }}>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'ai' ? 'active' : ''}`}
            onClick={() => { setActiveTab('ai'); setError(''); }}
            style={{
              padding: '0.75rem 1.5rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'ai' ? '3px solid var(--primary-color)' : '3px solid transparent',
              color: activeTab === 'ai' ? 'var(--primary-color)' : 'var(--text-secondary)',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s',
              fontSize: '1rem'
            }}
          >
            AI-Generated Tests
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'curated' ? 'active' : ''}`}
            onClick={() => { setActiveTab('curated'); setError(''); }}
            style={{
              padding: '0.75rem 1.5rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'curated' ? '3px solid var(--primary-color)' : '3px solid transparent',
              color: activeTab === 'curated' ? 'var(--primary-color)' : 'var(--text-secondary)',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s',
              fontSize: '1rem'
            }}
          >
            Curated Tests
          </button>
        </div>

        {error && <div className="error-msg" style={{ marginBottom: '1.5rem' }}>{error}</div>}

        {activeTab === 'ai' ? (
          <div className="config-form">
            {/* Topic Selection */}
            <div className="config-section">
              <h2>Select Topic</h2>
              <div className="topic-grid">
                {TOPICS.map((t) => (
                  <button
                    key={t.value}
                    type="button"
                    className={`topic-card ${selectedTopic === t.value ? 'active' : ''}`}
                    onClick={() => setSelectedTopic(t.value)}
                    id={`topic-${t.value.toLowerCase()}`}
                  >
                    <span className="topic-label">{t.label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Part Selection */}
            <div className="config-section">
              <h2>Select Part</h2>
              <div className="difficulty-grid">
                {PARTS.map((p) => (
                  <button
                    key={p.value}
                    type="button"
                    className={`difficulty-card ${selectedPart === p.value ? 'active' : ''}`}
                    onClick={() => setSelectedPart(p.value)}
                    id={`part-${p.value}`}
                  >
                    <div className="diff-top">
                      <span className="diff-label">{p.label}</span>
                      <span className="diff-time">{p.time}</span>
                    </div>
                    <p className="diff-desc">{p.desc}</p>
                  </button>
                ))}
              </div>
            </div>

            {/* Generate Button */}
            <button
              className="btn btn-primary btn-generate"
              onClick={handleGenerate}
              disabled={generateLoading}
              id="generate-listening-btn"
            >
              {generateLoading ? (
                <>
                  <span className="spinner"></span>
                  Generating with AI...
                </>
              ) : (
                'Generate Listening Test'
              )}
            </button>
          </div>
        ) : (
          <div className="curated-section">
            {curatedLoading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
                <span className="spinner"></span> &nbsp; Loading tests...
              </div>
            ) : parts.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: '4rem 2rem',
                border: '2px dashed var(--border-color)',
                borderRadius: '8px',
                color: 'var(--text-secondary)'
              }}>
                <p style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>No curated tests available</p>
                <p style={{ fontSize: '0.875rem' }}>Check back later for newly added curated listening tests.</p>
              </div>
            ) : (
              partGroups.map(group => (
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
              ))
            )}
          </div>
        )}

        {/* History Link */}
        <div className="config-footer" style={{ marginTop: '2rem' }}>
          <button className="btn btn-outline" onClick={() => navigate('/listening/history')} id="view-history-btn">
            View History
          </button>
        </div>
      </div>
    </div>
  );
}
