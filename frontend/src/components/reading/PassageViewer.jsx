export default function PassageViewer({ passage }) {
  if (!passage) return null;

  // Split into paragraphs and detect labeled paragraphs (A. xxx, B. xxx)
  const paragraphs = passage.split('\n').filter((p) => p.trim().length > 0);

  return (
    <div className="passage-viewer" id="passage-viewer">
      <div className="passage-header">
        <h3>Reading Passage</h3>
        <span className="passage-badge">IELTS Academic</span>
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
