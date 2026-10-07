import { describe, it, expect, afterEach } from 'vitest';
import { AxiosError } from 'axios';
import axiosClient from '../api/axiosClient';
import errorMessage from '../utils/errorMessage';

/*
 * What a page shows when a request fails. The server's own words reach the user only when
 * the backend wrote them for the user; a framework reason ("Content-Type 'application/json'
 * is not supported."), "Internal server error", "Network Error" or a script error never does.
 */
describe('errorMessage', () => {
  const originalAdapter = axiosClient.defaults.adapter;
  afterEach(() => { axiosClient.defaults.adapter = originalAdapter; });

  const failWith = async (status, data, code) => {
    axiosClient.defaults.adapter = (config) => Promise.reject(new AxiosError(
      'Request failed', code, config, {},
      status ? { status, data, headers: {}, config, statusText: '' } : undefined,
    ));
    try {
      await axiosClient.get('/anything');
    } catch (err) {
      return errorMessage(err, 'Could not save.');
    }
    throw new Error('the request should have failed');
  };

  it.each([
    ['a check written for the user', 400, { message: 'Username already exists', errorCode: 'BAD_REQUEST' }, 'Username already exists'],
    ['a locked account', 423, { message: 'Account locked for 15 minutes', errorCode: 'ACCOUNT_LOCKED' }, 'Account locked for 15 minutes'],
    ['the AI quota', 429, { message: 'You have exceeded your daily AI quota of 50 requests.', errorCode: 'RATE_LIMIT_EXCEEDED' }, 'You have exceeded your daily AI quota of 50 requests.'],
  ])('passes on %s', async (_, status, data, expected) => {
    expect(await failWith(status, data)).toBe(expected);
  });

  it.each([
    ['a framework 400', 400, { message: "Required part 'audio' is not present.", errorCode: 'INVALID_REQUEST' }],
    ['an unsupported content type', 415, { message: "Content-Type 'application/json' is not supported.", errorCode: 'UNSUPPORTED_MEDIA_TYPE' }],
    ['a server failure', 500, { message: 'Internal server error', errorCode: 'INTERNAL_SERVER_ERROR' }],
    ['a missing record', 404, { message: 'Reading quiz not found: 12', errorCode: 'RESOURCE_NOT_FOUND' }],
    ['a gateway page', 500, '<html>error</html>'],
  ])('keeps the page\'s own wording for %s', async (_, status, data) => {
    expect(await failWith(status, data)).toBe('Could not save.');
  });

  it.each([
    ['no connection', undefined, undefined, 'ERR_NETWORK', /Could not reach the server/],
    ['a timeout', undefined, undefined, 'ECONNABORTED', /took too long/],
    ['the AI being down', 503, { message: 'AI service is unavailable', errorCode: 'AI_SERVICE_ERROR' }, undefined, /busy right now/],
    ['a missing role', 403, { message: 'Access denied', errorCode: 'ACCESS_DENIED' }, undefined, /do not have permission/],
    ['a file too large', 413, '<html>413</html>', undefined, /too large/],
  ])('explains %s in plain words', async (_, status, data, code, expected) => {
    expect(await failWith(status, data, code)).toMatch(expected);
  });

  it('never shows a script error', () => {
    expect(errorMessage(new TypeError("Cannot read properties of undefined (reading 'data')"), 'Could not save.'))
      .toBe('Could not save.');
  });
});
