import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMockTest } from '../context/MockTestContext';
import mockTestApi from '../api/mockTestApi';
import styles from '../styles/MockTest.module.css';

export default function MockTestLobbyPage() {
  const navigate = useNavigate();
  const { activeSession, startOrResumeTest, loadActiveSession, clearSession } = useMockTest();
  const [tests, setTests] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // Wizard state
  const [selectedTestForSetup, setSelectedTestForSetup] = useState(null);
  const [setupStep, setSetupStep] = useState(1); // 1, 2, 3
  const [audioTesting, setAudioTesting] = useState(false);
  const [isTermsChecked, setIsTermsChecked] = useState(false);
  const audioRef = useRef(null);

  useEffect(() => {
    // Load active session on mount
    loadActiveSession().catch(() => {});
    
    // Load available mock tests
    setLoading(true);
    mockTestApi.getAllMockTests()
      .then(res => {
        setTests(res.data?.data || []);
      })
      .catch(err => {
        console.error('Failed to load mock tests', err);
      })
      .finally(() => setLoading(false));

    // Load attempt history
    setHistoryLoading(true);
    mockTestApi.getHistory()
      .then(res => {
        setHistory(res.data?.data || []);
      })
      .catch(err => {
        console.error('Failed to load history', err);
      })
      .finally(() => setHistoryLoading(false));
  }, []);

  useEffect(() => {
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
      }
    };
  }, []);

  const handleStartTest = async (testId) => {
    try {
      setActionLoading(true);
      await startOrResumeTest(testId);
      // Retrieve the current session to get the ID
      const session = await loadActiveSession();
      if (session) {
        navigate(`/mock-tests/take/${session.sessionId}`);
      }
    } catch (err) {
      alert(err.message || 'Failed to start test');
    } finally {
      setActionLoading(false);
    }
  };

  const handleResumeActive = () => {
    if (activeSession) {
      navigate(`/mock-tests/take/${activeSession.sessionId}`);
    }
  };

  const handleCancelActive = () => {
    if (window.confirm('Are you sure you want to abandon this mock test? Your progress will be lost.')) {
      clearSession();
    }
  };

  const handleExitSetup = () => {
    if (audioRef.current) {
      audioRef.current.pause();
    }
    setSelectedTestForSetup(null);
    setSetupStep(1);
    setAudioTesting(false);
    setIsTermsChecked(false);
  };

  const toggleTestAudio = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audioTesting) {
      audio.pause();
      setAudioTesting(false);
    } else {
      audio.play().catch(err => console.log('Audio play failed', err));
      setAudioTesting(true);
    }
  };

  // Compute quick statistics
  const completedHistory = history.filter(h => h.status === 'COMPLETED');
  const avgBand = completedHistory.length > 0 
    ? (completedHistory.reduce((acc, h) => acc + (h.overallBand || 0), 0) / completedHistory.length).toFixed(1)
    : '—';

  // WIZARD VIEW RENDERING
  if (selectedTestForSetup) {
    return (
      <div className={styles.container}>
        <style>{`
          .setup-card {
            background: var(--surface-container-lowest);
            border: 1px solid var(--outline-variant);
            border-radius: var(--radius-xl);
            padding: 32px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.04);
            margin-top: 24px;
            display: flex;
            flex-direction: column;
            gap: 24px;
          }
          .wizard-stepper {
            display: flex;
            justify-content: space-between;
            align-items: center;
            max-width: 600px;
            margin: 0 auto 32px auto;
            position: relative;
            width: 100%;
          }
          .wizard-step {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            z-index: 2;
            background: var(--surface-container-lowest);
            padding: 0 16px;
            opacity: 0.5;
            transition: opacity 0.3s;
          }
          .wizard-step.active {
            opacity: 1;
            font-weight: 700;
          }
          .wizard-step-num {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: var(--surface-container-high);
            color: var(--on-surface-variant);
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            border: 2px solid var(--outline-variant);
            transition: all 0.3s;
          }
          .wizard-step.active .wizard-step-num {
            background: var(--primary);
            color: var(--on-primary);
            border-color: var(--primary);
            box-shadow: 0 0 12px rgba(0, 49, 120, 0.3);
          }
          .wizard-step.completed .wizard-step-num {
            background: var(--secondary);
            color: var(--on-secondary);
            border-color: var(--secondary);
          }
          .wizard-line {
            position: absolute;
            top: 20px;
            left: 48px;
            right: 48px;
            height: 2px;
            background: var(--outline-variant);
            z-index: 1;
            opacity: 0.3;
          }
          .soundwave {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 4px;
            height: 24px;
            margin-top: 8px;
          }
          .soundwave-bar {
            width: 3px;
            height: 100%;
            background-color: var(--primary);
            border-radius: 2px;
          }
          .soundwave-bar.bar-1 { animation: soundwave-bounce 0.6s ease-in-out infinite alternate; animation-delay: 0.1s; }
          .soundwave-bar.bar-2 { animation: soundwave-bounce 0.7s ease-in-out infinite alternate; animation-delay: 0.3s; }
          .soundwave-bar.bar-3 { animation: soundwave-bounce 0.5s ease-in-out infinite alternate; animation-delay: 0.5s; }
          .soundwave-bar.bar-4 { animation: soundwave-bounce 0.8s ease-in-out infinite alternate; animation-delay: 0.2s; }
          .soundwave-bar.bar-5 { animation: soundwave-bounce 0.6s ease-in-out infinite alternate; animation-delay: 0.4s; }
          @keyframes soundwave-bounce {
            0% { height: 4px; }
            100% { height: 24px; }
          }
        `}</style>

        {/* Setup Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
          <button 
            className="btn btn-outline"
            style={{ padding: '8px 16px', display: 'flex', alignItems: 'center', gap: '8px' }}
            onClick={handleExitSetup}
          >
            <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>arrow_back</span>
            Back to Lobby
          </button>
          <div>
            <h1 className={styles.title} style={{ margin: 0, fontSize: '1.8rem' }}>{selectedTestForSetup.title} Setup</h1>
            <p className={styles.subtitle} style={{ margin: '4px 0 0 0' }}>Configure and verify your environment before starting.</p>
          </div>
        </div>

        {/* Stepper progress */}
        <div className="setup-card" style={{ marginBottom: '24px', padding: '24px' }}>
          <div className="wizard-stepper">
            <div className="wizard-line"></div>
            <div className={`wizard-step ${setupStep === 1 ? 'active' : ''} ${setupStep > 1 ? 'completed' : ''}`}>
              <div className="wizard-step-num">
                {setupStep > 1 ? <span className="material-symbols-outlined" style={{ fontSize: 20 }}>check</span> : '1'}
              </div>
              <span style={{ fontSize: '0.85rem' }}>Step 1: Setup</span>
            </div>
            <div className={`wizard-step ${setupStep === 2 ? 'active' : ''} ${setupStep > 2 ? 'completed' : ''}`}>
              <div className="wizard-step-num">
                {setupStep > 2 ? <span className="material-symbols-outlined" style={{ fontSize: 20 }}>check</span> : '2'}
              </div>
              <span style={{ fontSize: '0.85rem' }}>Step 2: System Check</span>
            </div>
            <div className={`wizard-step ${setupStep === 3 ? 'active' : ''}`}>
              <div className="wizard-step-num">3</div>
              <span style={{ fontSize: '0.85rem' }}>Step 3: Begin Exam</span>
            </div>
          </div>
        </div>

        {/* Step Content */}
        {setupStep === 1 && (
          <div className="setup-card" style={{ animation: 'fadeIn 0.4s' }}>
            <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: 0 }}>Step 1: Test Overview & Durations</h2>
            <p style={{ color: 'var(--on-surface-variant)', fontSize: '0.92rem', margin: 0 }}>
              This test consists of three continuous sections mimicking the official computer-based IELTS format.
            </p>

            <div className={styles['skills-grid']} style={{ marginTop: '12px' }}>
              <div className={styles['skill-card']}>
                <div className={styles['skill-indicator']} style={{ backgroundColor: '#003178' }}></div>
                <div className={styles['skill-icon-wrapper']} style={{ backgroundColor: 'rgba(0, 49, 120, 0.1)', color: '#003178' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>headset</span>
                </div>
                <h3 className={styles['skill-title']}>Listening</h3>
                <p className={styles['skill-desc']}>{selectedTestForSetup.listeningPartsCount || 4} Parts • 40 Questions</p>
                <div className={styles['skill-footer']}>
                  <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>schedule</span>
                  <span>30 Minutes</span>
                </div>
              </div>

              <div className={styles['skill-card']}>
                <div className={styles['skill-indicator']} style={{ backgroundColor: '#005faf' }}></div>
                <div className={styles['skill-icon-wrapper']} style={{ backgroundColor: 'rgba(0, 95, 175, 0.1)', color: '#005faf' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>menu_book</span>
                </div>
                <h3 className={styles['skill-title']}>Reading</h3>
                <p className={styles['skill-desc']}>{selectedTestForSetup.readingQuizzesCount || 3} Passages • 40 Questions</p>
                <div className={styles['skill-footer']}>
                  <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>schedule</span>
                  <span>60 Minutes</span>
                </div>
              </div>

              <div className={styles['skill-card']}>
                <div className={styles['skill-indicator']} style={{ backgroundColor: '#853100' }}></div>
                <div className={styles['skill-icon-wrapper']} style={{ backgroundColor: 'rgba(133, 49, 0, 0.1)', color: '#853100' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>edit_note</span>
                </div>
                <h3 className={styles['skill-title']}>Writing</h3>
                <p className={styles['skill-desc']}>{selectedTestForSetup.writingPromptsCount || 2} Tasks</p>
                <div className={styles['skill-footer']}>
                  <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>schedule</span>
                  <span>60 Minutes</span>
                </div>
              </div>
            </div>

            <div className={styles['info-grid']} style={{ marginTop: '12px' }}>
              <div className={styles['info-card']}>
                <div className={styles['info-header']}>
                  <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>info</span>
                  <h3 className={styles['info-title']}>Key Instructions</h3>
                </div>
                <ul style={{ paddingLeft: '20px', margin: 0, fontSize: '0.9rem', color: 'var(--on-surface-variant)', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <li>You must complete all three sections sequentially (Listening &rarr; Reading &rarr; Writing).</li>
                  <li>Once started, the overall test timer will count down and cannot be paused.</li>
                  <li>Answers are auto-saved in the background every 30 seconds.</li>
                </ul>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '16px' }}>
              <button className="btn btn-primary" onClick={() => setSetupStep(2)} style={{ padding: '12px 32px' }}>
                Proceed to System Check &rarr;
              </button>
            </div>
          </div>
        )}

        {setupStep === 2 && (
          <div className="setup-card" style={{ animation: 'fadeIn 0.4s' }}>
            <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: 0 }}>Step 2: Hardware & Network System Check</h2>
            <p style={{ color: 'var(--on-surface-variant)', fontSize: '0.92rem', margin: 0 }}>
              Verify your headphones and system latency below to prevent any disruption during the exam.
            </p>

            <div className={styles['info-grid']} style={{ marginTop: '12px' }}>
              {/* Headphone audio test */}
              <div className={styles['info-card']} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div className={styles['info-header']} style={{ marginBottom: 0 }}>
                  <span className="material-symbols-outlined" style={{ color: 'var(--secondary)' }}>headphones</span>
                  <h3 className={styles['info-title']}>Headphone & Volume Test</h3>
                </div>
                <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--on-surface-variant)' }}>
                  Put on your headphones and play the sample audio below. Adjust your computer volume to a comfortable level.
                </p>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', background: 'var(--surface-container-low)', padding: '16px', borderRadius: '12px' }}>
                  <button 
                    onClick={toggleTestAudio}
                    className="btn btn-primary"
                    style={{ borderRadius: '50%', width: '48px', height: '48px', minWidth: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0 }}
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: 28 }}>
                      {audioTesting ? 'pause' : 'play_arrow'}
                    </span>
                  </button>
                  <div style={{ flex: 1 }}>
                    <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--on-surface)' }}>
                      {audioTesting ? 'Playing sample audio...' : 'Test Sound Track'}
                    </span>
                    {audioTesting && (
                      <div className="soundwave">
                        <div className="soundwave-bar bar-1"></div>
                        <div className="soundwave-bar bar-2"></div>
                        <div className="soundwave-bar bar-3"></div>
                        <div className="soundwave-bar bar-4"></div>
                        <div className="soundwave-bar bar-5"></div>
                      </div>
                    )}
                  </div>
                  <audio ref={audioRef} src="https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3" loop />
                </div>
              </div>

              {/* Technical checks */}
              <div className={styles['info-card']} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div className={styles['info-header']} style={{ marginBottom: 0 }}>
                  <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>settings_suggest</span>
                  <h3 className={styles['info-title']}>System & Browser Compatibility</h3>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.85rem', borderBottom: '1px solid var(--outline-variant)', pb: '8px', paddingBottom: '8px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--on-surface-variant)' }}>
                      <span className="material-symbols-outlined" style={{ color: '#00875a', fontSize: '18px' }}>check_circle</span>
                      Browser check
                    </span>
                    <span style={{ fontWeight: 600 }}>Google Chrome / Edge Compatible</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.85rem', borderBottom: '1px solid var(--outline-variant)', pb: '8px', paddingBottom: '8px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--on-surface-variant)' }}>
                      <span className="material-symbols-outlined" style={{ color: '#00875a', fontSize: '18px' }}>check_circle</span>
                      Network check
                    </span>
                    <span style={{ fontWeight: 600 }}>Excellent (Latency &lt; 50ms)</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Checkbox verification */}
            <div style={{ background: 'var(--surface-container-low)', padding: '16px 20px', borderRadius: '12px', marginTop: '12px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '12px', cursor: 'pointer', userSelect: 'none' }}>
                <input 
                  type="checkbox" 
                  checked={isTermsChecked}
                  onChange={(e) => setIsTermsChecked(e.target.checked)}
                  style={{ width: '20px', height: '20px', accentColor: 'var(--primary)', cursor: 'pointer' }}
                />
                <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--on-surface)' }}>
                  I confirm that my sound/headphones are working perfectly and that I am ready to start the test in a quiet environment.
                </span>
              </label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '16px' }}>
              <button className="btn btn-outline" onClick={() => { if(audioRef.current) audioRef.current.pause(); setAudioTesting(false); setSetupStep(1); }} style={{ padding: '12px 24px' }}>
                &larr; Back
              </button>
              <button 
                className="btn btn-primary" 
                disabled={!isTermsChecked}
                onClick={() => { if(audioRef.current) audioRef.current.pause(); setAudioTesting(false); setSetupStep(3); }} 
                style={{ padding: '12px 32px' }}
              >
                Proceed to Confirmation &rarr;
              </button>
            </div>
          </div>
        )}

        {setupStep === 3 && (
          <div className="setup-card" style={{ animation: 'fadeIn 0.4s', border: '1px solid var(--error-container)' }}>
            <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-start' }}>
              <div style={{ background: 'var(--error-container)', color: 'var(--on-error-container)', padding: '16px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span className="material-symbols-outlined" style={{ fontSize: 36 }}>warning</span>
              </div>
              <div style={{ flex: 1 }}>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: '0 0 8px 0', color: 'var(--error)' }}>
                  Important Warning Before Starting
                </h2>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '0.92rem', color: 'var(--on-surface-variant)' }}>
                  <p>
                    <strong>1. Continuous Assessment:</strong> Once you click the "Begin Exam" button, the overall exam timer starts. The timer is server-authoritative and <strong>cannot be paused</strong> under any circumstances.
                  </p>
                  <p>
                    <strong>2. Tab Reload/Exit:</strong> Navigating away from this page or closing your browser tab will not stop the timer. It will continue running on the server, and the test will auto-submit when the duration runs out.
                  </p>
                  <p>
                    <strong>3. Single Attempt:</strong> Ensure you have approximately <strong>2.5 hours of uninterrupted time</strong> to complete the exam.
                  </p>
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '16px', borderTop: '1px solid var(--outline-variant)', pt: '16px', paddingTop: '16px' }}>
              <button className="btn btn-outline" onClick={() => setSetupStep(2)} style={{ padding: '12px 24px' }}>
                &larr; Back to Check
              </button>
              <button 
                className="btn btn-primary" 
                style={{ background: 'var(--error)', borderColor: 'var(--error)', color: 'var(--on-primary)', padding: '12px 36px', fontWeight: 700 }}
                onClick={() => handleStartTest(selectedTestForSetup.mockTestId)}
                disabled={actionLoading}
              >
                {actionLoading ? 'Loading Exam...' : 'Begin Exam'}
              </button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // STANDARD LOBBY VIEW RENDERING
  return (
    <div className={styles.container}>
      {/* Header Section */}
      <header className={styles.header}>
        <h1 className={styles.title}>IELTS Mock Test Full Flow</h1>
        <p className={styles.subtitle}>Prepare for the real exam environment. Complete all three sections continuously to get an accurate band score estimate.</p>
      </header>

      {/* Stepper */}
      <div className={styles['stepper-card']}>
        <div className={styles['stepper-steps']}>
          {/* Step 1 (Active/Current) */}
          <div className={`${styles['stepper-step']} ${styles.active}`}>
            <div className={styles['step-num']}>1</div>
            <span className={styles['step-label']}>Setup</span>
          </div>
          {/* Step 2 (Upcoming) */}
          <div className={styles['stepper-step']}>
            <div className={styles['step-num']}>2</div>
            <span className={styles['step-label']}>System Check</span>
          </div>
          {/* Step 3 (Upcoming) */}
          <div className={styles['stepper-step']}>
            <div className={styles['step-num']}>3</div>
            <span className={styles['step-label']}>Test Execution</span>
          </div>
          {/* Step 4 (Upcoming) */}
          <div className={styles['stepper-step']}>
            <div className={styles['step-num']}>4</div>
            <span className={styles['step-label']}>Results</span>
          </div>
        </div>
      </div>

      {/* Active Session Notification */}
      {activeSession && (
        <div 
          className="animate-fade-in" 
          style={{
            borderLeft: '4px solid var(--tertiary-container)',
            background: 'var(--surface-container-low)',
            padding: '24px',
            borderRadius: '12px',
            marginBottom: '32px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '16px'
          }}
        >
          <div>
            <h3 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--tertiary-container)', margin: 0, fontWeight: 700 }}>
              <span className="material-symbols-outlined">hourglass_empty</span>
              Test in Progress
            </h3>
            <p style={{ fontSize: '0.9rem', color: 'var(--on-surface-variant)', marginTop: '6px', marginBottom: 0 }}>
              You have an active session for <strong>{activeSession.title}</strong>, currently at the <strong>{activeSession.currentSection}</strong> section.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '12px' }}>
            <button className="btn btn-primary" onClick={handleResumeActive}>
              Resume Exam
            </button>
            <button className="btn btn-outline" style={{ color: 'var(--error)', borderColor: 'var(--error)' }} onClick={handleCancelActive}>
              Abandon Exam
            </button>
          </div>
        </div>
      )}

      {/* Journey Overview Card (Bento Grid Style) */}
      <div className={styles['skills-grid']}>
        {/* Reading Section */}
        <div className={styles['skill-card']}>
          <div className={styles['skill-indicator']} style={{ backgroundColor: '#005faf' }}></div>
          <div className={styles['skill-icon-wrapper']} style={{ backgroundColor: 'rgba(0, 95, 175, 0.1)', color: '#005faf' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>menu_book</span>
          </div>
          <h3 className={styles['skill-title']}>Reading</h3>
          <p className={styles['skill-desc']}>3 Passages • 40 Questions</p>
          <div className={styles['skill-footer']}>
            <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>schedule</span>
            <span>60 Minutes</span>
          </div>
        </div>

        {/* Listening Section */}
        <div className={styles['skill-card']}>
          <div className={styles['skill-indicator']} style={{ backgroundColor: '#003178' }}></div>
          <div className={styles['skill-icon-wrapper']} style={{ backgroundColor: 'rgba(0, 49, 120, 0.1)', color: '#003178' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>headset</span>
          </div>
          <h3 className={styles['skill-title']}>Listening</h3>
          <p className={styles['skill-desc']}>4 Parts • 40 Questions</p>
          <div className={styles['skill-footer']}>
            <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>schedule</span>
            <span>30 Minutes</span>
          </div>
        </div>

        {/* Writing Section */}
        <div className={styles['skill-card']}>
          <div className={styles['skill-indicator']} style={{ backgroundColor: '#853100' }}></div>
          <div className={styles['skill-icon-wrapper']} style={{ backgroundColor: 'rgba(133, 49, 0, 0.1)', color: '#853100' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>edit_note</span>
          </div>
          <h3 className={styles['skill-title']}>Writing</h3>
          <p className={styles['skill-desc']}>2 Tasks</p>
          <div className={styles['skill-footer']}>
            <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>schedule</span>
            <span>60 Minutes</span>
          </div>
        </div>
      </div>

      {/* Two Column Information (Requirements & Instructions) */}
      <div className={styles['info-grid']}>
        {/* Requirements */}
        <div className={styles['info-card']}>
          <div className={styles['info-header']}>
            <span className="material-symbols-outlined" style={{ color: 'var(--error)' }}>verified_user</span>
            <h3 className={styles['info-title']}>System Requirements</h3>
          </div>
          <ul className={styles['requirements-list']}>
            <li className={styles['requirement-item']}>
              <span className="material-symbols-outlined" style={{ color: 'var(--secondary)', fontSize: '18px' }}>check_circle</span>
              <div>
                <strong className={styles['req-name']}>Stable Internet Connection</strong>
                <span className={styles['req-desc']}>At least 5 Mbps recommended to prevent interruptions.</span>
              </div>
            </li>
            <li className={styles['requirement-item']}>
              <span className="material-symbols-outlined" style={{ color: 'var(--secondary)', fontSize: '18px' }}>check_circle</span>
              <div>
                <strong className={styles['req-name']}>Working Audio</strong>
                <span className={styles['req-desc']}>Headphones required for the Listening section.</span>
              </div>
            </li>
            <li className={styles['requirement-item']}>
              <span className="material-symbols-outlined" style={{ color: 'var(--secondary)', fontSize: '18px' }}>check_circle</span>
              <div>
                <strong className={styles['req-name']}>Quiet Environment</strong>
                <span className={styles['req-desc']}>Ensure you will not be disturbed for the next 2.5 hours.</span>
              </div>
            </li>
          </ul>
        </div>

        {/* Instructions */}
        <div className={styles['info-card']}>
          <div className={styles['info-header']}>
            <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>info</span>
            <h3 className={styles['info-title']}>Test Instructions</h3>
          </div>
          <ol className={styles['instructions-list']}>
            <li>The test mimics the official computer-delivered IELTS format.</li>
            <li>Once started, the timer cannot be paused. Ensure you are ready to commit the full duration.</li>
            <li>You can navigate between questions within a section, but cannot return to a section once submitted.</li>
            <li>Answers are auto-saved every 30 seconds.</li>
          </ol>
        </div>
      </div>

      {/* Test List */}
      <h2 className={styles['section-title']}>Available Full Mock Tests</h2>
      {loading ? (
        <div className="loading-spinner" style={{ margin: '32px auto' }}>
          <span className="spinner" />
          <span>Loading mock tests...</span>
        </div>
      ) : (
        <div className={styles['tests-grid']}>
          {tests.map(test => (
            <div key={test.mockTestId} className={styles['test-card']}>
              <div>
                <div className={styles['test-header']}>
                  <h3 className={styles['test-title']}>{test.title}</h3>
                  <span className={styles['difficulty-badge']}>{test.difficulty}</span>
                </div>
                <p className={styles['test-desc']}>
                  {test.description || 'Full academic simulation including all question types.'}
                </p>
                <div className={styles['test-metadata']}>
                  <span className={styles['metadata-item']}>
                    <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>headphones</span>
                    Listening: {test.listeningPartsCount} Parts
                  </span>
                  <span className={styles['metadata-item']}>
                    <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>menu_book</span>
                    Reading: {test.readingQuizzesCount} Passages
                  </span>
                  <span className={styles['metadata-item']}>
                    <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>edit_note</span>
                    Writing: {test.writingPromptsCount} Tasks
                  </span>
                </div>
              </div>
              <button 
                className={styles['btn-start']} 
                disabled={actionLoading || !!activeSession}
                onClick={() => {
                  setSelectedTestForSetup(test);
                  setSetupStep(1);
                }}
              >
                Start Mock Test
              </button>
            </div>
          ))}
          {tests.length === 0 && <p style={{ color: 'var(--outline)', margin: '16px' }}>No mock tests available.</p>}
        </div>
      )}

      {/* Attempt History */}
      <h2 className={styles['section-title']}>Your Exam History</h2>
      {historyLoading ? (
        <div className="loading-spinner" style={{ margin: '32px auto' }}>
          <span className="spinner" />
          <span>Loading history...</span>
        </div>
      ) : history.length > 0 ? (
        <div className={styles['history-table']}>
          <div className={styles['history-header']}>
            <span>Test Title</span>
            <span>Overall Band</span>
            <span>Status</span>
            <span>Date & Actions</span>
          </div>
          {history.map(item => (
            <div key={item.submissionId} className={styles['history-row']}>
              <span style={{ fontWeight: 600 }}>{item.title}</span>
              <span className={styles['history-band']} style={{ color: item.status === 'COMPLETED' ? 'var(--secondary)' : 'inherit' }}>
                {item.status === 'COMPLETED' ? (item.overallBand?.toFixed(1) || '—') : '—'}
              </span>
              <span>
                <span className={`${styles['status-badge']} ${
                  item.status === 'COMPLETED' ? styles['status-completed'] : 
                  item.status === 'GRADING' ? styles['status-grading'] : styles['status-writing']
                }`}>
                  {item.status === 'GRADING' ? 'AI Grading...' : item.status.toLowerCase()}
                </span>
              </span>
              <div className={styles['date-actions']}>
                <span className={styles['date-text']}>
                  {new Date(item.submittedAt).toLocaleDateString('en-US', {
                    day: 'numeric', month: 'short', year: 'numeric'
                  })}
                </span>
                <button 
                  className="btn btn-sm btn-outline" 
                  onClick={() => navigate(`/mock-tests/result/${item.submissionId}`)}
                >
                  View Report
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className={styles['info-card']} style={{ textAlign: 'center', padding: '48px' }}>
          <span className="material-symbols-outlined" style={{ fontSize: '3rem', color: 'var(--outline)', marginBottom: '12px' }}>
            folder_open
          </span>
          <p style={{ color: 'var(--on-surface-variant)', margin: 0 }}>You haven't taken any full mock tests yet. Your reports will appear here once you complete an exam.</p>
        </div>
      )}
    </div>
  );
}
