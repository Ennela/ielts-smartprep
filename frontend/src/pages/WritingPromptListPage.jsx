import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import writingApi from '../api/writingApi';

const TASK1_TYPES = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'];
const TASK2_TYPES = ['OPINION', 'DISCUSSION', 'CAUSE_AND_EFFECT', 'PROBLEM_AND_SOLUTION', 'ADVANTAGES_DISADVANTAGES', 'TWO_PART_QUESTION'];

const TASK2_FILTERS = [
    { value: '', label: 'All Types' },
    { value: 'OPINION', label: 'Opinion' },
    { value: 'DISCUSSION', label: 'Discussion' },
    { value: 'CAUSE_AND_EFFECT', label: 'Cause & Effect' },
    { value: 'PROBLEM_AND_SOLUTION', label: 'Problem & Solution' },
    { value: 'ADVANTAGES_DISADVANTAGES', label: 'Advantages & Disadvantages' },
    { value: 'TWO_PART_QUESTION', label: 'Two-Part Question' },
];

const TASK1_FILTERS = [
    { value: '', label: 'All Types' },
    { value: 'LINE_GRAPH', label: 'Line Graph' },
    { value: 'BAR_CHART', label: 'Bar Chart' },
    { value: 'PIE_CHART', label: 'Pie Chart' },
    { value: 'TABLE', label: 'Table' },
    { value: 'MAP', label: 'Map' },
    { value: 'DIAGRAM', label: 'Diagram' },
];

export default function WritingPromptListPage() {
    const navigate = useNavigate();
    const [prompts, setPrompts] = useState([]);
    const [activeTask, setActiveTask] = useState(1); // 1 or 2
    const [filter, setFilter] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [assembling, setAssembling] = useState(false);

    const handleStartFullTest = async () => {
        setAssembling(true);
        setError('');
        try {
            const res = await writingApi.assembleMockTest();
            const prompt1Id = res.data.data[0].promptId;
            const prompt2Id = res.data.data[1].promptId;
            navigate(`/writing/full-exam?task1Id=${prompt1Id}&task2Id=${prompt2Id}`);
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to assemble Writing test');
        } finally {
            setAssembling(false);
        }
    };

    useEffect(() => {
        loadPrompts();
    }, []);

    useEffect(() => {
        setFilter(''); // Reset filter when switching tabs
    }, [activeTask]);

    const loadPrompts = async () => {
        setLoading(true);
        setError('');
        try {
            const res = await writingApi.getPrompts();
            setPrompts(res.data.data || []);
        } catch (err) {
            setError('Failed to load prompts.');
        } finally {
            setLoading(false);
        }
    };

    // Filter prompts by active task and sub-filter
    const taskTypes = activeTask === 1 ? TASK1_TYPES : TASK2_TYPES;
    const filteredPrompts = prompts.filter(p => {
        if (!taskTypes.includes(p.essayType)) return false;
        if (filter && p.essayType !== filter) return false;
        return true;
    });

    const filters = activeTask === 1 ? TASK1_FILTERS : TASK2_FILTERS;

    const getTypeBadgeClass = (type) => {
        switch (type) {
            case 'OPINION': return 'badge-opinion';
            case 'DISCUSSION': return 'badge-discussion';
            case 'CAUSE_AND_EFFECT': return 'badge-cause';
            case 'PROBLEM_AND_SOLUTION': return 'badge-problem';
            case 'ADVANTAGES_DISADVANTAGES': return 'badge-advantages';
            case 'TWO_PART_QUESTION': return 'badge-twopart';
            case 'LINE_GRAPH': return 'badge-line-graph';
            case 'BAR_CHART': return 'badge-bar-chart';
            case 'PIE_CHART': return 'badge-pie-chart';
            case 'TABLE': return 'badge-table';
            case 'MAP': return 'badge-map';
            case 'DIAGRAM': return 'badge-diagram';
            default: return '';
        }
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

    return (
        <div className="writing-page">
            <div className="writing-content">
                <button className="btn-back" onClick={() => navigate('/dashboard')} id="back-to-dashboard">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    Home
                </button>

                <div className="writing-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                    <div>
                        <h1>Writing Practice</h1>
                        <p className="subtitle">
                            {activeTask === 1
                                ? 'Select a Task 1 prompt and write a report describing the chart'
                                : 'Select a Task 2 prompt and start writing your essay'}
                        </p>
                    </div>
                    <div style={{ display: 'flex', gap: '0.75rem' }}>
                        <button
                            className="btn btn-primary"
                            onClick={handleStartFullTest}
                            disabled={assembling}
                            id="start-writing-full-test"
                        >
                            {assembling ? 'Assembling...' : 'Start Mock Test (Both Tasks)'}
                        </button>
                        <button className="btn btn-outline" onClick={() => navigate('/writing/history')} id="view-writing-history">
                            View History
                        </button>
                    </div>
                </div>

                {/* Task Tabs */}
                <div className="task-tabs">
                    <button
                        className={`task-tab-btn ${activeTask === 1 ? 'active' : ''}`}
                        onClick={() => setActiveTask(1)}
                        id="task-tab-1"
                    >
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/>
                        </svg>
                        Task 1
                        <span className="task-tab-desc">Chart Description</span>
                    </button>
                    <button
                        className={`task-tab-btn ${activeTask === 2 ? 'active' : ''}`}
                        onClick={() => setActiveTask(2)}
                        id="task-tab-2"
                    >
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/>
                        </svg>
                        Task 2
                        <span className="task-tab-desc">Essay Writing</span>
                    </button>
                </div>

                {/* Sub-filter */}
                <div className="writing-filter">
                    {filters.map((t) => (
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
                        {filteredPrompts.length === 0 ? (
                            <p className="empty-msg">No prompts found.</p>
                        ) : (
                            filteredPrompts.map((p) => (
                                <div
                                    key={p.promptId}
                                    className={`card prompt-card card-clickable ${p.imageUrl ? 'prompt-card-with-image' : ''}`}
                                    onClick={() => navigate(`/writing/editor/${p.promptId}`)}
                                    id={`prompt-${p.promptId}`}
                                >
                                    {p.imageUrl && (
                                        <div className="prompt-card-thumbnail">
                                            <img src={p.imageUrl} alt="Chart" loading="lazy" />
                                        </div>
                                    )}
                                    <div className="prompt-card-body">
                                        <div className="prompt-card-header">
                                            <span className={`essay-type-badge ${getTypeBadgeClass(p.essayType)}`}>
                                                {formatType(p.essayType)}
                                            </span>
                                        </div>
                                        <p className="prompt-text">{p.promptText}</p>
                                        <span className="card-action">Start writing &rarr;</span>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
