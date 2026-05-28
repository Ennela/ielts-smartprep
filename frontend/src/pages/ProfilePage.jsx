import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import authService from '../api/authService';

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [targetReading, setTargetReading] = useState(user?.targetReadingScore || 6.5);
  const [targetWriting, setTargetWriting] = useState(user?.targetWritingScore || 6.5);
  const [targetListening, setTargetListening] = useState(user?.targetListeningScore || 6.5);
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  // Password change
  const [currentPw, setCurrentPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [pwLoading, setPwLoading] = useState(false);
  const [pwMsg, setPwMsg] = useState('');
  const [pwError, setPwError] = useState('');

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      await updateUser({
        displayName,
        targetReadingScore: parseFloat(targetReading),
        targetWritingScore: parseFloat(targetWriting),
        targetListeningScore: parseFloat(targetListening),
      });
      setSuccess('Profile updated successfully.');
    } catch (err) {
      setError(err.message || 'Failed to update profile.');
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (newPw.length < 6) {
      setPwError('New password must be at least 6 characters.');
      return;
    }
    setPwLoading(true);
    setPwError('');
    setPwMsg('');
    try {
      await authService.changePassword(currentPw, newPw);
      setPwMsg('Password changed successfully.');
      setCurrentPw('');
      setNewPw('');
    } catch (err) {
      setPwError(err.message || 'Failed to change password.');
    } finally {
      setPwLoading(false);
    }
  };

  return (
    <div className="profile-content">
      <h1 className="page-title reveal">Profile Settings</h1>
      <p className="subtitle reveal reveal-delay-1">Manage your account and target scores</p>

      {/* Profile Info */}
      <form className="card profile-card reveal reveal-delay-2" onSubmit={handleSaveProfile}>
        <h2 className="profile-section-title">Account Information</h2>

        <div className="profile-field">
          <label>Email</label>
          <input type="text" value={user?.email || ''} disabled className="profile-input disabled" />
        </div>

        <div className="profile-field">
          <label>Username</label>
          <input type="text" value={user?.username || ''} disabled className="profile-input disabled" />
        </div>

        <div className="profile-field">
          <label>Display Name</label>
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="profile-input"
            id="profile-display-name"
          />
        </div>

        <h2 className="profile-section-title" style={{ marginTop: 32 }}>Target Scores</h2>

        <div className="target-scores-grid">
          <div className="profile-field">
            <label>Reading Target</label>
            <select value={targetReading} onChange={(e) => setTargetReading(e.target.value)} className="profile-input" id="target-reading">
              {[5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0].map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
          <div className="profile-field">
            <label>Writing Target</label>
            <select value={targetWriting} onChange={(e) => setTargetWriting(e.target.value)} className="profile-input" id="target-writing">
              {[5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0].map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
          <div className="profile-field">
            <label>Listening Target</label>
            <select value={targetListening} onChange={(e) => setTargetListening(e.target.value)} className="profile-input" id="target-listening">
              {[5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0].map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>

        {error && <div className="error-msg">{error}</div>}
        {success && <div className="success-msg">{success}</div>}

        <button type="submit" className="btn btn-primary" disabled={saving} id="save-profile-btn">
          {saving ? <><span className="spinner"></span> Saving...</> : 'Save Changes'}
        </button>
      </form>

      {/* Password Change */}
      <form className="card profile-card reveal reveal-delay-3" onSubmit={handleChangePassword}>
        <h2 className="profile-section-title">Change Password</h2>

        <div className="profile-field">
          <label>Current Password</label>
          <input
            type="password"
            value={currentPw}
            onChange={(e) => setCurrentPw(e.target.value)}
            className="profile-input"
            required
            id="current-password"
          />
        </div>

        <div className="profile-field">
          <label>New Password</label>
          <input
            type="password"
            value={newPw}
            onChange={(e) => setNewPw(e.target.value)}
            className="profile-input"
            required
            minLength={6}
            id="new-password"
          />
        </div>

        {pwError && <div className="error-msg">{pwError}</div>}
        {pwMsg && <div className="success-msg">{pwMsg}</div>}

        <button type="submit" className="btn btn-outline" disabled={pwLoading} id="change-password-btn">
          {pwLoading ? 'Changing...' : 'Change Password'}
        </button>
      </form>
    </div>
  );
}
