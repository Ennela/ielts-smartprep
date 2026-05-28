import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import writingApi from '../api/writingApi';

const ESSAY_TYPES = [
    { value: '', label: 'All Types' },
    { value: 'OPINION', label: 'Opinion' },
    { value: 'DISCUSSION', label: 'Discussion' },
    { value: 'CAUSE_AND_EFFECT', label: 'Cause & Effect' },
];

export default function WritingPromptListPage() {
    const navigate = useNavigate();
    const [prompts, setPrompts] = useState([]);
    const [filter, setFilter] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        loadPrompts();
    }, [filter]);

    const loadPrompts = async () => {
        setLoading(true);
        setError('');
        try {
            const res = await writingApi.getPrompts(filter || undefined);
            setPrompts(res.data.data || []);
        } catch (err) {
            setError('Failed to load writing prompts.');
        } finally {
            setLoading(false);
        }
    };

    const getTypeBadgeClass = (type) => {
        switch (type) {
            case 'OPINION': return 'badge-opinion';
            case 'DISCUSSION': return 'badge-discussion';
            case 'CAUSE_AND_EFFECT': return 'badge-cause';
            default: return '';
        }
    };

    const formatType = (type) => {
        switch (type) {
            case 'CAUSE_AND_EFFECT': return 'Cause & Effect';
            default: return type.charAt(0) + type.slice(1).toLowerCase();
        }
    };

    return (
        <div className="writing-page">
            <div className="writing-content">
                <button className="btn-back" onClick={() => navigate('/dashboard')} id="back-to-dashboard">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    Dashboard
                </button>

                <div className="writing-header">
                    <div>
                        <h1>Writing Practice</h1>
                        <p className="subtitle">Choose a Task 2 prompt and start writing</p>
                    </div>
                    <button className="btn btn-outline" onClick={() => navigate('/writing/history')} id="view-writing-history">
                        View History
                    </button>
                </div>

                {/* Filter */}
                <div className="writing-filter">
                    {ESSAY_TYPES.map((t) => (
                        <button
                            key={t.value}
                            className={`filter-btn ${filter === t.value ? 'active' : ''}`}
                            onClick={() => setFilter(t.value)}
                            id={`filter-${t.value || 'all'}`}
                        >
                            {t.label}
                        </button>
                    ))}
                </div>

                {error && <div className="error-msg">{error}</div>}

                {loading ? (
                    <div className="loading-screen" style={{ height: '300px' }}>
                        <span className="spinner" style={{ width: 24, height: 24 }}></span>
                        Loading prompts...
                    </div>
                ) : (
                    <div className="prompt-list">
                        {prompts.length === 0 ? (
                            <p className="empty-msg">No prompts found.</p>
                        ) : (
                            prompts.map((p) => (
                                <div
                                    key={p.promptId}
                                    className="card prompt-card card-clickable"
                                    onClick={() => navigate(`/writing/editor/${p.promptId}`)}
                                    id={`prompt-${p.promptId}`}
                                >
                                    <div className="prompt-card-header">
                                        <span className={`essay-type-badge ${getTypeBadgeClass(p.essayType)}`}>
                                            {formatType(p.essayType)}
                                        </span>
                                    </div>
                                    <p className="prompt-text">{p.promptText}</p>
                                    <span className="card-action">Start Writing &rarr;</span>
                                </div>
                            ))
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
