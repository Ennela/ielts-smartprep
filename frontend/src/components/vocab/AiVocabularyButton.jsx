import { useState } from 'react';
import vocabApi from '../../api/vocabApi';

export default function AiVocabularyButton({ skillType, sourceId }) {
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [suggestions, setSuggestions] = useState([]);
  const [selectedWords, setSelectedWords] = useState({});
  const [error, setError] = useState('');
  const [saveLoading, setSaveLoading] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState('');

  const handleOpen = async () => {
    setIsOpen(true);
    setLoading(true);
    setError('');
    setSaveSuccess('');
    setSelectedWords({});
    try {
      const res = await vocabApi.aiSuggestVocab(skillType, sourceId);
      const items = res.data?.data || [];
      setSuggestions(items);

      // Select all by default
      const defaultSelected = {};
      items.forEach((item) => {
        defaultSelected[item.word] = true;
      });
      setSelectedWords(defaultSelected);
    } catch (err) {
      setError(err.message || 'Failed to generate vocabulary suggestions.');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSelect = (word) => {
    setSelectedWords((prev) => ({
      ...prev,
      [word]: !prev[word]
    }));
  };

  const handleSelectAll = (checked) => {
    const nextSelected = {};
    if (checked) {
      suggestions.forEach((item) => {
        nextSelected[item.word] = true;
      });
    }
    setSelectedWords(nextSelected);
  };

  const handleSave = async () => {
    const toSave = suggestions.filter((item) => selectedWords[item.word]).map((item) => ({
      word: item.word,
      phonetic: item.phonetic,
      partOfSpeech: item.partOfSpeech,
      meaningVi: item.meaningVi,
      example: item.example,
      collocation: item.collocation,
      cefrLevel: item.cefrLevel,
      sourceSkill: skillType,
      sourceRef: `Source ID: ${sourceId}`
    }));

    if (toSave.length === 0) {
      setError('Please select at least one word.');
      return;
    }

    setSaveLoading(true);
    setError('');
    try {
      const res = await vocabApi.bulkSaveVocab(toSave);
      setSaveSuccess(`Added ${res.data?.data?.savedCount || toSave.length} words to your Vocabulary Builder!`);
      setTimeout(() => {
        setIsOpen(false);
        setSaveSuccess('');
      }, 2000);
    } catch (err) {
      setError(err.message || 'Failed to save vocabulary.');
    } finally {
      setSaveLoading(false);
    }
  };

  const isAllSelected = suggestions.length > 0 && suggestions.every((item) => selectedWords[item.word]);

  const getCefrBadgeStyle = (level) => {
    switch (level) {
      case 'C2': return { background: '#ba1a1a', color: '#ffffff' };
      case 'C1': return { background: '#842c00', color: '#ffffff' };
      case 'B2':
      default: return { background: '#003fb1', color: '#ffffff' };
    }
  };

  return (
    <>
      {/* Floating Action Button */}
      <button
        onClick={handleOpen}
        style={{
          position: 'fixed',
          bottom: '32px',
          right: '32px',
          zIndex: 190,
          background: 'linear-gradient(135deg, var(--primary) 0%, var(--primary-container) 100%)',
          color: 'var(--on-primary)',
          border: 'none',
          borderRadius: '999px',
          padding: '14px 24px',
          fontFamily: 'var(--font-heading)',
          fontWeight: 700,
          fontSize: '0.9rem',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          cursor: 'pointer',
          boxShadow: '0 8px 30px rgba(0, 63, 177, 0.35)',
          transition: 'all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)'
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'translateY(-2px)';
          e.currentTarget.style.boxShadow = '0 12px 36px rgba(0, 63, 177, 0.45)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow = '0 8px 30px rgba(0, 63, 177, 0.35)';
        }}
      >
        <span className="material-symbols-outlined" style={{ fontSize: 22 }}>auto_awesome</span>
        AI Vocabulary
      </button>

      {/* Suggestion Modal Dialog */}
      {isOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(18, 28, 40, 0.45)',
          backdropFilter: 'blur(6px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 999,
          padding: '16px'
        }}>
          <style>{`
            .suggest-modal-card {
              background: var(--surface-container-lowest);
              border: 1px solid var(--outline-variant);
              border-radius: var(--radius-xl);
              width: 100%;
              max-width: 600px;
              box-shadow: var(--shadow-lg);
              display: flex;
              flex-direction: column;
              max-height: 85vh;
            }
            .suggest-modal-header {
              padding: 20px 24px;
              border-bottom: 1px solid var(--outline-variant);
              display: flex;
              justify-content: space-between;
              align-items: center;
              flex-shrink: 0;
            }
            .suggest-modal-body {
              padding: 24px;
              overflow-y: auto;
              flex: 1;
            }
            .suggest-modal-footer {
              padding: 16px 24px;
              border-top: 1px solid var(--outline-variant);
              display: flex;
              justify-content: flex-end;
              gap: 12px;
              align-items: center;
              flex-shrink: 0;
            }
            .suggest-item {
              display: flex;
              gap: 16px;
              padding: 16px;
              border-radius: var(--radius-lg);
              background: var(--surface-container-low);
              border: 1px solid var(--outline-variant);
              margin-bottom: 14px;
              transition: border-color var(--transition);
              cursor: pointer;
            }
            .suggest-item:hover {
              border-color: var(--primary);
            }
            .suggest-item-checkbox {
              width: 20px;
              height: 20px;
              border-radius: 4px;
              accent-color: var(--primary);
              cursor: pointer;
              margin-top: 2px;
            }
          `}</style>
          
          <div className="suggest-modal-card">
            <div className="suggest-modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span className="material-symbols-outlined" style={{ color: 'var(--primary)', fontSize: 24 }}>auto_awesome</span>
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700, margin: 0 }}>AI Vocabulary Suggestions</h3>
              </div>
              <button
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--outline)' }}
                onClick={() => setIsOpen(false)}
                disabled={saveLoading}
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <div className="suggest-modal-body">
              {loading ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 0', gap: '16px' }}>
                  <span className="spinner" style={{ width: 36, height: 36 }} />
                  <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.95rem', fontWeight: 600 }}>
                    Gemini is analyzing the text for advanced expressions...
                  </p>
                </div>
              ) : error ? (
                <div className="error-msg" style={{ margin: 0 }}>{error}</div>
              ) : saveSuccess ? (
                <div className="success-msg" style={{ margin: 0, textAlign: 'center', padding: '24px' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: 40, display: 'block', marginBottom: '8px' }}>check_circle</span>
                  {saveSuccess}
                </div>
              ) : (
                <div>
                  <p style={{ fontSize: '0.92rem', color: 'var(--color-text-secondary)', marginBottom: '20px', lineHeight: 1.5 }}>
                    Here are some advanced IELTS vocabulary items extracted from your test context. Select the ones you want to save to your card deck:
                  </p>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', background: 'var(--surface-container-low)', padding: '10px 16px', borderRadius: 'var(--radius-md)' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '0.9rem', fontWeight: 600, cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={isAllSelected}
                        onChange={(e) => handleSelectAll(e.target.checked)}
                        className="suggest-item-checkbox"
                      />
                      Select All
                    </label>
                    <span style={{ fontSize: '0.8rem', color: 'var(--outline)', fontWeight: 600 }}>
                      {Object.values(selectedWords).filter(Boolean).length} / {suggestions.length} selected
                    </span>
                  </div>

                  {suggestions.map((item) => (
                    <div
                      key={item.word}
                      className="suggest-item"
                      onClick={() => handleToggleSelect(item.word)}
                    >
                      <input
                        type="checkbox"
                        checked={!!selectedWords[item.word]}
                        onChange={() => {}} // Handled by div click
                        className="suggest-item-checkbox"
                        onClick={(e) => e.stopPropagation()}
                      />

                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                          <span style={{ fontWeight: 800, fontSize: '1.05rem', color: 'var(--primary)' }}>{item.word}</span>
                          <span style={{ fontSize: '0.85rem', fontStyle: 'italic', color: 'var(--color-text-muted)' }}>{item.phonetic}</span>
                          <span style={{
                            padding: '1px 8px',
                            borderRadius: '99px',
                            fontSize: '0.62rem',
                            fontWeight: 700,
                            ...getCefrBadgeStyle(item.cefrLevel)
                          }}>
                            {item.cefrLevel || 'B2'}
                          </span>
                          <span className="badge-reading" style={{ textTransform: 'uppercase', fontSize: '0.65rem' }}>
                            {item.partOfSpeech || 'noun'}
                          </span>
                        </div>

                        <p style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--on-surface)', marginBottom: '8px' }}>
                          {item.meaningVi}
                        </p>

                        {item.collocation && (
                          <div style={{ fontSize: '0.82rem', marginBottom: '4px' }}>
                            <span style={{ fontWeight: 700, color: 'var(--outline)' }}>Collocation: </span>
                            <span style={{ color: 'var(--color-text-secondary)', fontWeight: 500 }}>{item.collocation}</span>
                          </div>
                        )}

                        {item.example && (
                          <div style={{ fontSize: '0.82rem', fontStyle: 'italic', color: 'var(--color-text-secondary)' }}>
                            "{item.example}"
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="suggest-modal-footer">
              <button
                type="button"
                className="btn btn-outline"
                onClick={() => setIsOpen(false)}
                disabled={saveLoading}
              >
                Close
              </button>
              {!saveSuccess && suggestions.length > 0 && (
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleSave}
                  disabled={saveLoading}
                >
                  {saveLoading && <span className="spinner" style={{ width: 16, height: 16 }} />}
                  Add to Card Deck
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
