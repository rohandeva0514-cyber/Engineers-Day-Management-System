import { ArenaFrame, ArenaStatusLine } from '../components/ArenaFrame';
import type { ArenaLanguageOption } from '@/services/arena/arenaTypes';
import type { ApiError } from '@/services/apiError';

/**
 * Choose the language the mission will be run in.
 *
 * The cards come from the server, never from a constant here. A runtime that is
 * unavailable simply does not appear, rather than being offered and then failing at
 * the first run.
 *
 * The choice is saved as it is made — a refresh comes back to this screen with the
 * same card selected. It commits the participant to nothing: the lock happens at
 * Start Mission and is enforced by the backend, not by this component hiding the
 * cards.
 */
export function LanguageSelectScreen({
  languages,
  selected,
  onSelect,
  onContinue,
  onBack,
  busy,
  error,
}: {
  languages: ArenaLanguageOption[];
  selected: string | null;
  onSelect: (language: string) => void;
  onContinue: () => void;
  onBack: () => void;
  busy: boolean;
  error: ApiError | null;
}) {
  return (
    <ArenaFrame tone="active">
      <ArenaStatusLine tone="active" label="Step 1 of 2" />
      <h1 className="arena__title arena__title--sm">Select your debugging language</h1>
      <p className="arena__body">
        Your problems come from this language&rsquo;s bank. You can change it freely
        now — it is locked when the mission starts.
      </p>

      <div className="arena__cards" role="radiogroup" aria-label="Debugging language">
        {languages.map((language) => (
          <button
            key={language.id}
            type="button"
            role="radio"
            aria-checked={selected === language.id}
            className="arena__card"
            data-selected={selected === language.id}
            disabled={busy}
            onClick={() => onSelect(language.id)}
          >
            <span className="arena__card-name">{language.label}</span>
            <span className="arena__card-runtime">{language.runtime}</span>
          </button>
        ))}
      </div>

      {error !== null && (
        <p className="arena__error-detail" role="alert">
          {error.message}
        </p>
      )}

      <div className="arena__actions">
        <button
          type="button"
          className="arena__button arena__button--primary"
          disabled={selected === null || busy}
          onClick={onContinue}
        >
          {selected === null ? 'Select a language' : 'Continue to briefing'}
        </button>
        <button type="button" className="arena__button" disabled={busy} onClick={onBack}>
          Back
        </button>
      </div>
    </ArenaFrame>
  );
}
