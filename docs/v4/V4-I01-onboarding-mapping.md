# V4-I01 Onboarding 字段映射

映射来自现有 `questions.json` 和 `RelationshipProfileProjector.STORED_QUESTIONS`。没有用模型补题。显示名称来自 `web/src/onboarding/questions.ts` 的中文气泡，星号已去掉。

题库 50 项。已映射 22 项。未映射 28 项。缺失答案保持不存在。拒答不解释成不喜欢。自身特征不复制成伴侣偏好。`noSchoolEmail` 是流程标记，不是题目，不进入画像。

来源表是 `onboarding_answer`。敏感答案同时写 `value_enc`，`value_json` 为空。V3 的 `profile` 行仍由 `ProfileProjection` 写入，那不是这份 V4 映射。姓名、性别、生日、身高、地点、照片和语音没有合法的 V4 特征映射，保持 UNMAPPED。

## 已映射

| questionCode | 显示名称 | 答案类型 | 合法值域 | 来源 | profileSection | featureCode | 语义 | 转换 | 操作符 | 强度 | 证据 | 可匹配 | 硬约束 | 敏感 | 额外确认 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| isStudent | 你现在还是学生吗？ | single | yes、no | onboarding_answer.isStudent | SELF | life.student | 用户自身事实 | yes 或 no 才进入 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| hobbies | 你平时有什么爱好和兴趣？ | multi | cooking、music、travel、reading、gym、gaming、movies、art | onboarding_answer.hobbies | SELF | lifestyle.hobbies | 用户自身事实 | 只保留八个已知爱好，自定义和缺失不进入 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| cookingStyle | 你的做饭风格是？ | multi | italian、asian、mexican、comfort、baking、grilling、healthy、brunch | onboarding_answer.cookingStyle | SELF | lifestyle.cooking | 用户自身事实 | 爱好含 cooking 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| musicSound | 你平时听什么音乐？ | multi | pop、hiphop、indie、electronic、rnb、rock、country、jazz、classical、kpop、latin | onboarding_answer.musicSound | SELF | lifestyle.music | 用户自身事实 | 爱好含 music 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| travelStyle | 你的旅行风格是？ | multi | backpacking、luxury、foodie、adventure、beach、city、solo、road | onboarding_answer.travelStyle | SELF | lifestyle.travel | 用户自身事实 | 爱好含 travel 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| readingGenres | 最近在读什么类型的书？ | multi | litfic、fantasy、scifi、mystery、romance、memoir、selfhelp、poetry、classics、smut | onboarding_answer.readingGenres | SELF | lifestyle.reading | 用户自身事实 | 爱好含 reading 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| gymTrainingStyle | 你的训练方式是？ | multi | lifting、running、yoga、pilates、crossfit、boxing、dance、cycling、climbing | onboarding_answer.gymTrainingStyle | SELF | lifestyle.gym | 用户自身事实 | 爱好含 gym 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| gamingGenres | 你喜欢玩什么游戏？ | multi | fps、rpg、sports、moba、strategy、indie、cozy、fighting、mmo | onboarding_answer.gamingGenres | SELF | lifestyle.gaming | 用户自身事实 | 爱好含 gaming 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| moviesContent | 你喜欢看什么内容？ | multi | indie、horror、romcom、action、scifi、documentary、a24、foreign、anime、reality | onboarding_answer.moviesContent | SELF | lifestyle.movies | 用户自身事实 | 爱好含 movies 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| artMediums | 你用什么媒介创作？ | multi | drawing、painting、digital、pottery、sculpture、collage、crafts、mixed | onboarding_answer.artMediums | SELF | lifestyle.art | 用户自身事实 | 爱好含 art 时保留合法选项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| relationshipLooking | 你在找什么样的关系？ | multi | lifePartner、serious、casual、friends、notSure | onboarding_answer.relationshipLooking | SELF | relationship.goal | 用户自身事实 | 只保留注册表中的关系目标 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| matchingStyle | 你希望见一面现在怎么帮你匹配？ | single | faster、steady、fewer、theOne | onboarding_answer.matchingStyle | SELF | matching.pace | 用户自身事实 | 只保留注册表中的筛选节奏 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| schoolYear | 这学期你读几年级？ | single | freshman、sophomore、junior、senior、master、phd、other | onboarding_answer.schoolYear | SELF | education.year | 用户自身事实 | 只保留注册表中的年级 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| educationLevel | 你的最高学历是？ | single | highSchool、college、graduate、preferNot | onboarding_answer.educationLevel | SELF | education.level | 用户自身事实 | 合法学历为 CONFIRMED；preferNot 为 WITHHELD；缺失不存在 |  |  | CONFIRMED 或 WITHHELD | 是 | 否 | 否 | 否 | MAPPED |
| firstDateBudget | 人均，大概就行 | single | under20、20to50、50to100、100plus、noPref | onboarding_answer.firstDateBudget | SELF | spending.first_date | 用户自身事实 | 合法档位为 CONFIRMED；noPref 为 UNBOUNDED |  |  | CONFIRMED 或 UNBOUNDED | 是 | 否 | 否 | 否 | MAPPED |
| datingHelp | 如果你有个恋爱教练，最想让 ta 帮你什么？ | multi | approaching、convo、intoMe、confident、whatIWant、theDate、justDate | onboarding_answer.datingHelp | SELF | support.help_wanted | 用户自身事实 | 只保留注册表中的帮助项 |  |  | CONFIRMED | 是 | 否 | 否 | 否 | MAPPED |
| occupation | 你现在做什么工作？ | longText | longText | onboarding_answer.occupation | SELF | occupation.text | 用户自身事实 | 原文不进快照，只保留引用 |  |  | CONFIRMED | 是 | 否 | 是 | 否 | MAPPED |
| firstMessage | 这能帮对方更快了解你 | longText | longText | onboarding_answer.firstMessage | BEHAVIOR | opening.message | 用户行为事实 | 有原文才进入，类型为 SELF_REPORTED |  |  | CONFIRMED | 是 | 否 | 是 | 否 | MAPPED |
| expectedAgeRange | 你希望对方的年龄范围是？ | ageRange | ageRange | onboarding_answer.expectedAgeRange | PREFERENCE | partner.age | 用户伴侣偏好 | 两个整数边界才进入，缺失不补 | BETWEEN | SUPER | CONFIRMED | 是 | 是 | 否 | 否 | MAPPED |
| heightRange | 你希望对方多高？ | heightRange | heightRange | onboarding_answer.heightRange | PREFERENCE | partner.height | 用户伴侣偏好 | any 为不设限；上下限才是硬约束 | ANY 或 BETWEEN | NONE 或 SUPER | CONFIRMED | 是 | 条件 | 否 | 否 | MAPPED |
| educationImportance | 对方的学历需要不低于你吗？ | single | none、kinda、super | onboarding_answer.educationImportance | PREFERENCE | partner.education | 用户伴侣偏好 | none 不设限；kinda 软偏好；super 硬约束 | ANY 或 AT_LEAST | NONE、KINDA 或 SUPER | CONFIRMED | 是 | 条件 | 否 | 否 | MAPPED |
| expectedGender | 你想和谁约会？ | multi | men、women、nonbinary、everyone | onboarding_answer.expectedGender | PREFERENCE | partner.gender | 用户伴侣偏好 | everyone 不设限；其他选择是硬约束且只保留引用 | ANY 或 CONTROLLED_REF | NONE 或 SUPER | CONFIRMED | 是 | 条件 | 是 | 否 | MAPPED |

