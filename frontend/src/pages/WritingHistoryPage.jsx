import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import writingApi from '../api/writingApi';

export default function WritingHistoryPage() {
    const navigate = useNavigate();

    const { data: historyRes, isLoading, error } = useQuery({
        queryKey: ['writingHistory'],
        queryFn: () => writingApi.getHistory(),
    });

    const history = historyRes?.data?.data?.items || historyRes?.data?.data || [];

    const formatDate = (dateStr) => {
        return new Date(dateStr).toLocaleDateString('en-US', {
            year: 'numeric', month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit',
        });
    };

    const formatType = (type) => {
        switch (type) {
            case 'CAUSE_AND_EFFECT': return 'Cause & Effect';
            case 'PROBLEM_AND_SOLUTION': return 'Problem & Solution';
            case 'ADVANTAGES_DISADVANTAGES': return 'Advantages & Disadvantages';
            case 'TWO_PART_QUESTION': return 'Two-Part Question';
            case 'LINE_GRAPH': return 'Line Graph';
            case 'BAR_CHART': return 'Bar Chart';
            case 'PIE_CHART': return 'Pie Chart';
            case 'TABLE': return 'Table';
            case 'MAP': return 'Map';
            case 'DIAGRAM': return 'Diagram';
            default: return type ? type.charAt(0) + type.slice(1).toLowerCase() : '';
        }
    };

    const getScoreColor = (score) => {
        const s = parseFloat(score);
        if (s >= 7.0) return 'var(--color-success)';
        if (s >= 5.5) return 'var(--color-warning)';
        return 'var(--color-error)';
    };

    return (
        <div className="writing-page">
            <div className="writing-content">
                <button className="btn-back" onClick={() => navigate('/writing')} id="back-to-writing">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    Writing Prompts
                </button>

                <h1>Writing History</h1>
                <p className="subtitle">Review your previous essays and scores</p>

                {error && <div className="error-msg">Failed to load history.</div>}

                {isLoading ? (
                    <div className="loading-screen" style={{ height: '300px' }}>
                        <span className="spinner" style={{ width: 24, height: 24 }}></span>
                        Loading history...
                    </div>
                ) : history.length === 0 ? (
                    <div className="empty-state card">
                        <p>No essays written yet. Start practicing!</p>
                        <button className="btn btn-primary" onClick={() => navigate('/writing')} style={{ marginTop: 16 }}>
                            Start Writing
                        </button>
                    </div>
                ) : (
                    <div className="history-list">
                        {history.map((item) => (
                            <div
                                key={item.submissionId}
                                className="card history-card card-clickable"
                                onClick={() => navigate(`/writing/result/${item.submissionId}`)}
                                id={`history-${item.submissionId}`}
                            >
                                <div className="history-card-top">
                                    <span className={`essay-type-badge badge-${item.essayType?.toLowerCase()}`}>
                                        {formatType(item.essayType)}
                                    </span>
                                    <span className="history-date">{formatDate(item.submittedAt)}</span>
                                </div>
                                <p className="history-prompt-preview">{item.promptTextPreview}</p>
                                <div className="history-card-bottom">
                                    <span className="history-words">{item.wordCount} words</span>
                                    <span
                                        className="history-score"
                                        style={{ color: getScoreColor(item.overallBand) }}
                                    >
                                        Band {item.overallBand}
                                    </span>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
