import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MockTestAnalyticsDashboard from '../components/mocktest/MockTestAnalyticsDashboard';
import ProgressTimelineChart from '../components/mocktest/ProgressTimelineChart';

/*
 * The dashboard answers five questions in order. These tests pin the answers that must
 * be visible for a completed sitting, and what must NOT be shown while writing is still
 * grading or when Speaking is simply not part of the platform.
 */

const completedAnalytics = {
  submissionId: 500,
  status: 'COMPLETED',
  summary: {
    overallBand: 5.5,
    listeningBand: 6.0,
    readingBand: 5.5,
    writingBand: 5.5,
    speakingIncluded: false,
    speakingNote: 'Speaking: Not included',
    overallNote: 'Average of Listening, Reading and Writing. This is a 3-skill band, not a full IELTS 4-skill result.',
    weakestSkills: ['READING', 'WRITING'],
  },
  skills: [
    { skill: 'LISTENING', band: 6.0, correct: 4, total: 6, accuracy: 66.7, level: 'DEVELOPING', targetBand: 7.0, gapToTarget: 1.0, graded: true },
    { skill: 'READING', band: 5.5, correct: 5, total: 9, accuracy: 55.6, level: 'WEAK', targetBand: 6.5, gapToTarget: 1.0, graded: true },
    { skill: 'WRITING', band: 5.5, correct: null, total: null, accuracy: null, level: 'DEVELOPING', targetBand: 6.5, gapToTarget: 1.0, graded: true },
  ],
  listening: {
    correct: 4, total: 6, accuracy: 66.7, level: 'DEVELOPING',
    byPart: [
      { key: 'Part 1', label: 'Part 1', correct: 3, total: 3, accuracy: 100, level: 'STRONG' },
      { key: 'Part 2', label: 'Part 2', correct: 1, total: 3, accuracy: 33.3, level: 'WEAK' },
    ],
    byQuestionType: [
      { key: 'MCQ', label: 'Multiple choice', correct: 1, total: 2, accuracy: 50, level: 'WEAK' },
      { key: 'FILL_BLANK', label: 'Fill in the blank', correct: 3, total: 4, accuracy: 75, level: 'DEVELOPING' },
    ],
    wrongQuestions: [{ questionId: 204 }, { questionId: 205 }],
  },
  reading: {
    correct: 5, total: 9, accuracy: 55.6, level: 'WEAK',
    byQuestionType: [
      { key: 'TFNG', label: 'True / False / Not Given', correct: 1, total: 4, accuracy: 25, level: 'WEAK' },
      { key: 'MCQ', label: 'Multiple choice', correct: 1, total: 2, accuracy: 50, level: 'WEAK' },
      { key: 'SHORT_ANSWER', label: 'Short answer', correct: 3, total: 3, accuracy: 100, level: 'STRONG' },
    ],
    wrongQuestions: [{ questionId: 102 }, { questionId: 104 }, { questionId: 105 }, { questionId: 106 }],
  },
  writing: {
    band: 5.5, level: 'DEVELOPING',
    task1: { submissionId: 701, band: 5.5, wordCount: 160, criteria: [] },
    task2: { submissionId: 702, band: 6.0, wordCount: 260, criteria: [] },
    criteria: [
      { key: 'TASK_RESPONSE', label: 'Task Response', band: 5.5, level: 'DEVELOPING' },
      { key: 'COHERENCE_COHESION', label: 'Coherence & Cohesion', band: 6.5, level: 'DEVELOPING' },
      { key: 'LEXICAL_RESOURCE', label: 'Lexical Resource', band: 6.5, level: 'DEVELOPING' },
      { key: 'GRAMMAR', label: 'Grammatical Range & Accuracy', band: 5.0, level: 'WEAK' },
    ],
  },
  weaknesses: [
    { skill: 'READING', category: 'QUESTION_TYPE', key: 'TFNG', label: 'True / False / Not Given', accuracy: 25, sampleSize: 4, level: 'WEAK' },
    { skill: 'LISTENING', category: 'PART', key: 'Part 2', label: 'Part 2', accuracy: 33.3, sampleSize: 3, level: 'WEAK' },
    { skill: 'WRITING', category: 'CRITERION', key: 'GRAMMAR', label: 'Grammatical Range & Accuracy', band: 5.0, level: 'WEAK' },
  ],
  progress: {
    attemptNumber: 2,
    totalAttempts: 2,
    timeline: [
      { submissionId: 400, attemptNumber: 1, submittedAt: '2026-09-01T10:00:00', overallBand: 5.0, listeningBand: 5.0, readingBand: 5.0, writingBand: 5.0, current: false },
      { submissionId: 500, attemptNumber: 2, submittedAt: '2026-09-08T10:00:00', overallBand: 5.5, listeningBand: 6.0, readingBand: 5.5, writingBand: 5.5, current: true },
    ],
    trends: [
      { skill: 'OVERALL', current: 5.5, previous: 5.0, delta: 0.5, direction: 'IMPROVING' },
      { skill: 'LISTENING', current: 6.0, previous: 5.0, delta: 1.0, direction: 'IMPROVING' },
      { skill: 'READING', current: 5.5, previous: 5.0, delta: 0.5, direction: 'IMPROVING' },
      { skill: 'WRITING', current: 5.5, previous: 5.0, delta: 0.5, direction: 'IMPROVING' },
    ],
  },
  recommendations: [
    { skill: 'READING', title: 'Practise Reading: True / False / Not Given', reason: '25% accuracy in this test (4 questions).', actionPath: '/reading' },
    { skill: 'LISTENING', title: 'Practise Listening Part 2', reason: '33.3% accuracy in this test (3 questions).', actionPath: '/listening' },
    { skill: 'WRITING', title: 'Work on Writing: Grammatical Range & Accuracy', reason: 'Scored band 5.0 across both tasks, below the developing mark.', actionPath: '/writing' },
  ],
};

