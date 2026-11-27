# V4-P02-OPT live-profile-v2

文件是 `api/aiditto-domain/src/main/resources/relationship/live-profile-v2.tsv`。数据集摘要 `80cbae3e2470584c6ac0a9987ae4f7d96d50bf3b13ea943c9b4078817024f19d`。live-profile-v1 的 66 条和摘要 `4d4c9b0759dc91a2d872829b756a2d332fd0e9dbb86fd43d217f0a5ab99ddc1c` 没有改。120 条工程评估集也没有改。

v2 共 200 条。开发集 D001–D120，保留集 H001–H080。两段文本没有重复，也没有与 v1 原句重合。每行七列：`id`、`split`、`category`、`disposition`、`text`、`expected`、`note`。一条样本可以有多个预期键，键之间用分号分隔。

文本只含汉字、字母、`，` 和 `。`，长度 2 到 40，并能通过 `FreeTextPolicy`。样本不是同一短语的批量替换。

## 标签

| disposition | 含义 | 开发集 | 保留集 |
| --- | --- | ---: | ---: |
| EXTRACT | 应当提取列出的键 | 54 | 34 |
| UNKNOWN | 必须保持未知 | 50 | 34 |
| CONTRADICTION | 应当保留矛盾，不收成单一特征 | 8 | 6 |
| UNEXPRESSIBLE | 注册表无法表达 | 4 | 2 |
| WITHHOLD | 不应外发，实验不调用模型 | 4 | 4 |

EXTRACT 里含多个预期键的，开发集 22 条，保留集 14 条。保留集的 EXTRACT 预期键共 48 个。

类别覆盖明确自身、明确伴侣偏好、多个合法特征、混合自身与偏好、否定、矛盾、条件、场景依赖、无法确定、引用他人、恶意注入、无关陈述和注册表无法表达的语义。保留集各类数量：明确自身 10、明确偏好 10、混合 8、多特征 6、否定 6、矛盾 6、条件 6、不确定 6、场景 4、引用 4、注入 4、无关 4、外发拦截 4、无法表达 2。

隐私拦截成功只表示没有外发。WITHHOLD 样本的 `not-sent` 不计入模型语义正确。开发集可以在设计阶段查看；本轮没有用它改 Prompt 或规则。保留集在口径冻结后用 E3 测了一次。
