import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import writingApi from '../api/writingApi';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 10;

export default function WritingHistoryPage() {
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState('single'); // 'single' or 'full'
    const [singlePage, setSinglePage] = useState(0);
    const [fullPage, setFullPage] = useState(0);

    // Each list is one page of a Spring Page; the whole history is no longer shipped.
    const { data: historyRes, isLoading, isFetching, isPlaceholderData, error } = useQuery({
        queryKey: ['writingHistory', singlePage],
        queryFn: () => writingApi.getHistory(singlePage, PAGE_SIZE),
        placeholderData: keepPreviousData,
    });

    const { data: fullHistoryRes, isLoading: isFullLoading, isFetching: isFullFetching,
            isPlaceholderData: isFullPlaceholder, error: fullError } = useQuery({
        queryKey: ['writingFullHistory', fullPage],
        queryFn: () => writingApi.getFullHistory(fullPage, PAGE_SIZE),
        placeholderData: keepPreviousData,
    });

    const historyPage = historyRes?.data?.data;
    const fullHistoryPage = fullHistoryRes?.data?.data;
    const history = historyPage?.content || [];
    const fullHistory = fullHistoryPage?.content || [];

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
            case 'LETTER': return 'Letter';
            default: return type ? type.charAt(0) + type.slice(1).toLowerCase() : '';
        }
    };

    const getScoreColor = (score) => {
        const s = parseFloat(score);
        if (s >= 7.0) return 'var(--color-success)';
        if (s >= 5.5) return 'var(--color-warning)';
        return 'var(--color-error)';
    };

    const currentLoading = activeTab === 'single' ? isLoading : isFullLoading;
    const currentError = activeTab === 'single' ? error : fullError;
    const currentItems = activeTab === 'single' ? history : fullHistory;
    const currentPage = activeTab === 'single' ? singlePage : fullPage;
    const currentPageInfo = activeTab === 'single' ? historyPage : fullHistoryPage;
    const setCurrentPage = activeTab === 'single' ? setSinglePage : setFullPage;
    const currentFetching = activeTab === 'single' ? isFetching : isFullFetching;
    const currentPlaceholder = activeTab === 'single' ? isPlaceholderData : isFullPlaceholder;

    return (
        <div className="writing-page">
            <div className="writing-content">
                <button className="btn-back" onClick={() => navigate('/writing')} id="back-to-writing">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    Writing Prompts
                </button>

                <h1>Writing History</h1>
                <p className="subtitle">Review your previous essays and scores</p>

                {/* Tabs */}
                <div className="task-tabs" style={{ marginBottom: '20px' }}>
                    <button
                        className={`task-tab-btn ${activeTab === 'single' ? 'active' : ''}`}
                        onClick={() => setActiveTab('single')}
                        id="history-tab-single"
                    >
                        Single Task Practice
                    </button>
                    <button
                        className={`task-tab-btn ${activeTab === 'full' ? 'active' : ''}`}
                        onClick={() => setActiveTab('full')}
                        id="history-tab-full"
                    >
                        Full Mock Tests
                    </button>
                </div>

                {currentError && <div className="error-msg">Failed to load history.</div>}

                {currentLoading ? (
                    <div className="loading-screen" style={{ height: '300px' }}>
                        <span className="spinner" style={{ width: 24, height: 24 }}></span>
                        Loading history...
                    </div>
                ) : currentItems.length === 0 ? (
                    <div className="empty-state card">
                        <p>No essays written yet. Start practicing!</p>
                        <button className="btn btn-primary" onClick={() => navigate('/writing')} style={{ marginTop: 16 }}>
                            Start Writing
                        </button>
                    </div>
                ) : activeTab === 'single' ? (
                    <div className="history-list">
                        {currentItems.map((item) => (
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
                                    {item.timeSpentSeconds != null && (
                                        <span style={{ fontSize: '0.8rem', color: 'var(--on-surface-variant)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                            {Math.floor(item.timeSpentSeconds / 60)}:{(item.timeSpentSeconds % 60).toString().padStart(2, '0')}
                                            {item.autoSubmitted && (
                                                <span style={{ fontSize: '0.65rem', padding: '1px 5px', borderRadius: 'var(--radius-full)', background: 'rgba(186,26,26,0.08)', color: 'var(--error)', fontWeight: 600 }}>Auto</span>
                                            )}
                                        </span>
                                    )}
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
                ) : (
                    <div className="history-list">
                        {currentItems.map((item) => (
                            <div
                                key={item.id}
                                className="card history-card card-clickable"
                                onClick={() => navigate(`/writing/full-result`, { state: { result: item } })}
                                id={`full-history-${item.id}`}
                            >
                                <div className="history-card-top">
                                    <span className="essay-type-badge" style={{ background: 'var(--primary-container)', color: 'var(--primary)' }}>
                                        Full Mock Test
                                    </span>
                                    <span className="history-date">{formatDate(item.submittedAt)}</span>
                                </div>
                                <p className="history-prompt-preview" style={{ fontSize: '0.85rem', lineHeight: 1.5 }}>
                                    Task 1: {formatType(item.task1Result?.essayType)} (Band {item.task1Result?.overallBand})<br/>
                                    Task 2: {formatType(item.task2Result?.essayType)} (Band {item.task2Result?.overallBand})
                                </p>
                                <div className="history-card-bottom">
                                    <span className="history-words" style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                                        <span>Two Tasks</span>
                                        {item.timeSpentSeconds != null && (
                                            <span style={{ fontSize: '0.75rem', color: 'var(--on-surface-variant)' }}>
                                                {Math.floor(item.timeSpentSeconds / 60)}:{(item.timeSpentSeconds % 60).toString().padStart(2, '0')}
                                                {item.timeSpentTask1 != null && (
                                                    <> · T1: {Math.floor(item.timeSpentTask1 / 60)}:{(item.timeSpentTask1 % 60).toString().padStart(2, '0')}</>
                                                )}
                                                {item.timeSpentTask2 != null && (
                                                    <> · T2: {Math.floor(item.timeSpentTask2 / 60)}:{(item.timeSpentTask2 % 60).toString().padStart(2, '0')}</>
                                                )}
                                                {item.autoSubmitted && (
                                                    <span style={{ fontSize: '0.65rem', padding: '1px 5px', borderRadius: 'var(--radius-full)', background: 'rgba(186,26,26,0.08)', color: 'var(--error)', fontWeight: 600, marginLeft: 4 }}>Auto</span>
                                                )}
                                            </span>
                                        )}
                                    </span>
                                    <span
                                        className="history-score"
                                        style={{ color: getScoreColor(item.overallWritingBand), fontWeight: 700 }}
                                    >
                                        Band {item.overallWritingBand}
                                    </span>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {!currentLoading && !currentError && currentItems.length > 0 && (
                    <Pagination
                        page={currentPage}
                        totalPages={currentPageInfo?.totalPages || 0}
                        totalElements={currentPageInfo?.totalElements || 0}
                        size={PAGE_SIZE}
                        onPageChange={setCurrentPage}
                        isFetching={currentFetching}
                        isPlaceholderData={currentPlaceholder}
                    />
                )}
            </div>
        </div>
    );
}