const renderDashboard = (analytics, onReviewSkill = vi.fn()) =>
  render(
    <MemoryRouter>
      <MockTestAnalyticsDashboard analytics={analytics} onReviewSkill={onReviewSkill} />
    </MemoryRouter>
  );

describe('MockTestAnalyticsDashboard', () => {
  it('shows the three graded bands, the overall as a 3-skill band, and Speaking as not included', () => {
    renderDashboard(completedAnalytics);

    expect(within(screen.getByTestId('band-listening')).getByText('6.0')).toBeInTheDocument();
    expect(within(screen.getByTestId('band-reading')).getByText('5.5')).toBeInTheDocument();
    expect(within(screen.getByTestId('band-writing')).getByText('5.5')).toBeInTheDocument();
    expect(screen.getByText('Overall band (3 skills)')).toBeInTheDocument();
    expect(screen.getByText(/not a full IELTS 4-skill result/)).toBeInTheDocument();

    const speaking = screen.getByTestId('band-speaking');
    expect(within(speaking).getByText('Not included')).toBeInTheDocument();
    expect(within(speaking).getByText('Speaking: Not included')).toBeInTheDocument();
  });

  it('names every skill tied at the lowest band as pulling the score down', () => {
    renderDashboard(completedAnalytics);

    expect(within(screen.getByTestId('band-reading')).getByText('Pulling score down')).toBeInTheDocument();
    expect(within(screen.getByTestId('band-writing')).getByText('Pulling score down')).toBeInTheDocument();
    expect(within(screen.getByTestId('band-listening')).queryByText('Pulling score down')).not.toBeInTheDocument();
  });

  it('lists accuracy by question type and by part with a level label, and the writing criteria', () => {
    renderDashboard(completedAnalytics);

    expect(screen.getByText('True / False / Not Given')).toBeInTheDocument();
    expect(screen.getByText('1/4 · 25%')).toBeInTheDocument();
    expect(screen.getByText('Part 2')).toBeInTheDocument();
    expect(screen.getByText('1/3 · 33%')).toBeInTheDocument();
    expect(screen.getByText('Grammatical Range & Accuracy')).toBeInTheDocument();
    expect(screen.getByText('Band 5.0')).toBeInTheDocument();
    // Levels are text, not just colour.
    expect(screen.getAllByText('Weak').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Strong').length).toBeGreaterThan(0);
  });

  it('hands off to the detailed review for the wrong answers of a skill', () => {
    const onReviewSkill = vi.fn();
    renderDashboard(completedAnalytics, onReviewSkill);

    fireEvent.click(screen.getByRole('button', { name: 'Review 4 wrong answers' }));
    expect(onReviewSkill).toHaveBeenCalledWith('reading');

    fireEvent.click(screen.getByRole('button', { name: 'Review 2 wrong answers' }));
    expect(onReviewSkill).toHaveBeenCalledWith('listening');
  });

  it('shows the attempt timeline with the change since the last sitting', () => {
    renderDashboard(completedAnalytics);

    expect(screen.getByText('Attempt 2 of 2 completed. Am I improving?')).toBeInTheDocument();
    expect(screen.getByText('▲ +1.0 vs last')).toBeInTheDocument();
    expect(screen.getAllByText('▲ +0.5 vs last')).toHaveLength(3);
    // The table is the accessible form of the chart: one row per completed sitting.
    expect(screen.getByRole('link', { name: 'Attempt 1' })).toBeInTheDocument();
    expect(screen.getByText('Attempt 2 (this one)')).toBeInTheDocument();
  });

  it('lists the recommended next steps worst first, each with a way to start', () => {
    renderDashboard(completedAnalytics);

    const titles = ['Practise Reading: True / False / Not Given', 'Practise Listening Part 2', 'Work on Writing: Grammatical Range & Accuracy'];
    const found = titles.map((t) => screen.getByText(t));
    expect(found).toHaveLength(3);
    expect(found[0].compareDocumentPosition(found[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Start' })).toHaveLength(3);
  });

  it('does not fabricate a writing or overall band while grading is in progress', () => {
    const grading = {
      ...completedAnalytics,
      status: 'GRADING',
      summary: { ...completedAnalytics.summary, overallBand: null, writingBand: null, weakestSkills: ['READING'] },
      skills: completedAnalytics.skills.map((s) => (s.skill === 'WRITING' ? { ...s, band: null, level: null, gapToTarget: null, graded: false } : s)),
      writing: null,
      weaknesses: completedAnalytics.weaknesses.filter((w) => w.skill !== 'WRITING'),
      recommendations: completedAnalytics.recommendations.filter((r) => r.skill !== 'WRITING'),
      progress: {
        ...completedAnalytics.progress,
        attemptNumber: 0,
        totalAttempts: 1,
        trends: completedAnalytics.progress.trends.map((t) =>
          t.skill === 'OVERALL' || t.skill === 'WRITING' ? { ...t, current: null, delta: null, direction: 'PENDING' } : t
        ),
      },
    };
    renderDashboard(grading);

    expect(within(screen.getByTestId('band-writing')).getByText('Pending')).toBeInTheDocument();
    expect(within(screen.getByTestId('band-writing')).queryByText('0.0')).not.toBeInTheDocument();
    expect(screen.getByText('Writing criteria appear once grading has finished.')).toBeInTheDocument();
    expect(screen.queryByText('Work on Writing: Grammatical Range & Accuracy')).not.toBeInTheDocument();
    expect(screen.getAllByText('Pending').length).toBeGreaterThanOrEqual(2);
  });
});

describe('ProgressTimelineChart', () => {
  it('explains itself instead of drawing a line through a single point', () => {
    render(<ProgressTimelineChart timeline={[completedAnalytics.progress.timeline[1]]} />);
    expect(screen.getByText(/One completed sitting so far/)).toBeInTheDocument();
  });

  it('renders one line per skill, never a fourth for the overall band', () => {
    render(<ProgressTimelineChart timeline={completedAnalytics.progress.timeline} />);
    const legendItems = ['Listening', 'Reading', 'Writing'].map((l) => screen.getByText(l));
    expect(legendItems).toHaveLength(3);
    expect(screen.queryByText('Overall')).not.toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Band score per skill/ })).toBeInTheDocument();
  });
});
