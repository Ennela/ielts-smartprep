/**
 * The enum values the API sends, in one place.
 *
 * The Task 1 list was written out in six files and the Task 2 list in two, so a new
 * essay type meant hunting for copies; `questionType` is branched on in fourteen files
 * against string literals the frontend only knew eight of.
 *
 * Mirrors com.smartprep.model.enums.EssayType and QuestionType.
 */

// ── Writing ──────────────────────────────────────────────────────────────────

/**
 * EssayType values that are Task 1.
 *
 * LETTER is one of them — EssayType.isTask1() says so — but every copy of this list
 * in the pages left it out, so a General Training letter was labelled "Task 2" and
 * held to the 250-word minimum instead of 150.
 */
export const TASK1_TYPES = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM', 'LETTER'];

/** EssayType values that are Task 2 (write an essay). */
export const TASK2_TYPES = [
  'OPINION', 'DISCUSSION', 'CAUSE_AND_EFFECT',
  'PROBLEM_AND_SOLUTION', 'ADVANTAGES_DISADVANTAGES', 'TWO_PART_QUESTION',
];

export const ALL_ESSAY_TYPES = [...TASK2_TYPES, ...TASK1_TYPES];

export const ESSAY_TYPE_LABELS = {
  OPINION: 'Opinion',
  DISCUSSION: 'Discussion',
  CAUSE_AND_EFFECT: 'Cause & Effect',
  PROBLEM_AND_SOLUTION: 'Problem & Solution',
  ADVANTAGES_DISADVANTAGES: 'Advantages & Disadvantages',
  TWO_PART_QUESTION: 'Two-Part Question',
  LINE_GRAPH: 'Line Graph',
  BAR_CHART: 'Bar Chart',
  PIE_CHART: 'Pie Chart',
  TABLE: 'Table',
  MAP: 'Map',
  DIAGRAM: 'Diagram',
  LETTER: 'Letter',
};

export const isTask1Type = (essayType) => TASK1_TYPES.includes(essayType);

/** "LINE_GRAPH" → "Line Graph"; unknown values are title-cased rather than dropped. */
export const formatEssayType = (essayType) =>
  ESSAY_TYPE_LABELS[essayType] ||
  (essayType ? essayType.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ') : '');

// ── Reading / Listening questions ────────────────────────────────────────────

/** Every QuestionType the backend can send. */
export const QUESTION_TYPES = {
  MCQ: 'MCQ',
  TFNG: 'TFNG',
  YNNG: 'YNNG',
  FILL_BLANK: 'FILL_BLANK',
  SENTENCE_COMPLETION: 'SENTENCE_COMPLETION',
  SUMMARY_COMPLETION: 'SUMMARY_COMPLETION',
  MATCHING_HEADINGS: 'MATCHING_HEADINGS',
  MATCHING_INFORMATION: 'MATCHING_INFORMATION',
  MATCHING_FEATURES: 'MATCHING_FEATURES',
  MATCHING_SENTENCE_ENDINGS: 'MATCHING_SENTENCE_ENDINGS',
  DIAGRAM_LABEL_COMPLETION: 'DIAGRAM_LABEL_COMPLETION',
  SHORT_ANSWER: 'SHORT_ANSWER',
};

/** Types answered by picking one of a shared set of options. */
export const MATCHING_TYPES = [
  QUESTION_TYPES.MATCHING_HEADINGS,
  QUESTION_TYPES.MATCHING_INFORMATION,
  QUESTION_TYPES.MATCHING_FEATURES,
  QUESTION_TYPES.MATCHING_SENTENCE_ENDINGS,
];

/** Types answered by typing into a blank. */
export const COMPLETION_TYPES = [
  QUESTION_TYPES.FILL_BLANK,
  QUESTION_TYPES.SENTENCE_COMPLETION,
  QUESTION_TYPES.SUMMARY_COMPLETION,
  QUESTION_TYPES.DIAGRAM_LABEL_COMPLETION,
  QUESTION_TYPES.SHORT_ANSWER,
];
