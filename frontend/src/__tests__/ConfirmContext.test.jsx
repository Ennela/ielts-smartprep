import '@testing-library/jest-dom';
import { describe, it, expect } from 'vitest';
import { useState } from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ConfirmProvider, useConfirm } from '../context/ConfirmContext';

/*
 * window.confirm was replaced by one in-app dialog. It must answer the awaiting caller,
 * treat Escape and the backdrop as "no", and hold focus while it is open.
 */

function Asker() {
  const confirm = useConfirm();
  const [answer, setAnswer] = useState('none');
  return (
    <>
      <button onClick={async () => setAnswer(String(await confirm({ title: 'Delete it?', message: 'Gone for good.', confirmLabel: 'Delete', tone: 'danger' })))}>
        Ask
      </button>
      <output>{answer}</output>
    </>
  );
}

const renderAsker = () => render(<ConfirmProvider><Asker /></ConfirmProvider>);

describe('ConfirmProvider', () => {
  it('resolves true when the action is confirmed', async () => {
    renderAsker();
    fireEvent.click(screen.getByText('Ask'));

    const dialog = await screen.findByRole('dialog', { name: 'Delete it?' });
    expect(dialog).toHaveTextContent('Gone for good.');
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('true'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('resolves false on Escape and returns focus to the opener', async () => {
    renderAsker();
    const opener = screen.getByText('Ask');
    opener.focus();
    fireEvent.click(opener);

    await screen.findByRole('dialog');
    // Focus moves into the dialog, onto the first (safe) button.
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    fireEvent.keyDown(document, { key: 'Escape' });

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('false'));
    expect(opener).toHaveFocus();
  });

  it('keeps Tab inside the dialog', async () => {
    renderAsker();
    fireEvent.click(screen.getByText('Ask'));
    await screen.findByRole('dialog');

    const confirmButton = screen.getByRole('button', { name: 'Delete' });
    confirmButton.focus();
    fireEvent.keyDown(document, { key: 'Tab' });

    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
  });
});
