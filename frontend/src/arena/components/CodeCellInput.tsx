import { useRef, type ClipboardEvent, type KeyboardEvent } from 'react';

/**
 * The eight-character access code entry.
 *
 * Eight separate inputs rather than one field, because a code read off a phone
 * screen and typed at a terminal wants position to be visible — you can see at a
 * glance how many characters are in and which one you are on.
 *
 * The behaviours that make it usable are the ones people notice only when missing:
 *
 * - Pasting the whole code distributes it across the cells, which is what someone
 *   with the code in an email will do first.
 * - Backspace on an empty cell steps back and clears the previous one, rather than
 *   stranding the caret.
 * - Arrow keys move between cells.
 * - Everything is upper-cased on the way in, and characters outside the code
 *   alphabet are dropped rather than shown and then rejected later.
 *
 * The alphabet excludes 0, 1, I, O and L — the generator never emits them
 * (see `AccessCodeGenerator`), so accepting them here would only let someone type
 * a code that cannot exist.
 */

const LENGTH = 8;
const ALPHABET = /^[2-9A-HJ-NP-Z]$/;

export function CodeCellInput({
  value,
  onChange,
  onComplete,
  disabled,
  invalid,
}: {
  value: string;
  onChange: (next: string) => void;
  onComplete?: () => void;
  disabled?: boolean;
  invalid?: boolean;
}) {
  const cells = useRef<(HTMLInputElement | null)[]>([]);

  const characters = Array.from({ length: LENGTH }, (_, i) => value[i] ?? '');

  function focusCell(index: number) {
    cells.current[Math.min(Math.max(index, 0), LENGTH - 1)]?.focus();
  }

  function write(next: string) {
    const cleaned = next.toUpperCase().slice(0, LENGTH);
    onChange(cleaned);
    if (cleaned.length === LENGTH) onComplete?.();
    return cleaned;
  }

  function handleInput(index: number, raw: string) {
    const typed = raw.toUpperCase().replace(/\s/g, '');
    if (typed === '') return;

    // A character that could never appear in a real code is discarded silently.
    // Showing it and refusing later would be a worse experience than not taking it.
    const accepted = Array.from(typed).filter((c) => ALPHABET.test(c));
    if (accepted.length === 0) return;

    const chars = [...characters];
    let cursor = index;
    for (const character of accepted) {
      if (cursor >= LENGTH) break;
      chars[cursor] = character;
      cursor += 1;
    }

    const next = write(chars.join('').replace(/\s/g, ''));
    focusCell(Math.min(cursor, LENGTH - 1));
    if (next.length < LENGTH) focusCell(cursor);
  }

  function handleKeyDown(index: number, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Backspace') {
      event.preventDefault();
      const chars = [...characters];
      if (chars[index] !== '') {
        chars[index] = '';
        write(chars.join(''));
        return;
      }
      // Empty cell: step back and clear the one before it.
      if (index > 0) {
        chars[index - 1] = '';
        write(chars.join(''));
        focusCell(index - 1);
      }
      return;
    }

    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      focusCell(index - 1);
    }
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      focusCell(index + 1);
    }
  }

  function handlePaste(index: number, event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault();
    handleInput(index, event.clipboardData.getData('text'));
  }

  return (
    <div
      className="arena__cells"
      role="group"
      aria-label="Access code, eight characters"
      data-invalid={invalid === true}
    >
      {characters.map((character, index) => (
        <input
          // Position is the identity here: the cells never reorder, and there is
          // nothing else stable to key on.
          // eslint-disable-next-line react/no-array-index-key
          key={index}
          ref={(element) => {
            cells.current[index] = element;
          }}
          className="arena__cell"
          type="text"
          inputMode="text"
          autoComplete="off"
          autoCorrect="off"
          spellCheck={false}
          maxLength={1}
          disabled={disabled}
          value={character}
          aria-label={`Character ${index + 1} of ${LENGTH}`}
          onChange={(event) => handleInput(index, event.target.value)}
          onKeyDown={(event) => handleKeyDown(index, event)}
          onPaste={(event) => handlePaste(index, event)}
          onFocus={(event) => event.target.select()}
        />
      ))}
    </div>
  );
}
