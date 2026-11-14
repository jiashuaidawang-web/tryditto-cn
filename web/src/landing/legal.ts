import { t, type T } from '../i18n/lang';

export type LegalKind = 'terms' | 'privacy' | 'cookies';

type Doc = { title: T; sections: { h: T; p: T[] }[] };

export const LEGAL_UI = {
  draft: t('Draft · pending legal review', '草案 · 待律师定稿'),
  updated: t('Last updated: Nov 14, 2026', '更新日期：2026 年 11 月 14 日'),
  back: t('Back', '返回'),
  notFoundTitle: t('Page not found', '这个页面不存在'),
  notFoundBody: t('The link may be wrong, or the page has moved.', '链接可能写错了，或者页面已经搬走了。'),
  home: t('Back to home', '回到首页'),
};

export const LEGAL: Record<LegalKind, Doc> = {
  terms: {
    title: t('Terms of Service', '用户协议'),
    sections: [
      {
        h: t('What Ditto does', '见一面是什么'),
        p: [
          t(
            'Ditto arranges one in-person meet-up a week: every Wednesday we suggest one person, a time and a place. It is free right now.',
            '见一面每周为你安排一次线下见面：每周三推荐一位对象，并建议见面的时间和地点。目前完全免费。',
          ),
          t(
            'This site is a demo. Features and these terms may change.',
            '本站是演示版本，功能和本协议都可能调整。',
          ),
        ],
      },
      {
        h: t('Who can join', '谁可以使用'),
        p: [
          t('You must be 18 or older. Students are matched with students; working users with working users.', '必须年满 18 周岁。在校学生只和在校学生匹配，已工作的用户只和已工作的用户匹配。'),
          t('Sign in with your own phone number. One person, one account.', '请用你本人的手机号登录，首次登录即注册。一人只能有一个账号。'),
        ],
      },
      {
        h: t('What you agree to', '你需要遵守的'),
        p: [
          t('Your profile and photos are true and are of you.', '填写的资料真实，照片是你本人。'),
          t('No harassment, scams, or illegal content.', '不骚扰、不诈骗、不发布违法信息。'),
          t('Meet in public places for a first date and let a friend know where you are.', '第一次见面请选择公共场所，并把行程告诉朋友。'),
        ],
      },
      {
        h: t('No-shows and reports', '爽约和举报'),
        p: [
          t(
            'Missing a confirmed date without notice pauses your matching for a while; repeated no-shows stop it.',
            '确认见面后无故爽约，会被暂停一段时间的匹配；多次爽约会被停止匹配。',
          ),
          t(
            'You can report a match from the date pages. After a report the two of you are never matched again; safety reports pause the reported user until reviewed.',
            '你可以在约会相关页面举报对方。举报后你们不会再被配对；涉及人身安全、诈骗或疑似未成年的举报，被举报人会被立即暂停匹配，等待人工复核。',
          ),
        ],
      },
      {
        h: t('Limits', '责任范围'),
        p: [
          t(
            'We do not run background checks and cannot promise how a date goes. We are not responsible for what happens between users offline.',
            '我们不做背景调查，也不能保证每次见面的结果。用户之间线下发生的行为由当事人自行负责。',
          ),
        ],
      },
      {
        h: t('Leaving', '注销'),
        p: [t('You can delete your account anytime in Account settings.', '你可以随时在「账户设置」里注销账号。')],
      },
    ],
  },
  privacy: {
    title: t('Privacy Policy', '隐私政策'),
    sections: [
      {
        h: t('What we collect', '我们收集哪些信息'),
        p: [
          t('Your phone number, to sign you in.', '手机号：用于登录。'),
          t(
            'Your questionnaire answers: name, gender, who you want to date, birthday, interests, height and preferences.',
            '问卷答案：名字或昵称、性别、想约会的对象、生日、爱好、身高和各项偏好。',
          ),
          t('Your school email, to verify you are a student.', '学校邮箱：用于确认你是在校学生。'),
          t('Your photos.', '你上传的照片。'),
          t(
            'Your dating area: the place and travel mode you choose. We never read your GPS location.',
            '约会范围：你选择的地点和出行方式。我们不获取手机的 GPS 定位。',
          ),
          t(
            'What you do in a match: available times, check-in, feedback, reports, and the WeChat ID you choose to share.',
            '匹配过程中的信息：你选的空闲时段、到场签到、见面反馈、举报内容，以及你愿意交换的微信号。',
          ),
        ],
      },
      {
        h: t('How we use it', '用来做什么'),
        p: [t('Only to match you, arrange dates, keep people safe and handle reports. We never sell your data.', '只用于匹配、安排见面、保障安全和处理举报。我们不出售你的任何信息。')],
      },
      {
        h: t('Who sees what', '谁能看到'),
        p: [
          t(
            'Only the person you are matched with sees your photos, name, age and the basics on your date card.',
            '你的照片、名字、年龄和介绍卡上的基本信息，只给和你配对的人看。',
          ),
          t('Answers marked with 🔒 are never shown to anyone.', '带 🔒 的偏好不会展示给任何人。'),
          t('Your WeChat ID is shown only when you both want to meet again.', '你的微信号只在双方都想再见时，才展示给对方。'),
        ],
      },
      {
        h: t('Storage and security', '存储和安全'),
        p: [
          t(
            'Data is stored on servers in mainland China. Phone numbers, emails, WeChat IDs, feedback and reports are encrypted; photos are served through short-lived signed links.',
            '数据存放在中国境内的服务器上。手机号、邮箱、微信号、反馈和举报内容加密存储；照片只能通过限时有效的签名链接访问。',
          ),
          t(
            'We do not send your data to any third-party AI service. If that changes, we will update this policy and ask for your consent first.',
            '目前不会把你的资料发送给任何第三方 AI 服务。将来如有变化，会先更新本政策并征得你的同意。',
          ),
        ],
      },
      {
        h: t('Deleting your data', '删除你的信息'),
        p: [
          t(
            'You can edit your profile anytime. When you delete your account it is closed at once, every session is signed out and you leave matching; your personal data is erased 30 days later.',
            '你可以随时在资料页修改信息。在「账户设置」里注销后，账号立即停用，所有登录失效，也不再参与匹配；个人信息在 30 天后删除。',
          ),
        ],
      },
      {
        h: t('Minors', '未成年人'),
        p: [t('We do not serve anyone under 18.', '我们不向未满 18 周岁的人提供服务。')],
      },
    ],
  },
  cookies: {
    title: t('Cookies', 'Cookie 说明'),
    sections: [
      {
        h: t('No tracking', '不做追踪'),
        p: [
          t(
            'We do not use tracking cookies, and there is no third-party analytics or advertising code on this site.',
            '本站不使用 Cookie 追踪你，也没有任何第三方统计或广告代码。',
          ),
        ],
      },
      {
        h: t('What your browser keeps', '浏览器里保存了什么'),
        p: [
          t(
            'Your sign-in and questionnaire progress are kept in your browser’s local storage so you stay signed in and can pick up where you left off. Clearing this site’s data in your browser removes them.',
            '登录状态和问卷进度保存在浏览器的本地存储里，用来保持登录、断点续填。在浏览器里清除本站数据即可删除。',
          ),
        ],
      },
    ],
  },
};
