# V4-P01 问卷到画像的映射

题目以 `api/aiditto-app/src/main/resources/questions.json` 为准。下面没有列出的码，投影器不会假装已经有答案。

## 可以直接得到的自身事实

| 题目 | 画像 | 规则 |
| --- | --- | --- |
| hobbies 的八个固定选项 | lifestyle.hobbies | 只收 cooking、music、travel、reading、gym、gaming、movies、art |
| hobbies 的自定义文本 | lifestyle.stated_interest | `allowCustom` 允许用户另写。摄影不在八个选项里，所以只能作为原文，不能算成一个兴趣枚举 |
| cookingStyle、musicSound、travelStyle、readingGenres、gymTrainingStyle、gamingGenres、moviesContent、artMediums | 对应的 lifestyle.* | 只有选了所属兴趣才投影。未知选项丢掉，不推断 |
| relationshipLooking | relationship.goal，自身节 | 这是用户自己要的关系，不是对方的性格 |
| matchingStyle | matching.pace，自身节 | faster、steady、fewer、theOne |
| isStudent | life.student | 只有 yes / no。缺题就没有这个特征 |
| schoolYear | education.year | 七个年级选项 |
| educationLevel | education.level | 三个学历。`preferNot` 是 `WITHHELD`，不是 false，也不是「学历低」 |
| firstDateBudget | spending.first_date | 四个档。`noPref` 是 `UNBOUNDED`，不是 50，也不是 0.5 |
| datingHelp | support.help_wanted | 七个选项 |
| occupation | occupation.text | 去掉邮箱和手机号，最长 60。洗完为空就不生成 |
| firstMessage | opening.message，行为节，`SELF_REPORTED` | 保留原文。不从句子推断性格 |

兴趣分支里的自由文本不投影：`cookingSignatureDish`、`musicNowPlaying`、`travelNext`、`readingLastBook`、`gymTrainingFor`、`gamingNowPlaying`、`moviesLastWatch`、`artMake`。`hearAbout` 也不投影。这些文本不能被读成人格。

## 必须由用户明确表达的伴侣偏好

| 题目 | 画像 | 约束 |
| --- | --- | --- |
| expectedAgeRange 两个整数 | partner.age，`BETWEEN` | 指向既有年龄硬规则。缺题保持未知。不用匹配投影里的默认 18–24 |
| heightRange 且 any 为 true | partner.height，`ANY`，重要程度 `NONE` | `UNBOUNDED`。同时带来的 min/max 忽略，不取中点 |
| heightRange 且 any 不为 true | partner.height，`BETWEEN` | 指向既有身高硬规则 |
| educationImportance = none | partner.education | `UNBOUNDED`，重要程度 `NONE` |
| educationImportance = kinda | partner.education = atLeastMine | 重要程度 `KINDA`，约束 `SOFT`。很在意不等于一票否决 |
| educationImportance = super | partner.education = atLeastMine | 重要程度 `SUPER`，指向既有学历硬规则 |
| expectedGender 含 everyone | partner.gender | `UNBOUNDED`。不把 men、women、nonbinary 写进 JSON |
| expectedGender 是具体选项 | partner.gender | `CONTROLLED_REF`，引用 `profile.expected_genders_enc`。选项值仍只在原加密列 |

## 敏感字段继续留在原处

不复制进普通快照、模型输入、向量或日志：姓名、性别、生日、身高厘米、精确地址和 placeId、经纬度、照片、手机、邮箱、微信、`dealbreakers`、`ethnicity`、`expectedEthnicity`、`expectedPosition`、`position`、`politics`、`politicsImportance`、`religion`、`religionImportance`。`expectedGender` 的选项值也按这个边界处理，只留受控引用。

`TasteFacts` 白名单没有扩大，也没有缩小。

## 现在还不能当成事实的东西

| 类别 | 例子 | 本轮状态 |
| --- | --- | --- |
| 将来才可能由模型暂定 | 社交主动性、沟通习惯、探索倾向、相处节奏、作息、情感支持 | 注册表有槽。投影器不填。推断必须是 `PROVISIONAL`，偏好推断只能是 `SOFT` |
| 必须有真实反馈才算观察 | 约会里实际表现出的主动性、真实沟通 | 没有 `FEEDBACK_OBSERVATION` 就是未知。记忆节保持空 |
| 题库里完全没有 | MBTI、颜值、价值排名、人格优劣、摄影作为一个兴趣枚举 | 不生成。虚构案例里的摄影是自述原文 |

`VoiceAssetService` 的情景和分身对话不是 `OBSERVED_BEHAVIOR`。来源引用若以 voice、persona、llm、sim 的约定前缀开头，会被拒绝。

旧的 `ProfileProjector` 仍会把缺失年龄写成 18–24，供现有匹配使用。新投影器不会跟着做。两条结果可以不同，这是并行，不是把旧行改掉。
