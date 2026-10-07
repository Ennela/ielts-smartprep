import { useState } from 'react';
import adminApi from '../../api/adminApi';
import errorMessage from '../../utils/errorMessage';

/**
 * An image an admin attaches to content: a question group's diagram or map, or a Task 1
 * chart. The file is uploaded to this site and its URL stored.
 *
 * There is no field for a link to another site on purpose. The pages only load images
 * from this origin, so such a link saved fine and then showed nothing to learners, which
 * is how both live Task 1 prompts lost their charts.
 */
export default function ImageUploadField({ value, onChange, label = 'Image', id }) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);

  const handleFile = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const res = await adminApi.uploadImage(file);
      onChange(res.data.data.url);
    } catch (err) {
      setError(errorMessage(err, 'Upload failed'));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="image-upload-field">
      <label className="admin-form-label" style={{ fontSize: '0.8rem', display: 'block' }} htmlFor={id}>{label}</label>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <input id={id} type="file" accept="image/png,image/jpeg,image/webp" onChange={handleFile} disabled={uploading} />
        {uploading && <span className="spinner" />}
        {value && (
          <button type="button" className="btn btn-sm btn-outline" onClick={() => onChange('')}>Remove image</button>
        )}
      </div>
      {error && <p className="text-error" style={{ color: 'var(--color-danger, #ef4444)', fontSize: '0.8rem', margin: '4px 0 0' }}>{error}</p>}
      {value && (
        <div className="prompt-image-container" style={{ marginTop: 8 }}>
          <img src={value} alt="Uploaded" className="prompt-image" loading="lazy" style={{ maxWidth: '100%', maxHeight: 240 }} />
        </div>
      )}
    </div>
  );
}
