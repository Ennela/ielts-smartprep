import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import readingApi from '../api/readingApi';
import listeningApi from '../api/listeningApi';
import writingApi from '../api/writingApi';
import mockTestApi from '../api/mockTestApi';

export default function HistoryPage() {
  const navigate = useNavigate();
  const [historyItems, setHistoryItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters state
  const [skillFilter, setSkillFilter] = useState('All Skills');
  const [timeFilter, setTimeFilter] = useState('All Time');
  const [searchTerm, setSearchTerm] = useState('');
  
  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 8;

  useEffect(() => {
    const fetchAllHistory = async () => {
      setLoading(true);
      setError('');
      try {
        const [readingRes, listeningRes, writingRes, mockRes] = await Promise.all([
          readingApi.getHistory(0, 100).catch(err => {
            console.error('Reading history fetch failed:', err);
            return { data: { data: [] } };
          }),
          listeningApi.getHistory().catch(err => {
            console.error('Listening history fetch failed:', err);
            return { data: { data: [] } };
          }),
          writingApi.getHistory(0, 100).catch(err => {
            console.error('Writing history fetch failed:', err);
            return { data: { data: [] } };
          }),
          mockTestApi.getHistory().catch(err => {
            console.error('Mock test history fetch failed:', err);
            return { data: { data: [] } };
          })
        ]);

        const formatEssayType = (type) => {
          if (!type) return 'Writing Essay';
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
            default: return type.charAt(0) + type.slice(1).toLowerCase();
          }
        };

        const readingData = (readingRes.data?.data?.items || readingRes.data?.data || []).map(item => ({
          id: item.historyId || item.quizId,
          date: new Date(item.submittedAt || item.createdAt),
          skill: 'Reading',
          title: item.topic || 'Academic Reading Practice',
          score: item.bandScore ? `Band ${parseFloat(item.bandScore).toFixed(1)}` : (item.correctAnswers !== undefined ? `${item.correctAnswers}/${item.totalQuestions}` : '—'),
          timeSpent: '58 mins',
          actionUrl: item.quizId ? `/reading/result/${item.quizId}` : `/history/${item.historyId}/review`
        }));

        const listeningData = (listeningRes.data?.data?.items || listeningRes.data?.data || []).map(item => ({
          id: item.historyId || item.testId,
          date: new Date(item.submittedAt),
          skill: 'Listening',
          title: item.testMode === 'MOCK_TEST' ? 'Listening Mock Test' : 'Listening Section Practice',
          score: item.score ? `Band ${parseFloat(item.score).toFixed(1)}` : (item.correctAnswers !== undefined ? `${item.correctAnswers}/${item.totalQuestions}` : '—'),
          timeSpent: '30 mins',
          actionUrl: item.historyId ? `/history/${item.historyId}/review` : `/listening/result/${item.testId}`
        }));

        const writingData = (writingRes.data?.data?.items || writingRes.data?.data || []).map(item => {
          const isTask1 = item.essayType?.includes('TASK1') || ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'].includes(item.essayType);
          return {
            id: item.submissionId,
            date: new Date(item.submittedAt),
            skill: 'Writing',
            title: `Task ${isTask1 ? '1' : '2'} Essay: ${formatEssayType(item.essayType)}`,
            score: item.overallBand ? `Band ${parseFloat(item.overallBand).toFixed(1)}` : '—',
            timeSpent: '40 mins',
            actionUrl: `/writing/result/${item.submissionId}`
          };
        });

        const mockData = (mockRes.data?.data?.items || mockRes.data?.data || []).map(item => ({
          id: item.submissionId,
          date: new Date(item.submittedAt),
          skill: 'Mock Test',
          title: item.title || 'Full Mock Test',
          score: item.status === 'GRADING' ? 'Grading...' : (item.overallBand ? `Band ${parseFloat(item.overallBand).toFixed(1)}` : '—'),
          timeSpent: '2h 45m',
          actionUrl: `/mock-tests/result/${item.submissionId}`
        }));

        const combined = [...readingData, ...listeningData, ...writingData, ...mockData];
        // Sort descending by date
        combined.sort((a, b) => b.date.getTime() - a.date.getTime());

        setHistoryItems(combined);
      } catch (err) {
        setError('Failed to fetch test history records.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    fetchAllHistory();
  }, []);

  // Filter & Search processing
  const filteredItems = historyItems.filter(item => {
    // Skill Filter
    if (skillFilter !== 'All Skills') {
      if (skillFilter === 'Reading' && item.skill !== 'Reading') return false;
      if (skillFilter === 'Writing' && item.skill !== 'Writing') return false;
      if (skillFilter === 'Listening' && item.skill !== 'Listening') return false;
      if (skillFilter === 'Mock Tests' && item.skill !== 'Mock Test') return false;
    }

    // Time Filter
    if (timeFilter !== 'All Time') {
      const now = new Date();
      const diffMs = now.getTime() - item.date.getTime();
      const diffDays = diffMs / (1000 * 60 * 60 * 24);
      if (timeFilter === 'Last 30 Days' && diffDays > 30) return false;
      if (timeFilter === 'Last 3 Months' && diffDays > 90) return false;
    }

    // Search Term Filter
    if (searchTerm.trim() !== '') {
      const search = searchTerm.toLowerCase();
      const titleMatch = item.title.toLowerCase().includes(search);
      const skillMatch = item.skill.toLowerCase().includes(search);
      if (!titleMatch && !skillMatch) return false;
    }

    return true;
  });

  // Pagination processing
  const totalItems = filteredItems.length;
  const totalPages = Math.ceil(totalItems / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedItems = filteredItems.slice(startIndex, startIndex + itemsPerPage);

  const handlePageChange = (page) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
    }
  };

  const getSkillIcon = (skill) => {
    switch (skill) {
      case 'Reading':
        return {
          name: 'menu_book',
          wrapperClass: 'bg-secondary-container/20 text-secondary'
        };
      case 'Writing':
        return {
          name: 'edit_note',
          wrapperClass: 'bg-tertiary-container/20 text-tertiary'
        };
      case 'Listening':
        return {
          name: 'headset',
          wrapperClass: 'bg-[#e8f5e9] text-[#2e7d32]'
        };
      case 'Mock Test':
      default:
        return {
          name: 'assignment',
          wrapperClass: 'bg-primary-container/20 text-primary-container'
        };
    }
  };

  const formatDate = (date) => {
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  };

  return (
    <div className="flex flex-col space-y-xl py-6">
      {/* Header & Filters */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-md">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-background">Test History</h1>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">Review your past performance and track progress.</p>
        </div>
        
        <div className="flex flex-wrap gap-sm w-full md:w-auto">
          {/* Search bar */}
          <div className="relative flex-1 md:flex-initial min-w-[200px]">
            <input
              type="text"
              placeholder="Search assessments..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value);
                setCurrentPage(1);
              }}
              className="w-full bg-surface-container-lowest border border-outline-variant text-on-surface font-body-md text-body-md rounded-lg pl-9 pr-4 py-2 focus:outline-none focus:ring-2 focus:ring-primary-container focus:border-transparent"
            />
            <span className="material-symbols-outlined absolute left-2 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
          </div>

          <div className="relative">
            <select
              value={skillFilter}
              onChange={(e) => {
                setSkillFilter(e.target.value);
                setCurrentPage(1);
              }}
              className="appearance-none bg-surface-container-lowest border border-outline-variant text-on-surface font-body-md text-body-md rounded-lg pl-md pr-[36px] py-2 focus:outline-none focus:ring-2 focus:ring-primary-container focus:border-transparent"
            >
              <option value="All Skills">All Skills</option>
              <option value="Reading">Reading</option>
              <option value="Writing">Writing</option>
              <option value="Listening">Listening</option>
              <option value="Mock Tests">Mock Tests</option>
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-outline">expand_more</span>
          </div>

          <div className="relative">
            <select
              value={timeFilter}
              onChange={(e) => {
                setTimeFilter(e.target.value);
                setCurrentPage(1);
              }}
              className="appearance-none bg-surface-container-lowest border border-outline-variant text-on-surface font-body-md text-body-md rounded-lg pl-md pr-[36px] py-2 focus:outline-none focus:ring-2 focus:ring-primary-container focus:border-transparent"
            >
              <option value="All Time">All Time</option>
              <option value="Last 30 Days">Last 30 Days</option>
              <option value="Last 3 Months">Last 3 Months</option>
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-outline">expand_more</span>
          </div>
        </div>
      </div>

      {/* Loading state */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 bg-surface-container-lowest rounded-2xl border border-outline-variant">
          <div className="spinner mb-4"></div>
          <p className="text-on-surface-variant font-body-md">Loading your test history...</p>
        </div>
      ) : error ? (
        <div className="text-center py-20 bg-surface-container-lowest rounded-2xl border border-outline-variant">
          <span className="material-symbols-outlined text-error text-[48px] mb-2">error</span>
          <h3 className="font-title-lg text-title-lg text-on-surface mb-2">{error}</h3>
          <button onClick={() => window.location.reload()} className="btn btn-primary mt-2">Retry</button>
        </div>
      ) : historyItems.length === 0 ? (
        <div className="bg-surface-container-lowest rounded-2xl shadow-[0_4px_20px_rgba(0,0,0,0.05)] border border-outline-variant p-xl flex flex-col items-center justify-center text-center py-20">
          <div className="w-24 h-24 mb-md opacity-50 flex items-center justify-center rounded-full bg-surface-container">
            <span className="material-symbols-outlined text-[48px] text-outline">history</span>
          </div>
          <h3 className="font-title-lg text-title-lg text-on-surface mb-2">No attempts recorded yet</h3>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-lg">Start practicing now to see your scores and detailed evaluations recorded here.</p>
          <button onClick={() => navigate('/mock-tests')} className="btn btn-primary">Go to Mock Tests</button>
        </div>
      ) : filteredItems.length === 0 ? (
        <div className="bg-surface-container-lowest rounded-2xl shadow-[0_4px_20px_rgba(0,0,0,0.05)] border border-outline-variant p-xl flex flex-col items-center justify-center text-center py-20">
          <div className="w-24 h-24 mb-md opacity-50 flex items-center justify-center rounded-full bg-surface-container">
            <span className="material-symbols-outlined text-[48px] text-outline">filter_list_off</span>
          </div>
          <h3 className="font-title-lg text-title-lg text-on-surface mb-2">No matching history found</h3>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-lg">No history items matched your search or filters. Try adjusting your selections.</p>
          <button 
            onClick={() => {
              setSkillFilter('All Skills');
              setTimeFilter('All Time');
              setSearchTerm('');
            }}
            className="btn btn-outline"
          >
            Clear Filters
          </button>
        </div>
      ) : (
        /* History Table Wrapper */
        <div className="bg-surface-container-lowest rounded-2xl shadow-[0_4px_20px_rgba(0,0,0,0.05)] overflow-hidden border border-outline-variant">
          <div className="overflow-x-auto">
            <div className="min-w-[768px]">
              {/* Table Header */}
              <div className="grid grid-cols-12 gap-sm px-lg py-md border-b border-outline-variant bg-surface-container/30">
                <div className="col-span-2 font-label-md text-label-md text-on-surface-variant">Date</div>
                <div className="col-span-4 font-label-md text-label-md text-on-surface-variant">Assessment</div>
                <div className="col-span-2 font-label-md text-label-md text-on-surface-variant">Score / Band</div>
                <div className="col-span-2 font-label-md text-label-md text-on-surface-variant">Time Spent</div>
                <div className="col-span-2 text-right font-label-md text-label-md text-on-surface-variant">Action</div>
              </div>

              {/* List Items */}
              <div className="divide-y divide-outline-variant">
                {paginatedItems.map(item => {
                  const icon = getSkillIcon(item.skill);
                  return (
                    <div key={`${item.skill}-${item.id}`} className="grid grid-cols-12 gap-sm px-lg py-md items-center hover:bg-surface-container/10 transition-colors">
                      <div className="col-span-2 font-body-md text-body-md text-on-surface">
                        {formatDate(item.date)}
                      </div>
                      
                      <div className="col-span-4 flex items-center gap-sm">
                        <div className={`w-8 h-8 rounded-full ${icon.wrapperClass} flex items-center justify-center`}>
                          <span className="material-symbols-outlined text-[18px]">{icon.name}</span>
                        </div>
                        <div>
                          <div className="font-bold text-[14px] leading-tight text-on-surface line-clamp-1">
                            {item.title}
                          </div>
                          <div className="font-label-md text-label-md text-outline">
                            {item.skill}
                          </div>
                        </div>
                      </div>

                      <div className="col-span-2">
                        <span className={`inline-flex items-center px-2 py-1 rounded-md font-bold text-[14px] ${
                          item.skill === 'Mock Test' && item.score !== 'Grading...'
                            ? 'bg-primary-container text-on-primary-container' 
                            : 'bg-surface-container-highest text-on-surface'
                        }`}>
                          {item.score}
                        </span>
                      </div>

                      <div className="col-span-2 font-body-md text-body-md text-on-surface-variant">
                        {item.timeSpent}
                      </div>

                      <div className="col-span-2 flex justify-end">
                        <button
                          onClick={() => navigate(item.actionUrl)}
                          className="font-label-md text-label-md text-primary-container border border-outline-variant hover:bg-surface-container hover:border-outline px-md py-2 rounded-lg transition-colors"
                        >
                          View Review
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Pagination Footer */}
          <div className="px-lg py-md border-t border-outline-variant flex items-center justify-between bg-surface-container-lowest">
            <span className="font-body-md text-body-md text-on-surface-variant">
              Showing {startIndex + 1} to {Math.min(startIndex + itemsPerPage, totalItems)} of {totalItems} entries
            </span>
            {totalPages > 1 && (
              <div className="flex gap-1">
                <button
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage === 1}
                  className="w-8 h-8 flex items-center justify-center rounded border border-outline-variant text-on-surface-variant hover:bg-surface-container disabled:opacity-30"
                >
                  <span className="material-symbols-outlined text-[18px]">chevron_left</span>
                </button>
                
                {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                  <button
                    key={page}
                    onClick={() => handlePageChange(page)}
                    className={`w-8 h-8 flex items-center justify-center rounded font-label-md text-label-md ${
                      currentPage === page 
                        ? 'bg-primary-container text-on-primary-container font-bold' 
                        : 'border border-outline-variant text-on-surface hover:bg-surface-container'
                    }`}
                  >
                    {page}
                  </button>
                ))}

                <button
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage === totalPages}
                  className="w-8 h-8 flex items-center justify-center rounded border border-outline-variant text-on-surface-variant hover:bg-surface-container disabled:opacity-30"
                >
                  <span className="material-symbols-outlined text-[18px]">chevron_right</span>
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
