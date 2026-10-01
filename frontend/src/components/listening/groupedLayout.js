/**
 * Whether a Listening part should be shown with the grouped layout (QuestionPanel)
 * rather than one card per question.
 *
 * Until V54 a Listening question could only be a lone MCQ or a gap in its own sentence,
 * and that is all McqQuestion and FillBlankQuestion can draw. A part that groups its
 * questions -- notes with numbered gaps, a box of options A-H several questions choose
 * from -- or uses any other question type needs the layout Reading already has. Parts
 * written before V54 keep the cards they have always had.
 */
const CARD_TYPES = new Set(['MCQ', 'FILL_BLANK']);

export function usesGroupedLayout(questions) {
  return (questions || []).some(q => q.groupId != null || !CARD_TYPES.has(q.questionType));
}

/** How many questions come before this part, so numbering runs on across the test. */
export function questionOffset(parts, partIndex) {
  let offset = 0;
  for (let i = 0; i < partIndex; i++) offset += (parts[i].questions?.length || 0);
  return offset;
}