## 未映射

| questionCode | 显示名称 | 答案类型 | 合法值域 | 来源 | 语义 | 敏感 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| name | 你叫什么名字？ | text | text | onboarding_answer.name | 无法确定到 V4 特征 | 否 | UNMAPPED |
| gender | 你的性别是？ | single | man、woman、nonbinary、other | onboarding_answer.gender | 无法确定到 V4 特征 | 否 | UNMAPPED |
| birthday | 你的生日是哪天？ | date | date | onboarding_answer.birthday | 无法确定到 V4 特征 | 否 | UNMAPPED |
| cookingSignatureDish | 你的拿手菜是？ | text | text | onboarding_answer.cookingSignatureDish | 无法确定到 V4 特征 | 否 | UNMAPPED |
| musicNowPlaying | 最近在单曲循环谁？ | text | text | onboarding_answer.musicNowPlaying | 无法确定到 V4 特征 | 否 | UNMAPPED |
| travelNext | 下一个想去的目的地是？ | text | text | onboarding_answer.travelNext | 无法确定到 V4 特征 | 否 | UNMAPPED |
| readingLastBook | 最近读完的一本书？ | text | text | onboarding_answer.readingLastBook | 无法确定到 V4 特征 | 否 | UNMAPPED |
| gymTrainingFor | 你在为什么而练？ | text | text | onboarding_answer.gymTrainingFor | 无法确定到 V4 特征 | 否 | UNMAPPED |
| gamingNowPlaying | 最近在玩什么？ | text | text | onboarding_answer.gamingNowPlaying | 无法确定到 V4 特征 | 否 | UNMAPPED |
| moviesLastWatch | 最近看过最好看的？ | text | text | onboarding_answer.moviesLastWatch | 无法确定到 V4 特征 | 否 | UNMAPPED |
| artMake | 你都做些什么？ | text | text | onboarding_answer.artMake | 无法确定到 V4 特征 | 否 | UNMAPPED |
| ethnicity | 你的族裔是？ | multi | americanIndian、black、eastAsian、hispanic、middleEastern、pacificIslander、southAsian、southEastAsian、white | onboarding_answer.ethnicity | 无法确定到 V4 特征 | 是 | UNMAPPED |
| expectedEthnicity | 对族裔有偏好吗？ | multi | noPreference、americanIndian、black、eastAsian、hispanic、middleEastern、pacificIslander、southAsian、southEastAsian、white | onboarding_answer.expectedEthnicity | 无法确定到 V4 特征 | 是 | UNMAPPED |
| expectedPosition | 你的理想型？ | multi | top、bottom、masc、femme、flexible、preferNot | onboarding_answer.expectedPosition | 无法确定到 V4 特征 | 是 | UNMAPPED |
| position | 你会怎么形容自己？ | single | top、bottom、masc、femme、flexible、preferNot | onboarding_answer.position | 无法确定到 V4 特征 | 是 | UNMAPPED |
| height | 你的身高是？ | height | height | onboarding_answer.height | 无法确定到 V4 特征 | 否 | UNMAPPED |
| schoolEmail | 你的学校邮箱是？📧 | email | email | onboarding_answer.schoolEmail | 无法确定到 V4 特征 | 是 | UNMAPPED |
| personalEmail | 你的邮箱是？📧 | email | email | onboarding_answer.personalEmail | 无法确定到 V4 特征 | 是 | UNMAPPED |
| emailVerify | 我们给你发了一个 6 位验证码 | code | code | onboarding_answer.emailVerify | 无法确定到 V4 特征 | 否 | UNMAPPED |
| location | 你想在哪一带约会？📍 | location | location | onboarding_answer.location | 无法确定到 V4 特征 | 否 | UNMAPPED |
| photos | 挑几张最能体现你 vibe 的 | photos | photos | onboarding_answer.photos | 无法确定到 V4 特征 | 否 | UNMAPPED |
| preferencesIntro | 接下来聊聊你的偏好吧 | interstitial | interstitial | onboarding_answer.preferencesIntro | 无法确定到 V4 特征 | 否 | UNMAPPED |
| hearAbout | 你是从哪儿知道我们的？ | single | friend、poster、tabling、instagram、douyin、x、rednote、google、facebook、linkedin、wechat、bilibili、weibo | onboarding_answer.hearAbout | 无法确定到 V4 特征 | 否 | UNMAPPED |
| dealbreakers | 在意的都可以写：外形、习惯、性格等等。 | longText | longText | onboarding_answer.dealbreakers | 无法确定到 V4 特征 | 是 | UNMAPPED |
| politics | 你的政治倾向是？ | single | veryLeft、left、center、right、veryRight、apolitical | onboarding_answer.politics | 无法确定到 V4 特征 | 是 | UNMAPPED |
| politicsImportance | 对方需要和你政治观点一致吗？ | single | none、kinda、super | onboarding_answer.politicsImportance | 无法确定到 V4 特征 | 是 | UNMAPPED |
| religion | 你的宗教信仰是？ | multi | atheist、agnostic、christian、catholic、jewish、muslim、hindu、buddhist、spiritual | onboarding_answer.religion | 无法确定到 V4 特征 | 是 | UNMAPPED |
| religionImportance | 对方需要和你信仰一致吗？ | single | none、kinda、super | onboarding_answer.religionImportance | 无法确定到 V4 特征 | 是 | UNMAPPED |

关系历史、行为事实里除 `firstMessage` 以外的记录、尚未表达的偏好，都不从空白答案生成。明确选择「不限」「noPref」「everyone」或身高 `any` 时，才写成 UNBOUNDED。`preferNot` 只在学历上写成 WITHHELD。

