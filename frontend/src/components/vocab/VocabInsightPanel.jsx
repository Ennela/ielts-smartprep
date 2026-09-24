import { useCallback, useEffect, useState } from 'react';
import vocabApi from '../../api/vocabApi';
import styles from '../../styles/VocabInsight.module.css';

/*
 * The context-aware explanation for one word, as a side panel.
 *
 * It is a panel and not a route so that opening a word never costs the learner their place:
 * a review session keeps its card, the word bank keeps its page and its filters.
 *
 * Every section below the header is optional. A word with no useful synonym distinction has
 * no comparison section, and an explanation stored before a section existed simply renders
 * without it — which is also what happens to every vocabulary item saved before this feature.
 */

const REGISTER_LABELS = {
  informal: 'Thân mật',
  neutral: 'Trung tính',
  formal: 'Trang trọng',
  academic: 'Học thuật',
  slang: 'Tiếng lóng',
  literary: 'Văn chương',
};

const SETTING_LABELS = {
  EVERYDAY: 'Giao tiếp hằng ngày',
  IELTS_SPEAKING: 'IELTS Speaking',
  IELTS_WRITING: 'IELTS Writing',
};

const MISTAKE_LABELS = {
  GRAMMAR: 'Sai ngữ pháp',
  UNNATURAL: 'Đúng ngữ pháp nhưng không tự nhiên',
  REGISTER: 'Sai sắc thái trang trọng',
  CONTEXT: 'Đúng từ nhưng sai ngữ cảnh',
};

const hasItems = (list) => Array.isArray(list) && list.length > 0;

function Badge({ children }) {
  return <span className={styles.badge}>{children}</span>;
}

function Section({ id, title, count, openSections, onToggle, children }) {
  const isOpen = Boolean(openSections[id]);
  return (
    <section className={styles.section}>
      <button
        type="button"
        className={styles['section-toggle']}
        aria-expanded={isOpen}
        onClick={() => onToggle(id)}
      >
        <span>{title}</span>
        {count ? <span className={styles['section-count']}>{count}</span> : null}
        <span className="material-symbols-outlined" style={{ fontSize: 20 }} aria-hidden="true">
          {isOpen ? 'expand_less' : 'expand_more'}
        </span>
      </button>
      {isOpen && <div className={styles['section-content']}>{children}</div>}
    </section>
  );
}

function Example({ example }) {
  return (
    <div className={styles.example}>
      {example.setting && SETTING_LABELS[example.setting] && (
        <span className={styles.setting}>{SETTING_LABELS[example.setting]}</span>
      )}
      <p className={styles['example-en']}>{example.english}</p>
      {example.vietnamese && <p className={styles['example-vi']}>{example.vietnamese}</p>}
      {example.whyVi && <p className={styles['example-why']}>{example.whyVi}</p>}
    </div>
  );
}

function ExampleList({ examples }) {
  if (!hasItems(examples)) return null;
  return (
    <div className={styles.examples}>
      {examples.map((example, index) => (
        <Example key={`${example.english}-${index}`} example={example} />
      ))}
    </div>
  );
}

function PatternList({ items, render }) {
  return (
    <ul className={styles['pattern-list']}>
      {items.map((item, index) => (
        <li key={index} className={styles.pattern}>
          {render(item)}
        </li>
      ))}
    </ul>
  );
}

