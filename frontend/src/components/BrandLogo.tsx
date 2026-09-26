import ecellUrl from '@/assets/logo/ecell-mit-thane.png';
import instituteUrl from '@/assets/logo/mit-institute-white.png';
import kernelUrl from '@/assets/logo/mit-tech-kernel-white.png';
import { cn } from '@/lib/cn';

/**
 * The marks that head every page. One import each, one place.
 *
 * These are three DIFFERENT organisations, not variants of one logo:
 *
 *   institute  MAEER'S Maharashtra Institute of Technology, Thane - the college
 *   kernel     MIT TECH KERNEL - the technical community running the event
 *   ecell      E CELL MIT THANE - the entrepreneurship cell alongside it
 *
 * Imported through Vite rather than referenced from `/public`, so the build
 * fingerprints and cache-busts them and a broken path fails at build time rather
 * than shipping a missing image.
 *
 * `institute` and `kernel` are supplied as white artwork on transparency, which
 * is exactly what a near-black ground wants, so neither is recoloured or
 * cropped. `ecell` arrived as a full-colour JPEG matted onto black; it was
 * cropped to its artwork and un-matted into straight alpha, so it now sits on
 * the page ground like the other two instead of carrying a black tile and a
 * halo of JPEG blocking around its edges. Its own blue and red are untouched -
 * it is another organisation's mark, not a site accent to be recoloured.
 */

interface Mark {
  src: string;
  /** The asset's real pixels. Not a size - this fixes the ratio and reserves the box. */
  width: number;
  height: number;
  label: string;
  /**
   * Height classes per size token.
   *
   * Per-mark rather than shared, because these three are neither equal in rank
   * nor equal in shape:
   *
   *   RANK    MIT TECH KERNEL runs the event and leads every lockup, so it is
   *           set larger than the institute mark beside it at the same token.
   *
   *   SHAPE   the two wordmarks are wide (2.55 and 2.33); the E Cell lockup
   *           stacks an emblem over its wordmark and is nearly square (1.12).
   *           Matched heights would give the square mark the visual weight of a
   *           logo twice its rank, so it is set shorter at every token - which
   *           still leaves it legible, because a square mark spends its height
   *           on artwork rather than on a single line of letters.
   *
   * Sized by HEIGHT, never width: a shared width would distort at least two of
   * the three. `w-auto` on the element is what enforces that.
   */
  sizes: Record<LogoSize, string>;
}

export type LogoSize = 'sm' | 'md' | 'lg';

const MARKS = {
  institute: {
    src: instituteUrl,
    width: 2003,
    height: 785,
    label: "MAEER'S Maharashtra Institute of Technology, Thane",
    sizes: {
      sm: 'h-8 sm:h-10',
      md: 'h-10 sm:h-12 md:h-14 lg:h-16',
      lg: 'h-12 sm:h-16 lg:h-20',
    },
  },
  kernel: {
    src: kernelUrl,
    width: 1138,
    height: 489,
    label: 'MIT Tech Kernel',
    sizes: {
      sm: 'h-10 sm:h-12',
      md: 'h-12 sm:h-16 md:h-18 lg:h-20',
      lg: 'h-20 sm:h-28 lg:h-32',
    },
  },
  ecell: {
    src: ecellUrl,
    width: 520,
    height: 466,
    label: 'E Cell, MIT Thane',
    sizes: {
      sm: 'h-7 sm:h-8',
      md: 'h-10 sm:h-12 md:h-13 lg:h-14',
      lg: 'h-14 sm:h-18 lg:h-20',
    },
  },
} satisfies Record<string, Mark>;

export type BrandMark = keyof typeof MARKS;

interface BrandLogoProps {
  mark: BrandMark;
  /**
   * sm  dense chrome: the admin bar.
   * md  site header and the experience nav.
   * lg  standalone brand moments: the footer lockup, the closing colophon.
   */
  size?: LogoSize;
  className?: string;
  /**
   * True when adjacent text already names the organisation, so a screen reader
   * does not announce it twice.
   */
  decorative?: boolean;
}

export function BrandLogo({ mark, size = 'md', className, decorative = false }: BrandLogoProps) {
  const logo = MARKS[mark];

  return (
    <img
      src={logo.src}
      width={logo.width}
      height={logo.height}
      alt={decorative ? '' : logo.label}
      {...(decorative ? { 'aria-hidden': true } : {})}
      // max-w-full keeps it inside a narrow parent; w-auto keeps the ratio.
      className={cn('block w-auto max-w-full select-none', logo.sizes[size], className)}
      // Above the fold on every route: eager, and prioritised so it is not queued
      // behind the web fonts.
      loading="eager"
      decoding="async"
      fetchPriority="high"
      draggable={false}
    />
  );
}
