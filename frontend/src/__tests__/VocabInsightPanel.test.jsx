import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import VocabInsightPanel from '../components/vocab/VocabInsightPanel';

/*
 * The explanation panel has to survive everything the AI can hand it: a word with no
 * explanation yet, an explanation missing half its optional sections, and a generation
 * that failed outright. In all three cases the learner keeps the word and its meaning.
 */

vi.mock('../api/vocabApi', () => ({
  default: {
    getInsight: vi.fn(),
    generateInsight: vi.fn(),
  },
}));

import vocabApi from '../api/vocabApi';

const ok = (data) => ({ data: { success: true, data } });

const vocab = {
  vocabId: 7,
  word: 'trust',
  phonetic: '/trʌst/',
  partOfSpeech: 'verb',
  meaningVi: 'tin tưởng',
};

/** The smallest explanation the validator lets through: core meaning plus one sense. */
const minimalInsight = {
  word: 'trust',
  coreMeaningVi: 'Tin vào sự đáng tin cậy của ai đó.',
  senses: [{ meaningVi: 'Tin rằng ai đó đáng tin cậy.' }],
};

const fullInsight = {
  ...minimalInsight,
  ipa: '/trʌst/',
  partOfSpeech: 'verb',
  definitionEn: 'To believe that someone is reliable.',
  registers: ['neutral'],
  registerNoteVi: 'Trung tính trong hầu hết ngữ cảnh.',
  contextSummaryVi: 'Người nói nhấn mạnh rằng họ dựa vào được vào ai đó.',
  senses: [
    {
      meaningVi: 'Tin rằng ai đó đáng tin cậy.',
      contextVi: 'Khi nói về sự đáng tin của một người.',
      registers: ['neutral'],
      examples: [
        {
          english: 'I trust him to keep his promises.',
          vietnamese: 'Tôi tin anh ấy sẽ giữ lời hứa.',
          whyVi: 'Nhấn mạnh sự đáng tin cậy.',
          setting: 'EVERYDAY',
        },
      ],
    },
  ],
  synonymComparisons: [
    {
      focus: 'trust vs faith',
      words: [
        { word: 'trust', coreIdeaVi: 'Tin vào sự đáng tin cậy.' },
        { word: 'faith', coreIdeaVi: 'Niềm tin sâu sắc, không cần bằng chứng.' },
      ],
      interchangeabilityVi: 'Không phải lúc nào cũng thay thế được cho nhau.',
    },
  ],
  collocations: [{ phrase: 'earn trust', meaningVi: 'giành được lòng tin' }],
  grammarPatterns: [{ pattern: 'trust sb to do sth', explanationVi: 'Tin ai đó sẽ làm gì.' }],
  commonMistakes: [
    { incorrect: 'I trust on him.', corrected: 'I trust him.', type: 'GRAMMAR', whyVi: 'Trust là ngoại động từ.' },
  ],
  ieltsGuidance: { speakingVi: 'Tự nhiên khi nói về con người.' },
};

const renderPanel = (onClose = vi.fn()) =>
  render(<VocabInsightPanel vocab={vocab} onClose={onClose} />);

describe('VocabInsightPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the saved meaning even before any explanation exists', async () => {
    vocabApi.getInsight.mockResolvedValue(ok({ vocabId: 7, word: 'trust', status: 'NOT_GENERATED' }));

    renderPanel();

    expect(await screen.findByText('tin tưởng')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tạo giải thích' })).toBeInTheDocument();
    expect(vocabApi.generateInsight).not.toHaveBeenCalled();
  });

  it('generates only when the learner asks, so opening a word costs nothing', async () => {
    vocabApi.getInsight.mockResolvedValue(ok({ vocabId: 7, word: 'trust', status: 'NOT_GENERATED' }));
    vocabApi.generateInsight.mockResolvedValue(
      ok({ vocabId: 7, word: 'trust', status: 'READY', insight: fullInsight })
    );

    renderPanel();
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo giải thích' }));

    expect(await screen.findByText('Nghĩa và ngữ cảnh')).toBeInTheDocument();
    expect(vocabApi.generateInsight).toHaveBeenCalledWith(7, false);
  });

  it('renders every section of a complete explanation', async () => {
    vocabApi.getInsight.mockResolvedValue(
      ok({ vocabId: 7, word: 'trust', status: 'READY', insight: fullInsight })
    );

    renderPanel();

    expect(await screen.findByText('Nghĩa và ngữ cảnh')).toBeInTheDocument();
    expect(screen.getByText('Phân biệt từ gần nghĩa')).toBeInTheDocument();
    expect(screen.getByText('Cụm từ và cấu trúc')).toBeInTheDocument();
    expect(screen.getByText('Lỗi thường gặp')).toBeInTheDocument();
    expect(screen.getByText('Dùng trong IELTS')).toBeInTheDocument();
    // The meaning is readable straight away; the rest waits to be asked for.
    expect(screen.getByText('Tin rằng ai đó đáng tin cậy.')).toBeInTheDocument();
    expect(screen.queryByText('trust vs faith')).not.toBeInTheDocument();
  });

  it('expands a section on demand without losing the others', async () => {
    vocabApi.getInsight.mockResolvedValue(
      ok({ vocabId: 7, word: 'trust', status: 'READY', insight: fullInsight })
    );

    renderPanel();
    fireEvent.click(await screen.findByRole('button', { name: /Phân biệt từ gần nghĩa/ }));

    expect(screen.getByText('trust vs faith')).toBeInTheDocument();
    expect(screen.getByText('Niềm tin sâu sắc, không cần bằng chứng.')).toBeInTheDocument();
    expect(screen.getByText('Tin rằng ai đó đáng tin cậy.')).toBeInTheDocument();
  });

  it('renders an explanation whose optional sections are missing', async () => {
    vocabApi.getInsight.mockResolvedValue(
      ok({ vocabId: 7, word: 'trust', status: 'READY', insight: minimalInsight })
    );

    renderPanel();

    expect(await screen.findByText('Nghĩa và ngữ cảnh')).toBeInTheDocument();
    expect(screen.queryByText('Phân biệt từ gần nghĩa')).not.toBeInTheDocument();
    expect(screen.queryByText('Lỗi thường gặp')).not.toBeInTheDocument();
    expect(screen.queryByText('Dùng trong IELTS')).not.toBeInTheDocument();
  });

  it('keeps the word readable when generation is unavailable', async () => {
    vocabApi.getInsight.mockResolvedValue(
      ok({ vocabId: 7, word: 'trust', status: 'UNAVAILABLE', message: 'Thử lại sau.' })
    );

    renderPanel();

    expect(await screen.findByText('Chưa tạo được giải thích')).toBeInTheDocument();
    expect(screen.getByText('Thử lại sau.')).toBeInTheDocument();
    expect(screen.getByText('tin tưởng')).toBeInTheDocument();
  });

  it('reports a failed request instead of rendering an empty panel', async () => {
    vocabApi.getInsight.mockRejectedValue(new Error('Service Unavailable'));

    renderPanel();

    expect(await screen.findByRole('alert')).toHaveTextContent('Service Unavailable');
    expect(screen.getByText('trust')).toBeInTheDocument();
  });

  it('closes on Escape so a review session is never trapped', async () => {
    vocabApi.getInsight.mockResolvedValue(ok({ vocabId: 7, word: 'trust', status: 'NOT_GENERATED' }));
    const onClose = vi.fn();

    renderPanel(onClose);
    await screen.findByText('tin tưởng');
    fireEvent.keyDown(document, { key: 'Escape' });

    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });
});
