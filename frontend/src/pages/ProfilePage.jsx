import { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useTheme } from '../context/ThemeContext';
import authService from '../api/authService';
import analyticsApi from '../api/analyticsApi';
import styles from '../styles/Profile.module.css';

/** Bundled with the app, so it always resolves. */
const DEFAULT_AVATAR = '/assets/avatars/avatar_sarah.png';

export default function ProfilePage() {
  const { user, updateUser, logout } = useAuth();
  const { success, error } = useToast();
  const { theme: currentTheme, setTheme } = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();

  // Tab Control
  const activeTab = searchParams.get('tab') || 'personal';
  const setActiveTab = (tab) => {
    setSearchParams({ tab });
  };

  // --- TAB 1: Personal Info ---
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [savingPersonal, setSavingPersonal] = useState(false);
  const [avatarPreview, setAvatarPreview] = useState(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const fileInputRef = useRef(null);

  // Password fields
  const [currentPw, setCurrentPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);

  // --- TAB 2: Study Goals ---
  const [targetReading, setTargetReading] = useState(6.5);
  const [targetWriting, setTargetWriting] = useState(6.5);
  const [targetListening, setTargetListening] = useState(6.5);
  const [savingGoals, setSavingGoals] = useState(false);
  
  // Overview metrics (Current estimated band)
  const [overview, setOverview] = useState(null);
  const [loadingOverview, setLoadingOverview] = useState(false);
  const [overviewError, setOverviewError] = useState('');

  // --- TAB 3: Preferences ---
  // No language state: the app has no translations yet, so the control is rendered
  // disabled rather than pretending to switch anything. It stays on the page because the
  // feature is planned, and a disabled control with a reason beats a working-looking
  // dropdown that does nothing.
  const [notifications, setNotifications] = useState(true);
  const darkMode = currentTheme === 'dark';
  const [savingPrefs, setSavingPrefs] = useState(false);

  // --- Initial / Sync States ---
  useEffect(() => {
    if (user) {
      const parts = (user.displayName || '').split(' ');
      setFirstName(parts[0] || '');
      setLastName(parts.slice(1).join(' ') || '');
      setTargetReading(parseFloat(user.targetReadingScore) || 6.5);
      setTargetWriting(parseFloat(user.targetWritingScore) || 6.5);
      setTargetListening(parseFloat(user.targetListeningScore) || 6.5);
      setNotifications(user.emailNotifications !== false);
      setAvatarPreview(null); // Reset local preview when user changes
    }
  }, [user]);

  // Fetch estimated scores when Goals tab is loaded
  const loadOverview = () => {
    setLoadingOverview(true);
    setOverviewError('');
    analyticsApi.getOverview()
      .then(res => setOverview(res.data.data))
      .catch(err => {
        console.error('Error fetching analytics overview:', err);
        setOverviewError(err.response?.data?.message || err.message || 'Could not load your current estimated bands');
      })
      .finally(() => setLoadingOverview(false));
  };

  useEffect(() => {
    if (activeTab === 'goals' && user) loadOverview();
  }, [activeTab, user]);

  // Current Estimated scores
  const currentReading = overview?.averageScores?.READING || 0;
  const currentListening = overview?.averageScores?.LISTENING || 0;
  const currentWriting = overview?.averageScores?.WRITING || 0;

  // --- Dirty Checks ---
  const initialFirstName = user ? (user.displayName || '').split(' ')[0] || '' : '';
  const initialLastName = user ? (user.displayName || '').split(' ').slice(1).join(' ') || '' : '';
  const isPersonalDirty = firstName !== initialFirstName || lastName !== initialLastName;

  const isPasswordDirty = currentPw !== '' || newPw !== '';

  const initialReading = user ? parseFloat(user.targetReadingScore) || 6.5 : 6.5;
  const initialWriting = user ? parseFloat(user.targetWritingScore) || 6.5 : 6.5;
  const initialListening = user ? parseFloat(user.targetListeningScore) || 6.5 : 6.5;
  const isGoalsDirty = 
    parseFloat(targetReading) !== initialReading || 
    parseFloat(targetWriting) !== initialWriting || 
    parseFloat(targetListening) !== initialListening;

  // Dark mode applies the instant it is toggled and persists itself, so it is not part of
  // this form. The notification toggle is, and it compares against the saved account.
  const isPrefsDirty = notifications !== (user?.emailNotifications !== false);

  // --- Route Blocker for Unsaved Changes ---
  const isAnyFormDirty = isPersonalDirty || isPasswordDirty || isGoalsDirty || isPrefsDirty;

  useEffect(() => {
    if (!isAnyFormDirty) return;

    const handleGlobalClick = (e) => {
      const target = e.target.closest('a, button, [role="button"], .cursor-pointer');
      if (!target) return;

      const isLink = target.closest('a') && target.closest('a').getAttribute('href') && !target.closest('a').getAttribute('href').startsWith('#');
      const isBrand = target.closest('.flex-shrink-0') && (target.innerText?.includes('SmartPrep') || target.innerText?.includes('SP'));
      const isLogout = target.closest('#sidebar-logout-btn') || target.closest('#topbar-profile-btn') || target.innerText?.toLowerCase().includes('logout');

      if (isLink || isBrand || isLogout) {
        const proceed = window.confirm('You have unsaved changes. Are you sure you want to leave?');
        if (!proceed) {
          e.preventDefault();
          e.stopPropagation();
        }
      }
    };

    document.addEventListener('click', handleGlobalClick, true); // Capture phase to intercept navigation
    return () => document.removeEventListener('click', handleGlobalClick, true);
  }, [isAnyFormDirty]);

  // Handle unload event for tab closing/refresh
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (isAnyFormDirty) {
        e.preventDefault();
        e.returnValue = 'You have unsaved changes. Are you sure you want to leave?';
        return e.returnValue;
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isAnyFormDirty]);

  // --- Avatar Actions ---
  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleAvatarChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validation
    const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg'];
    if (!allowedTypes.includes(file.type)) {
      error('Only PNG or JPG images are allowed.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      error('File size must be under 5MB.');
      return;
    }

    // Set local preview
    const objectUrl = URL.createObjectURL(file);
    setAvatarPreview(objectUrl);
    setUploadingAvatar(true);

    try {
      const res = await authService.uploadAvatar(file);
      const { avatarUrl } = res.data.data;

      // Saved values, not the current form state. Sending the edited name here meant
      // uploading a picture also committed name changes the learner had not saved.
      await updateUser({
        displayName: user.displayName,
        avatarUrl,
        targetReadingScore: parseFloat(user.targetReadingScore) || 6.5,
        targetWritingScore: parseFloat(user.targetWritingScore) || 6.5,
        targetListeningScore: parseFloat(user.targetListeningScore) || 6.5
      });
      success('Profile picture updated successfully.');
    } catch (err) {
      error(err.message || 'Failed to upload profile picture.');
      setAvatarPreview(null); // revert preview on failure
    } finally {
      setUploadingAvatar(false);
    }
  };

  // --- Save Form Handlers ---
  const handleSavePersonal = async (e) => {
    e.preventDefault();
    if (!isPersonalDirty) return;
    setSavingPersonal(true);
    try {
      await updateUser({
        displayName: `${firstName} ${lastName}`.trim(),
        avatarUrl: user?.avatarUrl,
        targetReadingScore: parseFloat(targetReading),
        targetWritingScore: parseFloat(targetWriting),
        targetListeningScore: parseFloat(targetListening)
      });
      success('Personal information updated successfully.');
    } catch (err) {
      error(err.message || 'Failed to update personal information.');
    } finally {
      setSavingPersonal(false);
    }
  };

  const handleSavePassword = async (e) => {
    e.preventDefault();
    if (!isPasswordDirty) return;
    if (newPw.length < 6) {
      error('New password must be at least 6 characters.');
      return;
    }
    setSavingPassword(true);
    try {
      await authService.changePassword(currentPw, newPw);
      setCurrentPw('');
      setNewPw('');
      // The server blacklists the access token and revokes the refresh token on a password
      // change, so this session is already dead. Without logging out the page looked fine
      // and then every request failed with 401 until the user reloaded by hand.
      success('Password changed. Please sign in again with your new password.');
      setTimeout(() => logout(), 1200);
    } catch (err) {
      error(err.message || 'Failed to change password.');
    } finally {
      setSavingPassword(false);
    }
  };

  const handleSaveGoals = async (e) => {
    e.preventDefault();
    if (!isGoalsDirty) return;
    setSavingGoals(true);
    try {
      await updateUser({
        displayName: `${firstName} ${lastName}`.trim(),
        avatarUrl: user?.avatarUrl,
        targetReadingScore: parseFloat(targetReading),
        targetWritingScore: parseFloat(targetWriting),
        targetListeningScore: parseFloat(targetListening)
      });
      success('Study goals updated successfully.');
    } catch (err) {
      error(err.message || 'Failed to update study goals.');
    } finally {
      setSavingGoals(false);
    }
  };

  const handleSavePrefs = async (e) => {
    e.preventDefault();
    if (!isPrefsDirty) return;
    setSavingPrefs(true);

    const prevNotifs = user?.emailNotifications !== false;
    try {
      // A real save. This used to write localStorage, wait 500ms and report success, so
      // the setting was lost on every other device and the server never learned of it.
      await updateUser({
        displayName: user.displayName,
        avatarUrl: user?.avatarUrl,
        targetReadingScore: parseFloat(user.targetReadingScore) || 6.5,
        targetWritingScore: parseFloat(user.targetWritingScore) || 6.5,
        targetListeningScore: parseFloat(user.targetListeningScore) || 6.5,
        emailNotifications: notifications
      });
      success('Preferences saved successfully.');
    } catch (err) {
      setNotifications(prevNotifs);
      error(err.message || 'Failed to save preferences.');
    } finally {
      setSavingPrefs(false);
    }
  };

  const handleToggleDarkMode = (checked) => {
    // Immediately apply theme via context (persists to localStorage automatically)
    setTheme(checked ? 'dark' : 'light');
  };

  const avatarSrc = avatarPreview || (user?.avatarUrl
    ? (user.avatarUrl.startsWith('http') 
        ? user.avatarUrl 
        : (import.meta.env.VITE_API_URL || '/api/v1').replace('/api/v1', '') + user.avatarUrl)
    : DEFAULT_AVATAR);

  // --- Skeleton Screen Loading States ---
  if (!user) {
    return (
      <div className={styles.container}>
        <div className="animate-pulse space-y-lg py-6">
          <div className="h-10 bg-outline-variant/30 rounded w-1/3 mb-md"></div>
          <div className="h-6 bg-outline-variant/30 rounded w-1/2 mb-lg"></div>
          <div className="flex gap-md border-b border-outline-variant pb-xs">
            <div className="h-8 bg-outline-variant/30 rounded w-20"></div>
            <div className="h-8 bg-outline-variant/30 rounded w-20"></div>
            <div className="h-8 bg-outline-variant/30 rounded w-20"></div>
          </div>
          <div className="h-64 bg-outline-variant/20 rounded-2xl p-xl border border-outline-variant/40"></div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <h1 className={styles.title}>Profile &amp; Settings</h1>
        <p className={styles.subtitle}>Manage your account details and preparation preferences.</p>
      </div>

      {/* Tab Navigation */}
      <div className={styles['tabs-nav']}>
        <button 
          onClick={() => setActiveTab('personal')}
          className={`${styles['tab-btn']} ${activeTab === 'personal' ? styles.active : ''}`}
        >
          Personal Info
        </button>
        <button 
          onClick={() => setActiveTab('goals')}
          className={`${styles['tab-btn']} ${activeTab === 'goals' ? styles.active : ''}`}
        >
          Study Goals
        </button>
        <button 
          onClick={() => setActiveTab('prefs')}
          className={`${styles['tab-btn']} ${activeTab === 'prefs' ? styles.active : ''}`}
        >
          Preferences
        </button>
      </div>

      {/* Tab 1: Personal Info */}
      {activeTab === 'personal' && (
        <div className="animate-[fadeIn_0.3s_ease-in-out]">
          <div className={styles['settings-card']}>
            {/* Profile Picture Header */}
            <div className={styles['avatar-section']}>
              <div className={styles['avatar-wrapper']} onClick={handleAvatarClick}>
                {uploadingAvatar ? (
                  <div className="absolute inset-0 bg-black/60 flex items-center justify-center z-20">
                    <div className="spinner border-white border-t-transparent !mr-0"></div>
                  </div>
                ) : null}
                <img
                  alt="User avatar"
                  className={styles['avatar-img']}
                  src={avatarSrc}
                  onError={(e) => {
                    // Storage can lose an object and an old row can point at a file that is
                    // no longer there. Either way the learner should see the default
                    // picture rather than a broken-image icon.
                    if (!e.currentTarget.src.endsWith(DEFAULT_AVATAR)) {
                      e.currentTarget.src = DEFAULT_AVATAR;
                    }
                  }}
                />
                <div className={styles['avatar-overlay']}>
                  <span className={`material-symbols-outlined ${styles['avatar-icon']}`}>photo_camera</span>
                </div>
              </div>
              <input 
                type="file" 
                ref={fileInputRef} 
                onChange={handleAvatarChange} 
                accept="image/png, image/jpeg, image/jpg" 
                className="hidden" 
              />
              <div>
                <h2 className={styles['avatar-info-title']}>Profile Picture</h2>
                <p className={styles['avatar-info-desc']}>PNG, JPG under 5MB</p>
              </div>
            </div>

            {/* Profile Fields Form */}
            <form onSubmit={handleSavePersonal}>
              <div className={styles['form-grid']}>
                <div className={styles['form-group']}>
                  <label className={styles.label} htmlFor="profile-first-name">First Name</label>
                  <input
                    id="profile-first-name"
                    type="text"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    className={styles.input} 
                    required
                  />
                </div>
                <div className={styles['form-group']}>
                  <label className={styles.label} htmlFor="profile-last-name">Last Name</label>
                  <input
                    id="profile-last-name"
                    type="text"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    className={styles.input}
                    placeholder="Optional"
                  />
                </div>
                <div className={`${styles['form-group']} ${styles['full-width']}`}>
                  <label className={styles.label} htmlFor="profile-username">Username</label>
                  <input
                    id="profile-username"
                    type="text"
                    value={user.username || ''} 
                    disabled 
                    className={styles.input} 
                  />
                </div>
                <div className={`${styles['form-group']} ${styles['full-width']}`}>
                  <label className={styles.label} htmlFor="profile-email">Email Address</label>
                  <input
                    id="profile-email"
                    type="email"
                    value={user.email || ''} 
                    disabled 
                    className={styles.input} 
                  />
                </div>
              </div>

              <div className={styles['actions-row']}>
                <button 
                  type="submit" 
                  disabled={savingPersonal || !isPersonalDirty}
                  className={styles['btn-submit']}
                >
                  {savingPersonal ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>

            {/* Change Password Block */}
            <div className="pt-xl mt-xl border-t border-outline-variant">
              <h3 className="font-title-lg text-title-lg text-on-surface mb-md">Change Password</h3>
              <form onSubmit={handleSavePassword}>
                <div className="space-y-md">
                  <div className={styles['form-group']}>
                    <label className={styles.label} htmlFor="profile-current-password">Current Password</label>
                    <input
                      id="profile-current-password"
                      type="password"
                      value={currentPw}
                      onChange={(e) => setCurrentPw(e.target.value)}
                      placeholder="••••••••" 
                      className={styles.input} 
                      required
                    />
                  </div>
                  <div className={styles['form-group']}>
                    <label className={styles.label} htmlFor="profile-new-password">New Password</label>
                    <input
                      id="profile-new-password"
                      type="password"
                      value={newPw}
                      onChange={(e) => setNewPw(e.target.value)}
                      placeholder="••••••••" 
                      className={styles.input} 
                      required
                      minLength={6}
                    />
                  </div>
                </div>

                <div className={styles['actions-row']}>
                  <button 
                    type="submit" 
                    disabled={savingPassword || !isPasswordDirty}
                    className={styles['btn-submit']}
                  >
                    {savingPassword ? 'Changing...' : 'Change Password'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Study Goals */}
      {activeTab === 'goals' && (
        <div className="animate-[fadeIn_0.3s_ease-in-out]">
          <div className={styles['settings-card']}>
            <div className={styles['goals-header']}>
              <h2 className={styles['goals-title']}>IELTS Target Score</h2>
              <p className={styles['goals-desc']}>Set your target score to customize your learning path and mock test evaluations.</p>
            </div>

            {loadingOverview ? (
              <div className="animate-pulse space-y-md">
                <div className="h-28 bg-outline-variant/20 rounded-xl"></div>
                <div className="h-28 bg-outline-variant/20 rounded-xl"></div>
                <div className="h-28 bg-outline-variant/20 rounded-xl"></div>
              </div>
            ) : (
              <>
              {overviewError && (
                <div className="error-msg" role="alert">
                  <span>{overviewError} — the estimated bands below are not current.</span>
                  <button type="button" className="btn btn-outline" onClick={loadOverview}>Retry</button>
                </div>
              )}
              <form onSubmit={handleSaveGoals} className={styles['goals-stack']}>
                {/* Reading Target Slider */}
                <div className={`${styles['goal-slider-box']} ${styles.featured}`}>
                  {/* Current Estimated Score display (Read-only) */}
                  <div className="flex justify-between items-center mb-xs text-sm text-outline">
                    <span>Current Estimated Band</span>
                    <span className="font-semibold text-on-surface">{currentReading > 0 ? currentReading.toFixed(1) : 'No tests yet'}</span>
                  </div>
                  <div className="flex justify-between items-center mb-md">
                    <label className={styles['slider-label']} htmlFor="target-reading">Reading Band Target</label>
                    <span className={styles['slider-value']}>{parseFloat(targetReading).toFixed(1)}</span>
                  </div>
                  <input
                    id="target-reading"
                    type="range"
                    min="0"
                    max="9.0"
                    step="0.5"
                    value={targetReading}
                    onChange={(e) => setTargetReading(parseFloat(e.target.value))}
                    className={styles['range-input']} 
                  />
                  <div className={styles['slider-ticks']}>
                    <span>0</span><span>4.5</span><span>9.0</span>
                  </div>
                  {targetReading < currentReading && (
                    <div className="text-warning text-xs mt-sm flex items-center gap-xs font-semibold">
                      <span className="material-symbols-outlined text-[16px]">warning</span>
                      Warning: Target is lower than your current estimated score ({currentReading.toFixed(1)})
                    </div>
                  )}
                </div>

                {/* Writing Target Slider */}
                <div className={`${styles['goal-slider-box']} ${styles.featured}`}>
                  <div className="flex justify-between items-center mb-xs text-sm text-outline">
                    <span>Current Estimated Band</span>
                    <span className="font-semibold text-on-surface">{currentWriting > 0 ? currentWriting.toFixed(1) : 'No tests yet'}</span>
                  </div>
                  <div className="flex justify-between items-center mb-md">
                    <label className={styles['slider-label']} htmlFor="target-writing">Writing Band Target</label>
                    <span className={styles['slider-value']}>{parseFloat(targetWriting).toFixed(1)}</span>
                  </div>
                  <input
                    id="target-writing"
                    type="range"
                    min="0"
                    max="9.0"
                    step="0.5"
                    value={targetWriting}
                    onChange={(e) => setTargetWriting(parseFloat(e.target.value))}
                    className={styles['range-input']} 
                  />
                  <div className={styles['slider-ticks']}>
                    <span>0</span><span>4.5</span><span>9.0</span>
                  </div>
                  {targetWriting < currentWriting && (
                    <div className="text-warning text-xs mt-sm flex items-center gap-xs font-semibold">
                      <span className="material-symbols-outlined text-[16px]">warning</span>
                      Warning: Target is lower than your current estimated score ({currentWriting.toFixed(1)})
                    </div>
                  )}
                </div>

                {/* Listening Target Slider */}
                <div className={`${styles['goal-slider-box']} ${styles.featured}`}>
                  <div className="flex justify-between items-center mb-xs text-sm text-outline">
                    <span>Current Estimated Band</span>
                    <span className="font-semibold text-on-surface">{currentListening > 0 ? currentListening.toFixed(1) : 'No tests yet'}</span>
                  </div>
                  <div className="flex justify-between items-center mb-md">
                    <label className={styles['slider-label']} htmlFor="target-listening">Listening Band Target</label>
                    <span className={styles['slider-value']}>{parseFloat(targetListening).toFixed(1)}</span>
                  </div>
                  <input
                    id="target-listening"
                    type="range"
                    min="0"
                    max="9.0"
                    step="0.5"
                    value={targetListening}
                    onChange={(e) => setTargetListening(parseFloat(e.target.value))}
                    className={styles['range-input']} 
                  />
                  <div className={styles['slider-ticks']}>
                    <span>0</span><span>4.5</span><span>9.0</span>
                  </div>
                  {targetListening < currentListening && (
                    <div className="text-warning text-xs mt-sm flex items-center gap-xs font-semibold">
                      <span className="material-symbols-outlined text-[16px]">warning</span>
                      Warning: Target is lower than your current estimated score ({currentListening.toFixed(1)})
                    </div>
                  )}
                </div>

                <div className={styles['actions-row']}>
                  <button 
                    type="submit" 
                    disabled={savingGoals || !isGoalsDirty}
                    className={styles['btn-submit']}
                  >
                    {savingGoals ? 'Updating...' : 'Update Goals'}
                  </button>
                </div>
              </form>
              </>
            )}
          </div>
        </div>
      )}

      {/* Tab 3: Preferences */}
      {activeTab === 'prefs' && (
        <div className="animate-[fadeIn_0.3s_ease-in-out]">
          <form onSubmit={handleSavePrefs} className={styles['settings-card']}>
            <div className="space-y-xl">
              {/* Interface Language */}
              <div className={styles['pref-row']}>
                <div>
                  <h3 className={styles['pref-info-title']}>
                    Interface Language
                    <span className={styles['pref-badge']}>Coming soon</span>
                  </h3>
                  <p className={styles['pref-info-desc']}>
                    The interface is English only for now. Vietnamese is planned; this control
                    stays disabled until the translations ship.
                  </p>
                </div>
                <select
                  value="English (US)"
                  disabled
                  readOnly
                  aria-label="Interface language"
                  className="bg-surface border border-outline-variant text-on-surface font-body-lg text-body-lg rounded-lg px-md py-sm outline-none opacity-60 cursor-not-allowed"
                >
                  <option value="English (US)">English (US)</option>
                </select>
              </div>

              {/* Notifications Toggle */}
              <div className={styles['pref-row']}>
                <div>
                  <h3 className={styles['pref-info-title']}>Email Notifications</h3>
                  <p className={styles['pref-info-desc']}>
                    Saved to your account, so it follows you to any device. Progress emails
                    are not being sent yet; this records whether you want them.
                  </p>
                </div>
                <div className={styles['toggle-switch-wrapper']}>
                  <input
                    type="checkbox"
                    checked={notifications}
                    onChange={(e) => setNotifications(e.target.checked)}
                    id="notif-toggle"
                    aria-label="Email notifications"
                    className={styles['toggle-switch-input']}
                  />
                  <label 
                    htmlFor="notif-toggle"
                    className={styles['toggle-switch-slider']}
                  />
                </div>
              </div>

              {/* Dark Mode Toggle */}
              <div className={styles['pref-row']}>
                <div>
                  <h3 className={styles['pref-info-title']}>Dark Mode</h3>
                  <p className={styles['pref-info-desc']}>Switch to a darker theme for nighttime studying.</p>
                </div>
                <div className={styles['toggle-switch-wrapper']}>
                  <input
                    type="checkbox"
                    checked={darkMode}
                    onChange={(e) => handleToggleDarkMode(e.target.checked)}
                    id="dark-toggle"
                    aria-label="Dark mode"
                    className={styles['toggle-switch-input']}
                  />
                  <label 
                    htmlFor="dark-toggle"
                    className={styles['toggle-switch-slider']}
                  />
                </div>
              </div>
            </div>

            <div className={styles['actions-row']}>
              <button 
                type="submit" 
                disabled={savingPrefs || !isPrefsDirty}
                className={styles['btn-submit']}
              >
                {savingPrefs ? 'Saving...' : 'Save Preferences'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
