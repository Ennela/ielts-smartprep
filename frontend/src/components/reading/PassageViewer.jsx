export default function PassageViewer({ passage, moduleType }) {
  if (!passage) return null;

  // Split into paragraphs and detect labeled paragraphs (A. xxx, B. xxx)
  const paragraphs = passage.split('\n').filter((p) => p.trim().length > 0);

  return (
    <div className="passage-viewer" id="passage-viewer">
      <div className="passage-header">
        <h3>Reading Passage</h3>
        {/* Stored as GENERAL or GENERAL_TRAINING; it used to say Academic for both. */}
        <span className="passage-badge">
          {moduleType && moduleType.startsWith('GENERAL') ? 'IELTS General Training' : 'IELTS Academic'}
        </span>
      </div>
      <div className="passage-body">
        {paragraphs.map((para, idx) => {
          // Detect paragraph label pattern: "A. text..." or "B. text..."
          const labelMatch = para.match(/^([A-Z])\.\s+(.*)/s);
          if (labelMatch) {
            return (
              <div key={idx} className="passage-paragraph labeled-paragraph">
                <span className="paragraph-label">{labelMatch[1]}</span>
                <p>{labelMatch[2]}</p>
              </div>
            );
          }
          return <p key={idx} className="passage-paragraph">{para}</p>;
        })}
      </div>
    </div>
  );
}
