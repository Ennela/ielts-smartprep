import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import writingApi from '../api/writingApi';

export default function WritingEditorPage() {
    const { promptId } = useParams();
    const navigate = useNavigate();

    const [prompt, setPrompt] = useState(null);
    const [essayText, setEssayText] = useState('');
    const [wordCount, setWordCount] = useState(0);
    const [loading, setLoading] = useState(true);
    const [grading, setGrading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        loadPrompt();
    }, [promptId]);

    const loadPrompt = async () => {
        setLoading(true);
        try {
            const res = await writingApi.getPromptById(promptId);
            setPrompt(res.data.data);
        } catch (err) {
            setError('Failed to load prompt.');
        } finally {
            setLoading(false);
        }
    };

    // Debounced word count
    const countWords = useCallback((text) => {
        if (!text || !text.trim()) return 0;
        return text.trim().split(/\s+/).filter(w => w.length > 0).length;
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => {
            setWordCount(countWords(essayText));
        }, 300);
        return () => clearTimeout(timer);
    }, [essayText, countWords]);

    const handleGrade = async () => {
        if (wordCount < 250) return;
        setGrading(true);
        setError('');
        try {
            const res = await writingApi.gradeEssay(Number(promptId), essayText);
            const submissionId = res.data.data.submissionId;
            navigate(`/writing/result/${submissionId}`);
        } catch (err) {
            setError(err.response?.data?.message || 'Grading failed. Please try again.');
        } finally {
            setGrading(false);
        }
    };

    const formatType = (type) => {
        switch (type) {
            case 'CAUSE_AND_EFFECT': return 'Cause & Effect';
            default: return type ? type.charAt(0) + type.slice(1).toLowerCase() : '';
        }
    };

    if (loading) {
        return (
            <div className="loading-screen">
                <span className="spinner" style={{ width: 24, height: 24 }}></span>
                Loading...
            </div>
        );
    }

    return (
        <div className="writing-editor-page">
            <div className="editor-content">
                <button className="btn-back" onClick={() => navigate('/writing')} id="back-to-prompts">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    Back to Prompts
                </button>

                {/* Prompt Display */}
                <div className="editor-prompt-card card">
                    <div className="editor-prompt-header">
                        <span className={`essay-type-badge badge-${prompt?.essayType?.toLowerCase()}`}>
                            {formatType(prompt?.essayType)}
                        </span>
                        <span className="task-label">Writing Task 2</span>
                    </div>
                    <p className="editor-prompt-text">{prompt?.promptText}</p>
                    <p className="editor-prompt-hint">
                        Write at least 250 words. Give reasons for your answer and include any relevant examples from your own knowledge or experience.
                    </p>
                </div>

                {error && <div className="error-msg">{error}</div>}

                {/* Essay Editor */}
                <div className="editor-textarea-wrapper">
                    <textarea
                        className="editor-textarea"
                        value={essayText}
                        onChange={(e) => setEssayText(e.target.value)}
                        placeholder="Start writing your essay here..."
                        id="essay-editor"
                    />
                </div>

                {/* Status Bar */}
                <div className="editor-status-bar">
                    <div className={`word-count ${wordCount >= 250 ? 'count-ok' : 'count-low'}`}>
                        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M12 20V4M4 12h16"/>
                        </svg>
                        Word Count: <strong>{wordCount}</strong> / 250
                        {wordCount < 250 && (
                            <span className="count-warning"> ({250 - wordCount} more needed)</span>
                        )}
                    </div>

                    <button
                        className="btn btn-primary btn-grade"
                        onClick={handleGrade}
                        disabled={wordCount < 250 || grading}
                        id="grade-essay-btn"
                    >
                        {grading ? (
                            <>
                                <span className="spinner"></span>
                                AI is grading...
                            </>
                        ) : (
                            'Grade My Essay'
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
