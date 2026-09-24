import { useState, useEffect } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import historyApi from '../api/historyApi';
import { TASK1_TYPES, formatEssayType } from '../constants/examTypes';

const PAGE_SIZE = 8;
const SKILL_PARAM = { 'Reading': 'READING', 'Writing': 'WRITING', 'Listening': 'LISTENING', 'Mock Tests': 'MOCK_TEST' };
const TIME_DAYS = { 'Last 30 Days': 30, 'Last 3 Months': 90 };
const SKILL_LABEL = { READING: 'Reading', LISTENING: 'Listening', WRITING: 'Writing', MOCK_TEST: 'Mock Test' };
// Time spent as recorded for the sitting; older sittings recorded none.
const formatTimeSpent = (seconds) => {
  if (seconds === null || seconds === undefined) return '—';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min${minutes === 1 ? '' : 's'}`;
  return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}m`;
};

// The server keeps each skill's own label and id; the row text and review link are
// built here, the same way the four per-skill history pages do it. A listening test with
// a matched score_history row opens the answer review, like the Listening history page.
const toRow = (item) => {
  const band = item.score === null || item.score === undefined ? '—' : `Band ${parseFloat(item.score).toFixed(1)}`;
  const timeSpent = formatTimeSpent(item.timeSpentSeconds);
  switch (item.skill) {
    case 'READING':
      return { title: item.title || 'Academic Reading Practice', score: band, timeSpent, actionUrl: `/reading/result/${item.refId}` };
    case 'LISTENING':
      return {
        title: item.title === 'MOCK_TEST' ? 'Listening Mock Test' : 'Listening Section Practice', score: band, timeSpent,
        actionUrl: item.historyId ? `/history/${item.historyId}/review` : `/listening/result/${item.refId}`,
      };
    case 'WRITING':
      return { title: `Task ${item.title?.includes('TASK1') || TASK1_TYPES.includes(item.title) ? '1' : '2'} Essay: ${(formatEssayType(item.title) || 'Writing Essay')}`, score: band, timeSpent, actionUrl: `/writing/result/${item.refId}` };
    default:
      return { title: item.title || 'Full Mock Test', score: item.status === 'GRADING' ? 'Grading...' : band, timeSpent, actionUrl: `/mock-tests/result/${item.refId}` };
  }
};

// The API compares against submitted_at as a local date-time, so the cut-off is sent in
// local time too, without a zone suffix.
const localIso = (date) => {
  const p = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`;
};

// Page numbers to show around the current one; a long history is not a row of fifty buttons.
const pageWindow = (current, total) => {
  const span = 2;
  const from = Math.max(1, Math.min(current - span, total - span * 2));
  const to = Math.min(total, from + span * 2);
  const pages = [];
  for (let i = from; i <= to; i++) pages.push(i);
  return pages;
};

export default function HistoryPage() {
  const navigate = useNavigate();

  // Filters state -- applied by the server; changing one goes back to the first page.
  const [skillFilter, setSkillFilter] = useState('All Skills');
  const [timeFilter, setTimeFilter] = useState('All Time');
  const [searchTerm, setSearchTerm] = useState('');
  const [search, setSearch] = useState('');

  // Pagination (1-based, as the buttons below count)
  const [currentPage, setCurrentPage] = useState(1);

  // Typing in the search box does not fire a request per keystroke.
  useEffect(() => {
    const handle = setTimeout(() => setSearch(searchTerm.trim()), 300);
    return () => clearTimeout(handle);
  }, [searchTerm]);

  // react-query keeps the previous page on screen while the next one loads and
  // drops the response of a filter the user has already moved on from, which the
  // hand-rolled effect needed a cancelled flag for.
  const feedParams = {
    page: currentPage - 1,
    size: PAGE_SIZE,
    ...(SKILL_PARAM[skillFilter] ? { skill: SKILL_PARAM[skillFilter] } : {}),
    ...(TIME_DAYS[timeFilter]
      ? { from: localIso(new Date(Date.now() - TIME_DAYS[timeFilter] * 24 * 60 * 60 * 1000)) }
      : {}),
    ...(search ? { q: search } : {}),
  };

  const feedQuery = useQuery({
    queryKey: ['history', 'feed', { skillFilter, timeFilter, search, page: currentPage }],
    queryFn: () => historyApi.getFeed(feedParams),
    placeholderData: keepPreviousData,
    select: (res) => res.data?.data,
  });

  const loading = feedQuery.isLoading;
  const error = feedQuery.isError ? 'Failed to fetch test history records.' : '';
  const historyItems = (feedQuery.data?.content || []).map(item => ({
    id: `${item.skill}-${item.refId}`,
    date: new Date(item.submittedAt),
    skill: SKILL_LABEL[item.skill] || item.skill,
    ...toRow(item),
  }));
  const pageInfo = {
    totalPages: feedQuery.data?.totalPages || 0,
    totalElements: feedQuery.data?.totalElements || 0,
  };

  const filtersActive = skillFilter !== 'All Skills' || timeFilter !== 'All Time' || search !== '';
  const totalItems = pageInfo.totalElements;
  const totalPages = pageInfo.totalPages;
  const startIndex = (currentPage - 1) * PAGE_SIZE;
  const paginatedItems = historyItems;

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
      ) : totalItems === 0 && !filtersActive ? (
        <div className="bg-surface-container-lowest rounded-2xl shadow-[0_4px_20px_rgba(0,0,0,0.05)] border border-outline-variant p-xl flex flex-col items-center justify-center text-center py-20">
          <div className="w-24 h-24 mb-md opacity-50 flex items-center justify-center rounded-full bg-surface-container">
            <span className="material-symbols-outlined text-[48px] text-outline">history</span>
          </div>
          <h3 className="font-title-lg text-title-lg text-on-surface mb-2">No attempts recorded yet</h3>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-lg">Start practicing now to see your scores and detailed evaluations recorded here.</p>
          <button onClick={() => navigate('/mock-tests')} className="btn btn-primary">Go to Mock Tests</button>
        </div>
      ) : totalItems === 0 ? (
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
              setCurrentPage(1);
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
              Showing {startIndex + 1} to {startIndex + paginatedItems.length} of {totalItems} entries
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
                
                {pageWindow(currentPage, totalPages).map(page => (
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
