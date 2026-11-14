import { t, type T } from '../i18n/lang';

/**
 * One line of the manifesto; `em` is set in the display face, like Ditto's "one match".
 * Headings render with `word-break: keep-all`, so a zh heading may only wrap at a `\u200b`.
 */
export type ManifestoLine = T | { before: T; em: T; after: T };
export type ManifestoBlock = { h2: T } | { lines: ManifestoLine[] };

export const MANIFESTO_PAGE = {
  title: [t('The Future is', '未来，'), t('Human Again.', '重新有人味。')] as const,
  blocks: [
    {
      lines: [
        t("For the past decade, we've become more connected than ever.", '过去十年，我们比任何时候都更“连在一起”。'),
        t("And somehow, we've never felt further apart.", '却也从没觉得彼此这么远。'),
      ],
    },
    { lines: [t('We scroll.', '我们刷。'), t('We swipe.', '我们滑。'), t('We match.', '我们配对。'), t('We keep scrolling.', '然后接着刷。')] },
    {
      lines: [
        t(
          'Thousands of people pass through our screens every day, while the people sitting ten feet away remain strangers.',
          '每天有成千上万的人从屏幕上划过，而坐在三米外的人，依然是陌生人。',
        ),
      ],
    },
    { h2: t('We think connection deserves better.', '我们相信，\u200b相遇值得更好的方式。') },
    {
      lines: [
        t('Before dating became an algorithm, people met through people.', '在约会变成算法之前，人是通过人认识的。'),
        t('A friend knew someone.', '朋友认识某个人。'),
        t('A roommate made an introduction.', '室友帮忙牵了线。'),
        t('Someone saw something in two people before they saw it themselves.', '有人比两个当事人更早看出了他们之间的可能。'),
      ],
    },
    {
      lines: [
        t("Matchmaking wasn't about having infinite options.", '牵线从来不是为了给你无穷无尽的选择，'),
        t('It was about finding the right person.', '而是为了找到对的那个人。'),
      ],
    },
    { lines: [t('Ditto brings that tradition back to college.', '见一面把这个传统带回校园。')] },
    {
      lines: [
        t('No endless swiping.', '不用没完没了地滑。'),
        t('No hundreds of profiles.', '不用翻几百份资料。'),
        t('No searching for the next best thing.', '不用一直找“下一个更好的”。'),
      ],
    },
    {
      lines: [
        {
          before: t('Every Wednesday at 7 PM, Ditto gives you ', '每周三晚上 7 点，见一面从你的校园里为你挑出'),
          em: t('one match', '一个人'),
          after: t(' from your campus.', '。'),
        },
        t('One person.', '一个人。'),
        t('One introduction.', '一次介绍。'),
        t('One reason to actually meet.', '一个真的去见面的理由。'),
      ],
    },
    {
      lines: [
        t(
          "Because your campus is already full of people worth knowing. The problem isn't that we need more options—it's that we need better reasons to connect.",
          '因为你的校园里，本来就到处是值得认识的人。问题不在于选择不够多，而在于我们缺一个更好的理由去认识彼此。',
        ),
      ],
    },
    {
      lines: [
        t(
          "Technology was supposed to bring us closer. Instead, we're doomscrolling and doom-swiping past each other.",
          '科技本该让我们更近。结果我们却在没完没了地刷、没完没了地滑，和彼此擦肩而过。',
        ),
      ],
    },
    {
      lines: [
        t('Ditto wants to change that.', '见一面想改变这一点。'),
        t('We use technology to make the introduction, then get out of the way.', '我们用技术完成介绍，然后就退到一边。'),
      ],
    },
    {
      lines: [
        t("Because the best connection doesn't happen on a screen.", '因为最好的相遇，不会发生在屏幕上。'),
        t('It happens in real life.', '它发生在现实里。'),
      ],
    },
  ] as ManifestoBlock[],
  cta: [t('Start finding who you deserve to meet.', '去见\u200b值得你见的人。'), t('Only on Ditto.', '只在见一面。')] as const,
  phone: t('Phone number', '手机号'),
  join: t('Join Now', '立即加入'),
  continue: t('Continue', '继续'),
  docTitle: t('Manifesto', '我们的宣言'),
};
