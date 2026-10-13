import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import QuestionPanel from '../components/questions/QuestionPanel';
import ImageUploadField from '../components/admin/ImageUploadField';
import adminApi from '../api/adminApi';

vi.mock('../api/adminApi', () => ({ default: { uploadImage: vi.fn() } }));

/*
 * V56: a question group can carry the map, plan or diagram its questions label. Admins
 * upload it; a link to another site would be blocked by the page's CSP.
 */

describe('QuestionPanel group image', () => {
  it('shows the group picture once, from whichever question carries it', () => {
    const questions = [1, 2].map(n => ({
      questionId: n, questionType: 'DIAGRAM_LABEL_COMPLETION', questionText: `Label ${n}`, orderIndex: n,
      groupId: 7, groupLabel: 'Questions 1-2: Label the diagram.',
      imageUrl: n === 2 ? '/api/v1/images/img_x.png' : null,
    }));
    render(<QuestionPanel questions={questions} answers={{}} setAnswer={() => {}} />);

    const images = screen.getAllByRole('img');
    expect(images).toHaveLength(1);
    expect(images[0]).toHaveAttribute('src', '/api/v1/images/img_x.png');
  });
});

describe('ImageUploadField', () => {
  beforeEach(() => adminApi.uploadImage.mockReset());

  it('uploads the chosen file and keeps the URL it gets back', async () => {
    adminApi.uploadImage.mockResolvedValue({ data: { data: { url: '/api/v1/images/img_y.png' } } });
    const onChange = vi.fn();
    render(<ImageUploadField id="img" label="Diagram" value="" onChange={onChange} />);

    const file = new File([new Uint8Array([0x89, 0x50])], 'map.png', { type: 'image/png' });
    fireEvent.change(screen.getByLabelText('Diagram'), { target: { files: [file] } });

    await waitFor(() => expect(onChange).toHaveBeenCalledWith('/api/v1/images/img_y.png'));
    expect(adminApi.uploadImage).toHaveBeenCalledWith(file);
  });

  it('says why an upload was refused', async () => {
    adminApi.uploadImage.mockRejectedValueOnce(
      Object.assign(new Error('Request failed'), {
        response: { data: { message: 'Only PNG, JPEG or WebP images are allowed', errorCode: 'BAD_REQUEST' } },
        userMessage: 'Only PNG, JPEG or WebP images are allowed',
      }));
    render(<ImageUploadField id="img" label="Diagram" value="" onChange={() => {}} />);

    fireEvent.change(screen.getByLabelText('Diagram'), { target: { files: [new File(['x'], 'a.gif')] } });

    await waitFor(() => expect(screen.getByText('Only PNG, JPEG or WebP images are allowed')).toBeInTheDocument());
  });

  it('can remove the image', () => {
    const onChange = vi.fn();
    render(<ImageUploadField id="img" label="Diagram" value="/api/v1/images/img_y.png" onChange={onChange} />);
    fireEvent.click(screen.getByText('Remove image'));
    expect(onChange).toHaveBeenCalledWith('');
  });
});