function Comparison({ comparison }) {
  const words = comparison.words || [];
  // Two or three words line up as columns on a wide panel; anything else stays stacked,
  // which is also what happens on a phone.
  const gridClass = [styles['compare-grid'], styles[`compare-grid-${words.length}`]]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={styles.comparison}>
      {comparison.focus && <h4 className={styles['comparison-focus']}>{comparison.focus}</h4>}

      <div className={gridClass}>
        {words.map((entry) => (
          <div key={entry.word} className={styles['compare-card']}>
            <p className={styles['compare-word']}>{entry.word}</p>

            <div className={styles['compare-row']}>
              <span className={styles.label}>Ý chính</span>
              <p className={styles['compare-value']}>{entry.coreIdeaVi}</p>
            </div>

            {entry.typicalContextVi && (
              <div className={styles['compare-row']}>
                <span className={styles.label}>Ngữ cảnh thường gặp</span>
                <p className={styles['compare-value']}>{entry.typicalContextVi}</p>
              </div>
            )}

            {entry.nuanceVi && (
              <div className={styles['compare-row']}>
                <span className={styles.label}>Sắc thái</span>
                <p className={styles['compare-value']}>{entry.nuanceVi}</p>
              </div>
            )}

            {hasItems(entry.registers) && (
              <div className={styles.badges}>
                {entry.registers.map((register) => (
                  <Badge key={register}>{REGISTER_LABELS[register] || register}</Badge>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {comparison.interchangeabilityVi && (
        <p className={styles.interchange}>{comparison.interchangeabilityVi}</p>
      )}

      <ExampleList examples={comparison.contrastExamples} />
    </div>
  );
}

function InsightBody({ insight, openSections, onToggle }) {
  const collocations = insight.collocations || [];
  const grammarPatterns = insight.grammarPatterns || [];
  const guidance = insight.ieltsGuidance;

  return (
    <div className={styles.body}>
      <Section
        id="senses"
        title="Nghĩa và ngữ cảnh"
        count={insight.senses?.length}
        openSections={openSections}
        onToggle={onToggle}
      >
        {(insight.senses || []).map((sense, index) => (
          <div key={index} className={styles.sense}>
            <p className={styles['sense-meaning']}>{sense.meaningVi}</p>
            {sense.definitionEn && <p className={styles['sense-def']}>{sense.definitionEn}</p>}
            {hasItems(sense.registers) && (
              <div className={styles.badges}>
                {sense.registers.map((register) => (
                  <Badge key={register}>{REGISTER_LABELS[register] || register}</Badge>
                ))}
              </div>
            )}
            {sense.contextVi && <p className={styles['sense-context']}>{sense.contextVi}</p>}
            <ExampleList examples={sense.examples} />
          </div>
        ))}
      </Section>

      {hasItems(insight.synonymComparisons) && (
        <Section
          id="synonyms"
          title="Phân biệt từ gần nghĩa"
          openSections={openSections}
          onToggle={onToggle}
        >
          {insight.synonymComparisons.map((comparison, index) => (
            <Comparison key={index} comparison={comparison} />
          ))}
        </Section>
      )}

      {(hasItems(collocations) || hasItems(grammarPatterns)) && (
        <Section
          id="patterns"
          title="Cụm từ và cấu trúc"
          count={collocations.length + grammarPatterns.length}
          openSections={openSections}
          onToggle={onToggle}
        >
          {hasItems(collocations) && (
            <div>
              <p className={styles.subheading}>Cụm từ thường đi cùng</p>
              <PatternList
                items={collocations}
                render={(item) => (
                  <>
                    <p className={styles['pattern-name']}>{item.phrase}</p>
                    {item.meaningVi && <p className={styles['pattern-meaning']}>{item.meaningVi}</p>}
                    {item.example && <p className={styles['pattern-example']}>{item.example}</p>}
                  </>
                )}
              />
            </div>
          )}

          {hasItems(grammarPatterns) && (
            <div>
              <p className={styles.subheading}>Cấu trúc ngữ pháp</p>
              <PatternList
                items={grammarPatterns}
                render={(item) => (
                  <>
                    <p className={styles['pattern-name']}>{item.pattern}</p>
                    {item.explanationVi && (
                      <p className={styles['pattern-meaning']}>{item.explanationVi}</p>
                    )}
                    {item.example && <p className={styles['pattern-example']}>{item.example}</p>}
                  </>
                )}
              />
            </div>
          )}
        </Section>
      )}

      {hasItems(insight.commonMistakes) && (
        <Section
          id="mistakes"
          title="Lỗi thường gặp"
          count={insight.commonMistakes.length}
          openSections={openSections}
          onToggle={onToggle}
        >
          {insight.commonMistakes.map((mistake, index) => (
            <div key={index} className={styles.mistake}>
              {mistake.type && MISTAKE_LABELS[mistake.type] && (
                <span className={styles['mistake-type']}>{MISTAKE_LABELS[mistake.type]}</span>
              )}
              <p className={styles['mistake-line']}>
                <span className={styles['mark-wrong']} aria-hidden="true">
                  ✕
                </span>
                <span className={styles['wrong-text']}>{mistake.incorrect}</span>
              </p>
              {mistake.corrected && (
                <p className={styles['mistake-line']}>
                  <span className={styles['mark-right']} aria-hidden="true">
                    ✓
                  </span>
                  <span className={styles['right-text']}>{mistake.corrected}</span>
                </p>
              )}
              {mistake.whyVi && <p className={styles['mistake-why']}>{mistake.whyVi}</p>}
            </div>
          ))}
        </Section>
      )}

      {guidance && (
        <Section id="ielts" title="Dùng trong IELTS" openSections={openSections} onToggle={onToggle}>
          {guidance.speakingVi && (
            <div className={styles['guidance-item']}>
              <span className={styles.label}>Speaking</span>
              <p className={styles['guidance-text']}>{guidance.speakingVi}</p>
            </div>
          )}
          {guidance.writingVi && (
            <div className={styles['guidance-item']}>
              <span className={styles.label}>Writing</span>
              <p className={styles['guidance-text']}>{guidance.writingVi}</p>
            </div>
          )}
          {guidance.cautionVi && (
            <div className={styles['guidance-item']}>
              <span className={styles.label}>Cần lưu ý</span>
              <p className={styles['guidance-text']}>{guidance.cautionVi}</p>
            </div>
          )}
        </Section>
      )}
    </div>
  );
}

export default function VocabInsightPanel({ vocab, onClose }) {
  const [response, setResponse] = useState(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState('');
  // The first section is open so the learner reads the meaning immediately; the rest waits
  // to be asked for.
  const [openSections, setOpenSections] = useState({ senses: true });

  const toggleSection = useCallback((id) => {
    setOpenSections((prev) => ({ ...prev, [id]: !prev[id] }));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    vocabApi
      .getInsight(vocab.vocabId)
      .then((res) => {
        if (!cancelled) setResponse(res.data?.data || null);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'Không tải được giải thích.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [vocab.vocabId]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const generate = async (refresh) => {
    setGenerating(true);
    setError('');
    try {
      const res = await vocabApi.generateInsight(vocab.vocabId, refresh);
      setResponse(res.data?.data || null);
    } catch (err) {
      setError(err.message || 'Không tạo được giải thích. Bạn hãy thử lại sau.');
    } finally {
      setGenerating(false);
    }
  };

  const insight = response?.status === 'READY' ? response.insight : null;
  const registers = insight?.registers || [];

  return (
    <div
      className={styles.overlay}
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className={styles.panel} role="dialog" aria-modal="true" aria-label={`Giải thích từ ${vocab.word}`}>
        <div className={styles.header}>
          <div className={styles['header-top']}>
            <div>
              <h2 className={styles.word}>{insight?.word || vocab.word}</h2>
              <div className={styles.pronunciation}>
                {(insight?.ipa || vocab.phonetic) && (
                  <span className={styles.ipa}>{insight?.ipa || vocab.phonetic}</span>
                )}
                {(insight?.partOfSpeech || vocab.partOfSpeech) && (
                  <span className={styles.pos}>{insight?.partOfSpeech || vocab.partOfSpeech}</span>
                )}
              </div>
            </div>
            <button type="button" className={styles.close} onClick={onClose} aria-label="Đóng">
              <span className="material-symbols-outlined">close</span>
            </button>
          </div>

          {/* The saved meaning is always shown, so the panel is useful even before any
              explanation exists — and for every word saved before this feature. */}
          <p className={styles['core-meaning']}>{insight?.coreMeaningVi || vocab.meaningVi}</p>
          {insight?.definitionEn && <p className={styles['definition-en']}>{insight.definitionEn}</p>}

          {registers.length > 0 && (
            <div className={styles.badges}>
              {registers.map((register) => (
                <Badge key={register}>{REGISTER_LABELS[register] || register}</Badge>
              ))}
            </div>
          )}
          {insight?.registerNoteVi && <p className={styles['badge-note']}>{insight.registerNoteVi}</p>}
          {insight?.contextSummaryVi && <p className={styles.summary}>{insight.contextSummaryVi}</p>}
        </div>

        {loading || generating ? (
          <div className={styles.state}>
            <span className="spinner" style={{ width: 28, height: 28 }} />
            <p className={styles['state-text']} style={{ marginTop: 16 }}>
              {generating ? 'Đang phân tích cách dùng của từ này…' : 'Đang tải…'}
            </p>
          </div>
        ) : insight ? (
          <InsightBody insight={insight} openSections={openSections} onToggle={toggleSection} />
        ) : (
          <div className={styles.state}>
            <p className={styles['state-title']}>
              {response?.status === 'UNAVAILABLE' ? 'Chưa tạo được giải thích' : 'Chưa có giải thích'}
            </p>
            <p className={styles['state-text']}>
              {response?.message ||
                'Tạo giải thích chi tiết về ngữ cảnh, sắc thái, từ gần nghĩa và cách dùng trong IELTS.'}
            </p>
            <button type="button" className="btn btn-primary" onClick={() => generate(false)}>
              Tạo giải thích
            </button>
          </div>
        )}

        {error && (
          <p className={styles.notice} role="alert" style={{ margin: '0 24px 16px' }}>
            {error}
          </p>
        )}

        {insight && !generating && (
          <div className={styles.footer}>
            <span className={styles['generated-at']}>
              {response?.message ||
                (response?.generatedAt
                  ? `Tạo ngày ${new Date(response.generatedAt).toLocaleDateString('vi-VN')}`
                  : '')}
            </span>
            <button type="button" className={styles['open-btn']} onClick={() => generate(true)}>
              <span className="material-symbols-outlined" style={{ fontSize: 16 }} aria-hidden="true">
                refresh
              </span>
              Tạo lại
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
