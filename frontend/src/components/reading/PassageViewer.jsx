export default function PassageViewer({ passage }) {
  if (!passage) return null;

  // Split into paragraphs for better readability
  const paragraphs = passage.split('\n').filter((p) => p.trim().length > 0);

  return (
    <div className="passage-viewer" id="passage-viewer">
      <div className="passage-header">
        <h3>Reading Passage</h3>
        <span className="passage-badge">IELTS Academic</span>
      </div>
      <div className="passage-body">
        {paragraphs.map((para, idx) => (
          <p key={idx} className="passage-paragraph">{para}</p>
        ))}
      </div>
    </div>
  );
}
