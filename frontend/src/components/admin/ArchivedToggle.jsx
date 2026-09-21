/**
 * Active / Archived switch for the admin content lists.
 *
 * Archiving is a soft delete and the confirm dialogs promise the item can be
 * restored, but until this existed no list could show archived rows, so the
 * restore endpoints were unreachable from the UI.
 */
export default function ArchivedToggle({ archived, onChange, id = 'archived-toggle' }) {
  return (
    <div className="archived-toggle" role="group" aria-label="Show active or archived items" id={id}>
      <button
        type="button"
        className={`filter-btn ${!archived ? 'active' : ''}`}
        onClick={() => onChange(false)}
      >Active</button>
      <button
        type="button"
        className={`filter-btn ${archived ? 'active' : ''}`}
        onClick={() => onChange(true)}
      >Archived</button>
    </div>
  );
}
