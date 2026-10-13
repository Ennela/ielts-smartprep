import { describe, it, expect, afterEach } from 'vitest';
import axiosClient from '../api/axiosClient';
import speakingApi from '../api/speakingApi';

/*
 * POST /speaking/grade only accepts multipart/form-data. axiosClient's default JSON
 * Content-Type made axios serialise the FormData to JSON, and every submission came back
 * "Content-Type 'application/json' is not supported."
 */
describe('speakingApi.grade', () => {
  const originalAdapter = axiosClient.defaults.adapter;
  afterEach(() => { axiosClient.defaults.adapter = originalAdapter; });

  it('sends the recordings as a multipart form, not JSON', async () => {
    let sent;
    axiosClient.defaults.adapter = (config) => {
      sent = config;
      return Promise.resolve({ data: { success: true, data: {} }, status: 200, statusText: 'OK', headers: {}, config });
    };

    await speakingApi.grade(4, [
      { blob: new Blob(['a'], { type: 'audio/webm' }), duration: 21.6 },
      { blob: new Blob(['b'], { type: 'audio/webm' }), duration: 30.2 },
    ]);

    expect(sent.data).toBeInstanceOf(FormData);
    expect(String(sent.headers.getContentType() || '')).not.toMatch(/json/);
    expect(sent.data.get('promptId')).toBe('4');
    expect(sent.data.getAll('durationSeconds')).toEqual(['22', '30']);
    expect(sent.data.getAll('audio')).toHaveLength(2);
  });
});
