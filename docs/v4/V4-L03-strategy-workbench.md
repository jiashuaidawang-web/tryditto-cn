# V4-L03 策略学习

页面在 `/match-lab/strategy`。

冻结来源展示 128 条观察。数据集版本是 `f03-synthetic-1`。页面上的标签投影是 F03-FINAL 的 `f03-labels-2`。历史标签文件不在这里改写，也不另报一套旧计数。

沙箱来源只统计当前工作区的反馈账本。样本不足时，未校准基线、常数频率、分箱、Logistic 和 Isotonic 都是 `INSUFFICIENT`。`ScoreCalibration.Result` 只有汇总误差，没有分箱曲线点，所以页面不另画一条曲线。`SCORE_AB_ACCEPT_A` 和 `SCORE_BA_ACCEPT_B` 分开，未知标签不记成负例。

策略行包括 E+G1、E+G2、E+G3、E+G4、F 和 M02-B5。`realLift` 是 `NOT_IDENTIFIED`。未发布候选没有被填上接受、见面或继续意愿。

激活按钮只证明拒绝。它不修改生产评分或求解配置。
