# V4-I01 证据适配器

`OnboardingEvidenceAdapter` 是服务端组件。输入只有用户编号。它读取该用户已经落库的 `onboarding_answer`，不接受客户端传来的特征码、证据编号、其他用户、来源版本、授权状态或画像版本。

读取时只保留 `RelationshipProfileProjector.STORED_QUESTIONS` 里的题。敏感题如果只有 `value_enc`，性别偏好会变成密封引用，其他密封题不会被当成一段可见原文。投影后交给 `SnapshotAdmission`。写入仍只走 `RelationshipProfileService.save`。适配器自己不插入画像版本。

`serverProjection` 在这份问卷投影上合并已经保存、且未撤回的 F02 确认。旧的 `user_taste` 摘要不会被拿来恢复用户事实。

相同的有效答案和相同的确认集合再次投影，输入哈希不变，`save` 不新增版本。改一题会提高 `profile.version`。新的投影保留其他仍然合法的答案，也保留未撤回的确认。已撤回的确认以撤回状态出现，不恢复成有效偏好。

`UNKNOWN` 是没有这道证据。`WITHHELD` 只来自明确的不愿透露。`UNBOUNDED` 只来自明确的不设限。未映射的题不会进入正式快照。
