import { useEffect, useRef } from 'react';
import { EditorState, type Extension } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import {
  HighlightStyle,
  bracketMatching,
  indentOnInput,
  syntaxHighlighting,
} from '@codemirror/language';
import { tags } from '@lezer/highlight';

/**
 * The code terminal.
 *
 * CodeMirror 6 rather than Monaco: about a fifth of the bundle, it themes from
 * plain CSS custom properties so the editor is part of this interface rather than
 * a widget dropped into it, and it behaves on a tablet. For C, C++, Java, Python
 * and JavaScript the practical difference is bracket matching and indentation,
 * which CodeMirror does well.
 *
 * Grammars are loaded per language, on demand, so a Python participant never
 * downloads the C++ parser.
 *
 * `Tab` indents rather than moving focus, which is what anyone editing code
 * expects — but `Escape` then `Tab` escapes, so a keyboard user is never trapped
 * inside the editor. That pairing is the whole reason `indentWithTab` is safe to
 * bind here.
 */

/** Syntax colours, drawn from the same tokens as the rest of the site. */
const arenaHighlight = HighlightStyle.define([
  { tag: tags.keyword, color: 'var(--color-magenta)' },
  { tag: [tags.controlKeyword, tags.moduleKeyword], color: 'var(--color-magenta)' },
  { tag: [tags.function(tags.variableName), tags.function(tags.propertyName)], color: 'var(--color-signal)' },
  { tag: [tags.string, tags.special(tags.string)], color: 'var(--color-orange)' },
  { tag: [tags.number, tags.bool, tags.null], color: 'var(--color-tech)' },
  { tag: [tags.comment, tags.lineComment, tags.blockComment], color: 'var(--color-faint)', fontStyle: 'italic' },
  { tag: [tags.typeName, tags.className], color: 'var(--color-tech)' },
  { tag: [tags.operator, tags.punctuation], color: 'var(--color-muted)' },
  { tag: tags.variableName, color: 'var(--color-ink)' },
  { tag: tags.propertyName, color: 'var(--color-ink)' },
  { tag: tags.definition(tags.variableName), color: 'var(--color-ink)' },
]);

const arenaTheme = EditorView.theme(
  {
    '&': {
      backgroundColor: 'transparent',
      color: 'var(--color-ink)',
      height: '100%',
      fontSize: '13.5px',
    },
    '.cm-scroller': {
      fontFamily: 'var(--font-mono)',
      lineHeight: '1.65',
      overflow: 'auto',
    },
    '.cm-content': { caretColor: 'var(--color-signal)', paddingBlock: '0.75rem' },
    '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--color-signal)', borderLeftWidth: '2px' },
    '.cm-gutters': {
      backgroundColor: 'transparent',
      color: 'var(--color-faint)',
      border: 'none',
      borderRight: '1px solid var(--color-line)',
      paddingRight: '0.5rem',
    },
    '.cm-activeLine': { backgroundColor: 'rgb(255 255 255 / 0.025)' },
    '.cm-activeLineGutter': { backgroundColor: 'transparent', color: 'var(--color-muted)' },
    '&.cm-focused': { outline: 'none' },
    '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': {
      backgroundColor: 'var(--color-signal-soft)',
    },
    '.cm-matchingBracket, &.cm-focused .cm-matchingBracket': {
      backgroundColor: 'var(--color-signal-soft)',
      outline: '1px solid var(--color-signal-deep)',
    },
  },
  { dark: true },
);

/**
 * Load a grammar for a language id.
 *
 * C is handled by the C++ grammar — it is a superset, and the highlighting is
 * correct for C source. A language we do not recognise gets no grammar rather than
 * a wrong one: plain monospace text is honest, mis-highlighted code is not.
 */
async function grammarFor(language: string): Promise<Extension | null> {
  switch (language) {
    case 'c':
    case 'cpp':
      return (await import('@codemirror/lang-cpp')).cpp();
    case 'java':
      return (await import('@codemirror/lang-java')).java();
    case 'python':
      return (await import('@codemirror/lang-python')).python();
    case 'javascript':
      return (await import('@codemirror/lang-javascript')).javascript();
    default:
      return null;
  }
}

export function CodeEditor({
  value,
  language,
  onChange,
  readOnly,
}: {
  /** Treated as the initial document for this `docKey`, not a controlled value. */
  value: string;
  language: string;
  onChange: (next: string) => void;
  readOnly?: boolean;
}) {
  const host = useRef<HTMLDivElement | null>(null);
  const view = useRef<EditorView | null>(null);

  // Held in a ref so changing the handler never tears down the editor — that would
  // lose the cursor position and the undo history on every keystroke.
  const emit = useRef(onChange);
  emit.current = onChange;

  useEffect(() => {
    if (host.current === null) return;

    let cancelled = false;
    let instance: EditorView | null = null;

    void (async () => {
      const grammar = await grammarFor(language);
      if (cancelled || host.current === null) return;

      const extensions: Extension[] = [
        lineNumbers(),
        highlightActiveLine(),
        highlightActiveLineGutter(),
        history(),
        bracketMatching(),
        indentOnInput(),
        syntaxHighlighting(arenaHighlight),
        arenaTheme,
        EditorView.lineWrapping,
        // `indentWithTab` last so it wins over the default Tab binding.
        keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) emit.current(update.state.doc.toString());
        }),
      ];

      if (grammar !== null) extensions.push(grammar);
      if (readOnly === true) {
        extensions.push(EditorState.readOnly.of(true), EditorView.editable.of(false));
      }

      instance = new EditorView({
        state: EditorState.create({ doc: value, extensions }),
        parent: host.current,
      });
      view.current = instance;
    })();

    return () => {
      cancelled = true;
      instance?.destroy();
      view.current = null;
    };
    // `value` is deliberately absent: it seeds the document once. Including it
    // would rebuild the editor on every keystroke. Switching problems remounts
    // this component via its `key`, which is what loads the next document.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [language, readOnly]);

  return <div className="arena-editor" ref={host} />;
}
