import { createContext, useContext, useReducer, useCallback } from 'react';

export const ReadingContext = createContext(null);

const initialState = {
  quiz: null,
  answers: {},        // { questionId: userAnswer }
  timeRemaining: 0,
  isSubmitted: false,
  result: null,
  loading: false,
  error: null,
};

function readingReducer(state, action) {
  switch (action.type) {
    case 'SET_LOADING':
      return { ...state, loading: action.payload, error: null };
    case 'SET_ERROR':
      return { ...state, loading: false, error: action.payload };
    case 'SET_QUIZ': {
      const quizId = action.payload.quizId;
      let draftAnswers = {};
      try {
        const savedDraft = localStorage.getItem(`reading_quiz_draft_${quizId}`);
        if (savedDraft) {
          draftAnswers = JSON.parse(savedDraft);
        }
      } catch (e) {
        console.error("Failed to load draft", e);
      }
      return {
        ...state,
        quiz: action.payload,
        answers: draftAnswers,
        timeRemaining: action.payload.timeLimitSeconds || 600,
        isSubmitted: action.payload.submitted || false,
        loading: false,
        error: null,
      };
    }
    case 'SET_ANSWER': {
      const newAnswers = { ...state.answers, [action.payload.questionId]: action.payload.answer };
      if (state.quiz && state.quiz.quizId) {
        try {
          localStorage.setItem(`reading_quiz_draft_${state.quiz.quizId}`, JSON.stringify(newAnswers));
        } catch (e) {
          console.error("Failed to save draft", e);
        }
      }
      return {
        ...state,
        answers: newAnswers,
      };
    }
    case 'TICK_TIMER':
      return {
        ...state,
        timeRemaining: Math.max(0, state.timeRemaining - 1),
      };
    case 'SUBMIT':
      return { ...state, isSubmitted: true, loading: true };
    case 'SET_RESULT':
      if (state.quiz && state.quiz.quizId) {
        try {
          localStorage.removeItem(`reading_quiz_draft_${state.quiz.quizId}`);
        } catch (e) {
          console.error("Failed to delete draft", e);
        }
      }
      return { ...state, result: action.payload, isSubmitted: true, loading: false };
    case 'RESET':
      return { ...initialState };
    default:
      return state;
  }
}

export function ReadingProvider({ children }) {
  const [state, dispatch] = useReducer(readingReducer, initialState);

  const setLoading = useCallback((val) => dispatch({ type: 'SET_LOADING', payload: val }), []);
  const setError = useCallback((msg) => dispatch({ type: 'SET_ERROR', payload: msg }), []);
  const setQuiz = useCallback((quiz) => dispatch({ type: 'SET_QUIZ', payload: quiz }), []);
  const setAnswer = useCallback((questionId, answer) =>
    dispatch({ type: 'SET_ANSWER', payload: { questionId, answer } }), []);
  const tickTimer = useCallback(() => dispatch({ type: 'TICK_TIMER' }), []);
  const submitStart = useCallback(() => dispatch({ type: 'SUBMIT' }), []);
  const setResult = useCallback((result) => dispatch({ type: 'SET_RESULT', payload: result }), []);
  const reset = useCallback(() => dispatch({ type: 'RESET' }), []);

  const value = {
    ...state,
    setLoading,
    setError,
    setQuiz,
    setAnswer,
    tickTimer,
    submitStart,
    setResult,
    reset,
  };

  return (
    <ReadingContext.Provider value={value}>
      {children}
    </ReadingContext.Provider>
  );
}

export const useReading = () => useContext(ReadingContext);
