import { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useTheme } from '../context/ThemeContext';
import authService from '../api/authService';
import axiosClient from '../api/axiosClient';
import styles from '../styles/Profile.module.css';

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
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

  // --- TAB 3: Preferences ---
  const [language, setLanguage] = useState('English (US)');
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
      setAvatarPreview(null); // Reset local preview when user changes
    }
  }, [user]);

  // Load preferences from localStorage on mount
  useEffect(() => {
    const savedLang = localStorage.getItem('pref_lang');
    if (savedLang) setLanguage(savedLang);
    const savedNotifs = localStorage.getItem('pref_notifications');
    if (savedNotifs !== null) setNotifications(savedNotifs !== 'false');
  }, []);

  // Fetch estimated scores when Goals tab is loaded
  useEffect(() => {
    if (activeTab === 'goals' && user) {
      setLoadingOverview(true);
      axiosClient.get('/analytics/overview')
        .then(res => setOverview(res.data.data))
        .catch(err => console.error('Error fetching analytics overview:', err))
        .finally(() => setLoadingOverview(false));
    }
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

  const initialLang = localStorage.getItem('pref_lang') || 'English (US)';
  const initialNotifs = localStorage.getItem('pref_notifications') !== 'false';
  const isPrefsDirty = 
    language !== initialLang || 
    notifications !== initialNotifs;

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

      // Update the user profile with the new avatar url
      await updateUser({
        displayName: `${firstName} ${lastName}`.trim(),
        avatarUrl,
        targetReadingScore: parseFloat(targetReading),
        targetWritingScore: parseFloat(targetWriting),
        targetListeningScore: parseFloat(targetListening)
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
      success('Password changed successfully.');
      setCurrentPw('');
      setNewPw('');
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

    const prevNotifs = localStorage.getItem('pref_notifications') !== 'false';
    
    // Optimistic UI state updates for notifications
    try {
      // Save local preferences
      localStorage.setItem('pref_lang', language);
      localStorage.setItem('pref_notifications', notifications.toString());

      // Simulate a backend update for notifications using optimistic UI state
      await new Promise((resolve) => {
        setTimeout(() => {
          resolve(true);
        }, 500);
      });

      success('Preferences saved successfully.');
    } catch (err) {
      // Revert optimistic state on failure
      setNotifications(prevNotifs);
      localStorage.setItem('pref_notifications', prevNotifs.toString());
      error('Failed to sync notification settings with backend.');
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
        : (import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1').replace('/api/v1', '') + user.avatarUrl)
    : '/assets/avatars/avatar_sarah.png');

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
                  <label className={styles.label}>First Name</label>
                  <input 
                    type="text" 
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    className={styles.input} 
                    required
                  />
                </div>
                <div className={styles['form-group']}>
                  <label className={styles.label}>Last Name</label>
                  <input 
                    type="text" 
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    className={styles.input} 
                    required
                  />
                </div>
                <div className={`${styles['form-group']} ${styles['full-width']}`}>
                  <label className={styles.label}>Username</label>
                  <input 
                    type="text" 
                    value={user.username || ''} 
                    disabled 
                    className={styles.input} 
                  />
                </div>
                <div className={`${styles['form-group']} ${styles['full-width']}`}>
                  <label className={styles.label}>Email Address</label>
                  <input 
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
                    <label className={styles.label}>Current Password</label>
                    <input 
                      type="password" 
                      value={currentPw}
                      onChange={(e) => setCurrentPw(e.target.value)}
                      placeholder="••••••••" 
                      className={styles.input} 
                      required
                    />
                  </div>
                  <div className={styles['form-group']}>
                    <label className={styles.label}>New Password</label>
                    <input 
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
              <form onSubmit={handleSaveGoals} className={styles['goals-stack']}>
                {/* Reading Target Slider */}
                <div className={`${styles['goal-slider-box']} ${styles.featured}`}>
                  {/* Current Estimated Score display (Read-only) */}
                  <div className="flex justify-between items-center mb-xs text-sm text-outline">
                    <span>Current Estimated Band</span>
                    <span className="font-semibold text-on-surface">{currentReading > 0 ? currentReading.toFixed(1) : 'No tests yet'}</span>
                  </div>
                  <div className="flex justify-between items-center mb-md">
                    <label className={styles['slider-label']}>Reading Band Target</label>
                    <span className={styles['slider-value']}>{parseFloat(targetReading).toFixed(1)}</span>
                  </div>
                  <input 
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
                    <label className={styles['slider-label']}>Writing Band Target</label>
                    <span className={styles['slider-value']}>{parseFloat(targetWriting).toFixed(1)}</span>
                  </div>
                  <input 
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
                    <label className={styles['slider-label']}>Listening Band Target</label>
                    <span className={styles['slider-value']}>{parseFloat(targetListening).toFixed(1)}</span>
                  </div>
                  <input 
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
                  <h3 className={styles['pref-info-title']}>Interface Language</h3>
                  <p className={styles['pref-info-desc']}>Choose your preferred language for the dashboard.</p>
                </div>
                <select 
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                  className="bg-surface border border-outline-variant text-on-surface font-body-lg text-body-lg rounded-lg px-md py-sm focus:border-primary focus:ring-1 focus:ring-primary outline-none cursor-pointer"
                >
                  <option value="English (US)">English (US)</option>
                  <option value="English (UK)">English (UK)</option>
                  <option value="Vietnamese">Vietnamese</option>
                </select>
              </div>

              {/* Notifications Toggle */}
              <div className={styles['pref-row']}>
                <div>
                  <h3 className={styles['pref-info-title']}>Email Notifications</h3>
                  <p className={styles['pref-info-desc']}>Receive weekly progress reports and study reminders.</p>
                </div>
                <div className={styles['toggle-switch-wrapper']}>
                  <input 
                    type="checkbox"
                    checked={notifications}
                    onChange={(e) => setNotifications(e.target.checked)}
                    id="notif-toggle"
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
