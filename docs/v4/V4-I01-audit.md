# V4-I01 产品业务审计

审计读的是当前源码。没有把实验室提示词当成已经存在的接口。本轮只使用合成用户，不读线上用户。

## 注册与登录

`AuthController` 提供 `POST /api/auth/sms/send`、`POST /api/auth/login`、`POST /api/auth/logout`、`POST /api/auth/email/code`、`POST /api/auth/email/verify`。

`AccountService.loginUser` 按手机号哈希查找 `user`。没有行就插入，状态是 `onboarding`，角色默认 `user`。已停用账号在登录时恢复为 `active` 或 `onboarding`。`deleting`、`purged`、`banned` 登录失败。注册不写 `matching_consent`，也不写 `relationship_profile_lifecycle`。

事务在 `loginUser` 上。邀请码冲突会再查一次手机号。

## 问卷

`OnboardingController`：

- `PUT /api/onboarding/answers/{questionId}`，正文 `{value, furthestStepId?}`。`value` 为 null 时删除该答案。
- `PUT /api/onboarding/progress`
- `GET /api/onboarding/progress`
- `POST /api/onboarding/complete`

`OnboardingService.writeAnswer` 先锁用户行，再由 `AnswerValidator` 按 `questions.json` 校验，然后写入 `onboarding_answer`。入驻完成后的每次修改调用 `ProfileProjection.write`，把 `profile.version` 加一，并在提交后发出 `ProfileWrittenEvent`。`complete` 在同一事务里检查必填项、把状态改成 `active`、写 `profile`、发事件。

`deleting` 和 `purged` 现在不能再写答案或完成入驻。这只挡住已进入注销的账号。

没有单独的“问卷版本”接口。源版本就是 `profile.version`，完成入驻时从 1 开始，之后每次编辑加一。

## 资料、照片、语音

`ProfileController` 的资料修改转回 `OnboardingService.writeAnswer`。照片走 `PhotoController` 的上传票据、完成和删除，表是 `photo`。地点走 `PlaceController`，表是 `place`。

`VoiceController` 在 `/api/voice` 下有开始、作答、停止和读取语音画像。表是 `voice_session`、`voice_question`、`voice_answer`、`voice_sample`、`voice_profile`。这些表不进入 `OnboardingEvidenceAdapter`。

## 现有 V3 画像

`ProfileProjection` 把答案写成 `profile`。`TasteListener` 在 `ProfileWrittenEvent` 提交后调用 `TasteExtractor`，写入 `user_taste`。`FeedbackLearningService.scanAndRewrite` 调用 `TasteRewriter`、`PairMemoryService` 和 `JudgeCalibrationService`，改的是 `user_taste`、`ai_pair_memory` 和 `judge_calibration`。默认 `aiditto.learning.enabled` 与 `aiditto.taste.enabled` 仍是开启。

这些类不写 `relationship_profile_version`。

## V4 画像

权威写入仍是 `RelationshipProfileService`。`OnboardingEvidenceAdapter` 只接受用户编号，从 `onboarding_answer` 读取，再经 `RelationshipProfileProjector`、`FeatureRegistry` 和 `SnapshotAdmission`。`projectAuthoritative` 还会并入已经保存的 F02 确认。客户端不能提交特征码、证据编号、别人的身份、来源版本或授权状态。

`aiditto.relationship-profile.processing-enabled` 默认 false。关闭时读写画像被拒绝。模型修订 `saveModelRevision` 仍直接拒绝。

没有面向用户的 V4 画像 HTTP 接口。Match Lab 里的画像编辑不是这条入驻路径。

## 授权

匹配授权在 `MatchingConsentController` 和 `matching_consent`。缺行是 `UNKNOWN`。`REVOKED` 不能进池。注册不把缺行改成 `GRANTED`。

画像处理授权和外部模型授权在 `relationship_profile_lifecycle`。两者分开。授予处理授权时，模型导出默认是 `revoked`。没有生命周期行就是未授予。

反馈处理授权和个体学习授权没有产品设置表。它们只存在于离线反馈上下文 `MatchFeedbackContext`。缺省构造不会在注册时写成已授权。群体评估使用同一上下文里的反馈授权，并有独立的消费用途 `GROUP_EVALUATION`。线上协议文本不在本轮。

## 注销

`DELETE /api/account` 调用 `AccountService.requestDelete`。它锁用户、把状态写成 `deleting`、记下 `purge_at`、撤销会话、放开发布。默认模式到此为止，不擦 V4 行。

`V4_INTEGRATION_TEST` 在同一事务里继续调用 `AccountLifecycleCoordinator`。细节见 `V4-I01-account-lifecycle.md`。

停用和重新启用是另一条接口，不擦画像。
