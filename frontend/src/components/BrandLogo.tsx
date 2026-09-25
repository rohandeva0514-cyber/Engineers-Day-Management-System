import instituteUrl from '@/assets/logo/mit-institute-white.png';
import kernelUrl from '@/assets/logo/mit-tech-kernel-white.png';
import { cn } from '@/lib/cn';

/**
 * The two marks that head every page. One import each, one place.
 *
 * These are two DIFFERENT organisations, not two variants of one logo:
 *
 *   institute  MAEER'S Maharashtra Institute of Technology, Thane - the college
 *   kernel     MIT TECH KERNEL - the technical community running the event
 *
 * Both are supplied as white artwork on transparency, which is exactly what a
 * near-black ground wants, so neither is recoloured, filtered or cropped. They
 * are used as given.
 *
 * Imported through Vite rather than referenced from `/public`, so the build
 * fingerprints and cache-busts them and a broken path fails at build time rather
 * than shipping a missing image.
 */

interface Mark {
  src: string;
  /** The asset's real pixels. Not a size - this fixes the ratio and reserves the box. */
  width: number;
  height: number;
  label: string;
}

const MARKS = {
  institute: {
    src: instituteUrl,
    width: 2003,
    height: 785,
    label: "MAEER'S Maharashtra Institute of Technology, Thane",
  },
  kernel: {
    src: kernelUrl,
    width: 1138,
    height: 489,
    label: 'MIT Tech Kernel',
  },
} satisfies Record<string, Mark>;

export type BrandMark = keyof typeof MARKS;

type LogoSize = 'sm' | 'md' | 'lg';

/**
 * Sized by HEIGHT, never width, so neither lockup can be stretched - they have
 * different ratios (2.55 and 2.33), so a shared width would distort at least one
 * of them. `w-auto` is what enforces that.
 */
const SIZES: Record<LogoSize, string> = {
  /** Dense chrome: the admin bar. */
  sm: 'h-10 sm:h-12',
  /** Site header and the experience nav. */
  md: 'h-14 sm:h-20',
  /** Standalone brand moments. */
  lg: 'h-20 sm:h-28',
};

interface BrandLogoProps {
  mark: BrandMark;
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
      className={cn('block w-auto max-w-full select-none', SIZES[size], className)}
      // Above the fold on every route: eager, and prioritised so it is not queued
      // behind the web fonts.
      loading="eager"
      decoding="async"
      fetchPriority="high"
      draggable={false}
    />
  );
}
