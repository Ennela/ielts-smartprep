import { useState, useEffect } from 'react';
import vocabApi from '../api/vocabApi';

export default function VocabularyPage() {
  const [vocabList, setVocabList] = useState([]);
  const [dueList, setDueList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState('review'); // 'review' or 'all'
  const [searchTerm, setSearchTerm] = useState('');

  // Manual Add Modal State
  const [showAddModal, setShowAddModal] = useState(false);
  const [newWord, setNewWord] = useState({
    word: '',
    phonetic: '',
    partOfSpeech: 'adjective',
    meaningVi: '',
    example: '',
    collocation: '',
    cefrLevel: 'B2',
    sourceSkill: '',
    sourceRef: ''
  });
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState('');

  // Review Session State
  const [reviewIndex, setReviewIndex] = useState(0);
  const [isFlipped, setIsFlipped] = useState(false);
  const [reviewCount, setReviewCount] = useState(0);

  useEffect(() => {
    loadVocabulary();
  }, []);

  const loadVocabulary = async () => {
    setLoading(true);
    setError('');
    try {
      const [allRes, dueRes] = await Promise.all([
        vocabApi.getAllVocab(),
        vocabApi.getDueVocab()
      ]);
      setVocabList(allRes.data?.data || []);
      setDueList(dueRes.data?.data || []);
      setReviewIndex(0);
      setIsFlipped(false);
    } catch (err) {
      setError(err.message || 'Failed to fetch vocabulary data.');
    } finally {
      setLoading(false);
    }
  };

  const handleManualAdd = async (e) => {
    e.preventDefault();
    if (!newWord.word || !newWord.meaningVi) {
      setAddError('Word and Vietnamese meaning are required.');
      return;
    }
    setAddLoading(true);
    setAddError('');
    try {
      await vocabApi.addVocab(newWord);
      setShowAddModal(false);
      setNewWord({
        word: '',
        phonetic: '',
        partOfSpeech: 'adjective',
        meaningVi: '',
        example: '',
        collocation: '',
        cefrLevel: 'B2',
        sourceSkill: '',
        sourceRef: ''
      });
      await loadVocabulary();
    } catch (err) {
      setAddError(err.message || 'Failed to add word.');
    } finally {
      setAddLoading(false);
    }
  };

  const handleReviewRating = async (grade) => {
    if (dueList.length === 0) return;
    const currentItem = dueList[reviewIndex];
    try {
      await vocabApi.reviewVocab(currentItem.vocabId, grade);
      setReviewCount((prev) => prev + 1);

      // Transition to next card
      setIsFlipped(false);
      setTimeout(() => {
        if (reviewIndex + 1 < dueList.length) {
          setReviewIndex((prev) => prev + 1);
        } else {
          // Finished session, reload
          loadVocabulary();
        }
      }, 300);
    } catch (err) {
      setError('Failed to submit review: ' + err.message);
    }
  };

  const handleDeleteWord = async (vocabId) => {
    if (!window.confirm('Are you sure you want to remove this word from your builder?')) return;
    try {
      await vocabApi.deleteVocab(vocabId);
      loadVocabulary();
    } catch (err) {
      setError('Failed to delete word: ' + err.message);
    }
  };

  const filteredAllList = vocabList.filter((item) => {
    const term = searchTerm.toLowerCase();
    return (
      item.word.toLowerCase().includes(term) ||
      item.meaningVi.toLowerCase().includes(term) ||
      (item.partOfSpeech && item.partOfSpeech.toLowerCase().includes(term))
    );
  });

  const getCefrBadgeStyle = (level) => {
    switch (level) {
      case 'C2': return { background: '#ba1a1a', color: '#ffffff' };
      case 'C1': return { background: '#842c00', color: '#ffffff' };
      case 'B2':
      default: return { background: '#003fb1', color: '#ffffff' };
    }
  };

  const getRelativeDueDate = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diffTime = date.getTime() - now.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays < 0) {
      return <span style={{ color: 'var(--error)', fontWeight: 600 }}>Overdue</span>;
    } else if (diffDays === 0) {
      return <span style={{ color: 'var(--secondary)', fontWeight: 600 }}>Due today</span>;
    } else if (diffDays === 1) {
      return `Tomorrow`;
    } else {
      return `In ${diffDays} days`;
    }
  };

  return (
    <div className="vocabulary-page" style={{ padding: '32px', maxWidth: '1200px', margin: '0 auto', width: '100%' }}>
      {/* Dynamic Flip Card CSS Rules Injection */}
      <style>{`
        .vocab-tabs {
          display: flex;
          border-bottom: 2px solid var(--outline-variant);
          margin-bottom: 32px;
          gap: 24px;
        }
        .vocab-tab-btn {
          background: none;
          border: none;
          font-family: var(--font-heading);
          font-size: 1.1rem;
          font-weight: 600;
          color: var(--on-surface-variant);
          padding: 12px 4px;
          cursor: pointer;
          position: relative;
          transition: color var(--transition);
        }
        .vocab-tab-btn:hover {
          color: var(--primary);
        }
        .vocab-tab-btn.active {
          color: var(--primary);
        }
        .vocab-tab-btn.active::after {
          content: '';
          position: absolute;
          bottom: -2px;
          left: 0;
          right: 0;
          height: 2px;
          background: var(--primary);
        }
        .vocab-badge-count {
          background: var(--primary-container);
          color: var(--on-primary-container);
          font-size: 0.75rem;
          padding: 2px 8px;
          border-radius: 99px;
          margin-left: 6px;
          font-weight: 700;
        }
        .vocab-badge-due {
          background: var(--error);
          color: white;
          font-size: 0.75rem;
          padding: 2px 8px;
          border-radius: 99px;
          margin-left: 6px;
          font-weight: 700;
        }
        .metric-cards-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
          gap: 24px;
          margin-bottom: 32px;
        }
        .metric-item-card {
          background: var(--surface-container-lowest);
          border: 1px solid var(--outline-variant);
          border-radius: var(--radius-xl);
          padding: 24px;
          text-align: center;
          box-shadow: var(--shadow-sm);
        }
        .metric-item-num {
          font-size: 2.5rem;
          font-weight: 800;
          color: var(--primary);
          line-height: 1.2;
          margin-bottom: 4px;
        }
        .metric-item-lbl {
          font-size: 0.85rem;
          color: var(--on-surface-variant);
          text-transform: uppercase;
          font-weight: 600;
          letter-spacing: 0.05em;
        }
        .flashcard-wrapper {
          perspective: 1000px;
          width: 100%;
          max-width: 500px;
          height: 320px;
          margin: 0 auto 32px auto;
        }
        .flashcard-inner {
          position: relative;
          width: 100%;
          height: 100%;
          transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
          transform-style: preserve-3d;
        }
        .flashcard-inner.flipped {
          transform: rotateY(180deg);
        }
        .flashcard-front, .flashcard-back {
          position: absolute;
          width: 100%;
          height: 100%;
          backface-visibility: hidden;
          border-radius: var(--radius-xl);
          padding: 32px;
          display: flex;
          flex-direction: column;
          box-shadow: var(--shadow-lg);
          border: 1px solid var(--outline-variant);
        }
        .flashcard-front {
          background: linear-gradient(135deg, var(--surface-container-lowest) 0%, var(--surface-container-low) 100%);
          justify-content: center;
          align-items: center;
        }
        .flashcard-back {
          background: linear-gradient(135deg, var(--surface-container-low) 0%, var(--surface-container-lowest) 100%);
          transform: rotateY(180deg);
          overflow-y: auto;
        }
        .rating-btns {
          display: grid;
          grid-template-columns: repeat(4, 1fr);
          gap: 12px;
          max-width: 500px;
          margin: 0 auto;
        }
        .btn-rate {
          padding: 14px 10px;
          border: none;
          border-radius: var(--radius-lg);
          color: white;
          font-family: var(--font-heading);
          font-weight: 700;
          font-size: 0.9rem;
          cursor: pointer;
          transition: transform var(--transition), box-shadow var(--transition);
          display: flex;
          flex-direction: column;
          align-items: center;
        }
        .btn-rate:hover {
          transform: translateY(-2px);
          box-shadow: var(--shadow);
        }
        .btn-rate-again { background: #ba1a1a; }
        .btn-rate-hard { background: #842c00; }
        .btn-rate-good { background: #003fb1; }
        .btn-rate-easy { background: #006c4a; }
        .rate-lbl-sm {
          font-size: 0.7rem;
          font-weight: 500;
          opacity: 0.85;
          margin-top: 2px;
        }
        .modal-overlay {
          position: fixed;
          inset: 0;
          background: rgba(18, 28, 40, 0.4);
          backdrop-filter: blur(4px);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 999;
          padding: 16px;
        }
        .modal-card {
          background: var(--surface-container-lowest);
          border: 1px solid var(--outline-variant);
          border-radius: var(--radius-xl);
          width: 100%;
          max-width: 560px;
          box-shadow: var(--shadow-lg);
          overflow: hidden;
        }
        .modal-header {
          padding: 20px 24px;
          border-bottom: 1px solid var(--outline-variant);
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        .modal-body {
          padding: 24px;
          max-height: 70vh;
          overflow-y: auto;
        }
        .vocab-grid-list {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
          gap: 20px;
        }
        .vocab-item-card {
          background: var(--surface-container-lowest);
          border: 1px solid var(--outline-variant);
          border-radius: var(--radius-lg);
          padding: 20px;
          display: flex;
          flex-direction: column;
          box-shadow: var(--shadow-sm);
          position: relative;
        }
        .vocab-delete-btn {
          position: absolute;
          top: 16px;
          right: 16px;
          background: transparent;
          border: none;
          color: var(--outline);
          cursor: pointer;
          transition: color var(--transition);
        }
        .vocab-delete-btn:hover {
          color: var(--error);
        }
      `}</style>

      {/* Title Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h1 style={{ fontSize: '2rem', fontWeight: 700, margin: 0 }}>Vocabulary Builder</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.95rem', marginTop: '4px' }}>
            Master IELTS vocabulary using Spaced Repetition (SRS).
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
          <span className="material-symbols-outlined" style={{ fontSize: 20 }}>add</span>
          Add Custom Word
        </button>
      </div>

      {error && <div className="error-msg">{error}</div>}

      {/* Metrics Row */}
      <div className="metric-cards-grid">
        <div className="metric-item-card">
          <div className="metric-item-num" style={{ color: 'var(--error)' }}>{dueList.length}</div>
          <div className="metric-item-lbl">Due for Review</div>
        </div>
        <div className="metric-item-card">
          <div className="metric-item-num">{vocabList.length}</div>
          <div className="metric-item-lbl">Total Words Saved</div>
        </div>
        <div className="metric-item-card">
          <div className="metric-item-num" style={{ color: 'var(--secondary)' }}>{reviewCount}</div>
          <div className="metric-item-lbl">Reviewed Today</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="vocab-tabs">
        <button
          className={`vocab-tab-btn ${activeTab === 'review' ? 'active' : ''}`}
          onClick={() => { setActiveTab('review'); setIsFlipped(false); }}
        >
          Daily Review
          {dueList.length > 0 ? (
            <span className="vocab-badge-due">{dueList.length}</span>
          ) : (
            <span className="vocab-badge-count">0</span>
          )}
        </button>
        <button
          className={`vocab-tab-btn ${activeTab === 'all' ? 'active' : ''}`}
          onClick={() => setActiveTab('all')}
        >
          Word Bank
          <span className="vocab-badge-count">{vocabList.length}</span>
        </button>
      </div>

      {loading ? (
        <div className="loading-spinner">
          <span className="spinner" style={{ width: 32, height: 32 }} />
          Loading vocabulary...
        </div>
      ) : (
        <div>
          {/* TAB 1: DAILY REVIEW */}
          {activeTab === 'review' && (
            <div style={{ padding: '16px 0' }}>
              {dueList.length === 0 ? (
                <div className="card" style={{ textAlign: 'center', padding: '48px 24px', maxWidth: '600px', margin: '0 auto' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '48px', color: 'var(--secondary)', marginBottom: '16px' }}>
                    check_circle
                  </span>
                  <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '8px' }}>All caught up!</h3>
                  <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.92rem', marginBottom: '24px' }}>
                    You have no cards due for review today. Add more vocabulary cards while practicing Reading, Listening, or Writing.
                  </p>
                  <button className="btn btn-outline" onClick={() => setActiveTab('all')}>
                    Browse Word Bank
                  </button>
                </div>
              ) : (
                <div>
                  <div style={{ textAlign: 'center', marginBottom: '20px', fontSize: '0.9rem', color: 'var(--outline)', fontWeight: 600 }}>
                    Card {reviewIndex + 1} of {dueList.length}
                  </div>

                  {/* Flashcard Area */}
                  <div className="flashcard-wrapper" onClick={() => setIsFlipped(!isFlipped)}>
                    <div className={`flashcard-inner ${isFlipped ? 'flipped' : ''}`}>
                      {/* FRONT OF CARD */}
                      <div className="flashcard-front">
                        <span style={{
                          padding: '4px 12px',
                          borderRadius: 'var(--radius-full)',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          textTransform: 'uppercase',
                          marginBottom: '20px',
                          ...getCefrBadgeStyle(dueList[reviewIndex].cefrLevel)
                        }}>
                          {dueList[reviewIndex].cefrLevel || 'B2'}
                        </span>
                        <h2 style={{ fontSize: '2.5rem', fontWeight: 800, margin: '0 0 8px 0', color: 'var(--primary)' }}>
                          {dueList[reviewIndex].word}
                        </h2>
                        {dueList[reviewIndex].phonetic && (
                          <p style={{ fontStyle: 'italic', color: 'var(--color-text-muted)', fontSize: '1.1rem', margin: '0 0 6px 0' }}>
                            {dueList[reviewIndex].phonetic}
                          </p>
                        )}
                        <span className="badge-listening" style={{ textTransform: 'uppercase', fontSize: '0.72rem' }}>
                          {dueList[reviewIndex].partOfSpeech || 'adjective'}
                        </span>

                        <p style={{ marginTop: '36px', fontSize: '0.8rem', color: 'var(--outline)', fontWeight: 600 }}>
                          Click card to flip
                        </p>
                      </div>

                      {/* BACK OF CARD */}
                      <div className="flashcard-back">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', borderBottom: '1px solid var(--outline-variant)', paddingBottom: '8px' }}>
                          <span style={{ fontWeight: 800, color: 'var(--primary)', fontSize: '1.2rem' }}>
                            {dueList[reviewIndex].word}
                          </span>
                          <span style={{
                            padding: '2px 10px',
                            borderRadius: '99px',
                            fontSize: '0.7rem',
                            fontWeight: 700,
                            ...getCefrBadgeStyle(dueList[reviewIndex].cefrLevel)
                          }}>
                            {dueList[reviewIndex].cefrLevel || 'B2'}
                          </span>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', textAlign: 'left' }}>
                          <div>
                            <span style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--outline)' }}>Meaning</span>
                            <p style={{ fontSize: '1.05rem', fontWeight: 600, color: 'var(--on-surface)', marginTop: '2px' }}>
                              {dueList[reviewIndex].meaningVi}
                            </p>
                          </div>

                          {dueList[reviewIndex].collocation && (
                            <div>
                              <span style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--outline)' }}>Collocation</span>
                              <p style={{ fontSize: '0.9rem', color: 'var(--color-text-secondary)', marginTop: '2px', fontWeight: 500 }}>
                                {dueList[reviewIndex].collocation}
                              </p>
                            </div>
                          )}

                          {dueList[reviewIndex].example && (
                            <div>
                              <span style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--outline)' }}>Example</span>
                              <p style={{ fontSize: '0.9rem', fontStyle: 'italic', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                                "{dueList[reviewIndex].example}"
                              </p>
                            </div>
                          )}

                          {dueList[reviewIndex].sourceSkill && (
                            <div style={{ display: 'flex', gap: '6px', alignItems: 'center', marginTop: '4px' }}>
                              <span className="material-symbols-outlined" style={{ fontSize: 16, color: 'var(--outline)' }}>link</span>
                              <span style={{ fontSize: '0.72rem', color: 'var(--outline)', fontWeight: 500 }}>
                                Source: {dueList[reviewIndex].sourceSkill} {dueList[reviewIndex].sourceRef ? `(${dueList[reviewIndex].sourceRef})` : ''}
                              </span>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Rating Choice Panel */}
                  <div style={{ textAlign: 'center', opacity: isFlipped ? 1 : 0.3, pointerEvents: isFlipped ? 'auto' : 'none', transition: 'opacity 0.2s ease' }}>
                    <p style={{ fontSize: '0.85rem', color: 'var(--outline)', fontWeight: 600, marginBottom: '12px' }}>
                      How well did you remember this word?
                    </p>
                    <div className="rating-btns">
                      <button className="btn-rate btn-rate-again" onClick={() => handleReviewRating('AGAIN')}>
                        Again
                        <span className="rate-lbl-sm">1d</span>
                      </button>
                      <button className="btn-rate btn-rate-hard" onClick={() => handleReviewRating('HARD')}>
                        Hard
                        <span className="rate-lbl-sm">1d</span>
                      </button>
                      <button className="btn-rate btn-rate-good" onClick={() => handleReviewRating('GOOD')}>
                        Good
                        <span className="rate-lbl-sm">6d</span>
                      </button>
                      <button className="btn-rate btn-rate-easy" onClick={() => handleReviewRating('EASY')}>
                        Easy
                        <span className="rate-lbl-sm">14d+</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: WORD BANK */}
          {activeTab === 'all' && (
            <div>
              {/* Search Control */}
              <div style={{ marginBottom: '24px', position: 'relative' }}>
                <span className="material-symbols-outlined" style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: 'var(--outline)' }}>
                  search
                </span>
                <input
                  type="text"
                  placeholder="Search by word, definition, part of speech..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '12px 16px 12px 48px',
                    borderRadius: 'var(--radius-lg)',
                    border: '1px solid var(--outline-variant)',
                    background: 'var(--surface-container-lowest)',
                    fontSize: '0.95rem'
                  }}
                />
              </div>

              {filteredAllList.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '48px', color: 'var(--outline)' }}>
                  No vocabulary words found matching your search.
                </div>
              ) : (
                <div className="vocab-grid-list">
                  {filteredAllList.map((item) => (
                    <div key={item.vocabId} className="vocab-item-card">
                      <button className="vocab-delete-btn" onClick={() => handleDeleteWord(item.vocabId)}>
                        <span className="material-symbols-outlined" style={{ fontSize: 18 }}>delete</span>
                      </button>
                      
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                        <span style={{
                          padding: '1px 8px',
                          borderRadius: '99px',
                          fontSize: '0.65rem',
                          fontWeight: 700,
                          ...getCefrBadgeStyle(item.cefrLevel)
                        }}>
                          {item.cefrLevel || 'B2'}
                        </span>
                        <span className="badge-reading" style={{ textTransform: 'uppercase', fontSize: '0.65rem' }}>
                          {item.partOfSpeech || 'adjective'}
                        </span>
                      </div>

                      <h3 style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--primary)', marginBottom: '2px' }}>
                        {item.word}
                      </h3>
                      {item.phonetic && (
                        <p style={{ fontStyle: 'italic', color: 'var(--color-text-muted)', fontSize: '0.85rem', marginBottom: '12px' }}>
                          {item.phonetic}
                        </p>
                      )}

                      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '0.88rem' }}>
                        <div>
                          <span style={{ fontWeight: 700, fontSize: '0.72rem', color: 'var(--outline)', textTransform: 'uppercase' }}>Meaning</span>
                          <p style={{ color: 'var(--on-surface)', fontWeight: 500, marginTop: '1px' }}>{item.meaningVi}</p>
                        </div>

                        {item.collocation && (
                          <div>
                            <span style={{ fontWeight: 700, fontSize: '0.72rem', color: 'var(--outline)', textTransform: 'uppercase' }}>Collocation</span>
                            <p style={{ color: 'var(--color-text-secondary)', marginTop: '1px' }}>{item.collocation}</p>
                          </div>
                        )}

                        {item.example && (
                          <div>
                            <span style={{ fontWeight: 700, fontSize: '0.72rem', color: 'var(--outline)', textTransform: 'uppercase' }}>Example</span>
                            <p style={{ color: 'var(--color-text-secondary)', fontStyle: 'italic', marginTop: '1px' }}>"{item.example}"</p>
                          </div>
                        )}
                      </div>

                      <div style={{ borderTop: '1px solid var(--outline-variant)', marginTop: '16px', paddingTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', color: 'var(--outline)' }}>
                        <div>Due: {getRelativeDueDate(item.dueDate)}</div>
                        <div style={{ fontWeight: 600 }}>Interval: {item.intervalDays}d</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ADD CUSTOM WORD MODAL */}
      {showAddModal && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-header">
              <h3 style={{ fontSize: '1.2rem', fontWeight: 700, margin: 0 }}>Add Custom Vocabulary</h3>
              <button
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--outline)' }}
                onClick={() => setShowAddModal(false)}
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
            <form onSubmit={handleManualAdd}>
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {addError && <div className="error-msg">{addError}</div>}
                
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group" style={{ margin: 0 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>Word *</label>
                    <input
                      type="text"
                      placeholder="e.g. ubiquitous"
                      value={newWord.word}
                      onChange={(e) => setNewWord({ ...newWord, word: e.target.value })}
                      required
                    />
                  </div>

                  <div className="form-group" style={{ margin: 0 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>Phonetic</label>
                    <input
                      type="text"
                      placeholder="e.g. /juːˈbɪkwɪtəs/"
                      value={newWord.phonetic}
                      onChange={(e) => setNewWord({ ...newWord, phonetic: e.target.value })}
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group" style={{ margin: 0 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>Part of Speech</label>
                    <select
                      value={newWord.partOfSpeech}
                      onChange={(e) => setNewWord({ ...newWord, partOfSpeech: e.target.value })}
                      style={{
                        width: '100%',
                        padding: '12px 16px',
                        border: '1px solid var(--outline-variant)',
                        borderRadius: 'var(--radius-sm)',
                        background: 'var(--surface-container-lowest)',
                        fontSize: '0.95rem',
                        outline: 'none'
                      }}
                    >
                      <option value="noun">Noun</option>
                      <option value="verb">Verb</option>
                      <option value="adjective">Adjective</option>
                      <option value="adverb">Adverb</option>
                      <option value="phrase">Phrase</option>
                      <option value="preposition">Preposition</option>
                    </select>
                  </div>

                  <div className="form-group" style={{ margin: 0 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>CEFR Level</label>
                    <select
                      value={newWord.cefrLevel}
                      onChange={(e) => setNewWord({ ...newWord, cefrLevel: e.target.value })}
                      style={{
                        width: '100%',
                        padding: '12px 16px',
                        border: '1px solid var(--outline-variant)',
                        borderRadius: 'var(--radius-sm)',
                        background: 'var(--surface-container-lowest)',
                        fontSize: '0.95rem',
                        outline: 'none'
                      }}
                    >
                      <option value="B2">B2</option>
                      <option value="C1">C1</option>
                      <option value="C2">C2</option>
                    </select>
                  </div>
                </div>

                <div className="form-group" style={{ margin: 0 }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>Meaning (Vietnamese) *</label>
                  <input
                    type="text"
                    placeholder="e.g. phổ biến, có mặt ở khắp nơi"
                    value={newWord.meaningVi}
                    onChange={(e) => setNewWord({ ...newWord, meaningVi: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group" style={{ margin: 0 }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>Collocation</label>
                  <input
                    type="text"
                    placeholder="e.g. ubiquitous presence"
                    value={newWord.collocation}
                    onChange={(e) => setNewWord({ ...newWord, collocation: e.target.value })}
                  />
                </div>

                <div className="form-group" style={{ margin: 0 }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>Example Sentence</label>
                  <textarea
                    placeholder="e.g. Mobile phones are ubiquitous in modern society."
                    value={newWord.example}
                    onChange={(e) => setNewWord({ ...newWord, example: e.target.value })}
                    style={{
                      width: '100%',
                      padding: '12px 16px',
                      border: '1px solid var(--outline-variant)',
                      borderRadius: 'var(--radius-sm)',
                      background: 'var(--surface-container-lowest)',
                      fontSize: '0.95rem',
                      fontFamily: 'var(--font-body)',
                      minHeight: '80px',
                      outline: 'none',
                      resize: 'vertical'
                    }}
                  />
                </div>
              </div>

              <div style={{ padding: '16px 24px', borderTop: '1px solid var(--outline-variant)', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button type="button" className="btn btn-outline" onClick={() => setShowAddModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={addLoading}>
                  {addLoading && <span className="spinner" style={{ width: 16, height: 16 }} />}
                  Save Word
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
