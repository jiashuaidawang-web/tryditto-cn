import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, Route } from 'react-router';
import { ApiError } from '../api/http';
import { labApi, type Overview, type Profile, type Recall, type Reasoning, type RegistryFeature, type Score, type ScoreItem } from './api';
import { FeedbackPage, GlobalPage, LearningPage } from './LoopPages';
import { ReplayPage, SafetyPage, StrategyPage } from './EvaluationPages';
import './match-lab.css';

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="lab">
      <aside>
        <p className="lab-mark">Match Lab</p>
        <p className="lab-env">SYNTHETIC_SANDBOX</p>
        <nav>
          <NavLink to="/match-lab" end>实验总览</NavLink>
          <NavLink to="/match-lab/profiles">用户画像</NavLink>
          <NavLink to="/match-lab/candidates">候选发现</NavLink>
          <NavLink to="/match-lab/score">双向评分</NavLink>
          <NavLink to="/match-lab/reasoning">AI 关系推理</NavLink>
          <NavLink to="/match-lab/global">全局匹配</NavLink>
          <NavLink to="/match-lab/feedback">反馈事件</NavLink>
          <NavLink to="/match-lab/learning">画像学习</NavLink>
          <NavLink to="/match-lab/strategy">策略学习</NavLink>
          <NavLink to="/match-lab/replay">实验回放</NavLink>
          <NavLink to="/match-lab/safety">安全审计</NavLink>
        </nav>
      </aside>
      <main>{children}</main>
    </div>
  );
}

function Gate({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return <p className="lab-error">加载失败。</p>;
  if (error.status === 401) return <p className="lab-error">需要管理员登录。请先在登录页使用管理员手机号，再回到这里。</p>;
  if (error.status === 403) return <p className="lab-error">访问被拒绝。开关关闭，或当前账号不是管理员。隐藏菜单不能代替这个检查。</p>;
  return <p className="lab-error">{error.message}</p>;
}

function OverviewPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [dataset, setDataset] = useState('ALL');
  useEffect(() => { labApi.overview().then(setData).catch(setError); }, []);
  if (error) return <Gate error={error} />;
  if (!data) return <p>正在读取实验目录。</p>;
  const versions = Array.from(new Set(data.catalog.map((entry) => entry.datasetVersion)));
  const rows = data.catalog.filter((entry) => dataset === 'ALL' || entry.datasetVersion === dataset);
  return (
    <>
      <header className="lab-top">
        <h1>实验总览</h1>
        <span>{data.environment}</span>
        <span>生产启用 {data.production}</span>
        <span>真实关系质量 {data.realRelationshipQuality}</span>
      </header>
      <section className="lab-cards">
        <article><b>{data.profiles}</b><span>本次沙箱虚构画像</span></article>
        <article><b>{data.runs}</b><span>本次进程运行</span></article>
        <article><b>{data.catalog.length}</b><span>目录条目</span></article>
        <article><b>{data.commitSha}</b><span>本次进程提交</span></article>
      </section>
      <p className="lab-note">每条实验的人数和样本量只属于该实验。不要把不同数据集的人数、配对数或反馈数加成一次业务统计。</p>
      <div className="lab-form">
        <label>数据集
          <select value={dataset} onChange={(event) => setDataset(event.target.value)} aria-label="数据集版本">
            <option value="ALL">全部版本</option>
            {versions.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
        <a href={`https://github.com/jiashuaidawang-web/tryditto-cn/commit/${data.frozenCommit}`}>冻结数据集提交</a>
        <a href={data.ciIndex}>分支 CI 列表</a>
        <span>研究环境 {data.researchEnvironment}</span>
        <span>工作区 {data.workspace.workspaceId}</span>
        <span>{data.storage}</span>
        <span>具体通过记录见 {data.statusDocument}</span>
      </div>
      <section className="lab-cards">
        {data.policies.map((item) => (
          <article key={item.policy}><b>{item.policy}</b><span>{item.experimentId}</span><small>{item.note}</small></article>
        ))}
      </section>
      <ul className="lab-limits">{data.limits.map((item) => <li key={item}>{item}</li>)}</ul>
      <table>
        <thead><tr><th>实验</th><th>来源</th><th>状态</th><th>本实验指标</th><th>案例</th></tr></thead>
        <tbody>
          {rows.map((entry) => (
            <tr key={entry.experimentId}>
              <td>{entry.title}<small>{entry.module} · {entry.datasetVersion}</small><small>{entry.datasetIdentityHash}</small><small>{entry.datasetContentHash}</small></td>
              <td>{entry.sourceType}<small>{entry.executionMode}</small></td>
              <td>{entry.executionStatus}<small>{entry.qualityStatus}</small></td>
              <td>{entry.metrics.join(' · ')}<small>{entry.limitations}</small></td>
              <td>{entry.caseIds.join(', ') || '无'}<small>{entry.policyVersion}</small></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function ProfilesPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [registry, setRegistry] = useState<RegistryFeature[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [other, setOther] = useState<number | null>(null);
  const [diff, setDiff] = useState<{ featureCode: string; section: string; left: string; right: string }[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [code, setCode] = useState('self.pace');
  const [value, setValue] = useState('slow');
  const [name, setName] = useState('新虚构画像');
  const [editCode, setEditCode] = useState('self.pace');
  const [editValue, setEditValue] = useState('slow');
  const [editStatus, setEditStatus] = useState('CONFIRMED');

  async function load() {
    const [people, features] = await Promise.all([labApi.profiles(), labApi.registry()]);
    setProfiles(people);
    setRegistry(features);
    setSelected((current) => people.some((item) => item.userId === current) ? current : (people[0]?.userId ?? null));
  }
  useEffect(() => { load().catch(setError); }, []);
  if (error) return <Gate error={error} />;
  const person = profiles.find((item) => item.userId === selected);
  const definition = registry.find((item) => item.featureCode === code);
  const editDefinition = registry.find((item) => item.featureCode === editCode);
  const layers = [
    ['SELF', '自我 SelfProfile'],
    ['PREFERENCE', '偏好 PreferenceProfile'],
    ['BEHAVIOR', '行为 BehaviorProfile'],
    ['MEMORY', '记忆 MemoryProfile'],
  ] as const;

  function payload(nextCode: string, nextValue: string, nextStatus: string) {
    if (!person) return null;
    const features = person.features.map((feature) => feature.featureCode === nextCode
      ? { ...feature, value: nextValue, status: nextStatus }
      : feature);
    const row = (feature: Profile['features'][number]) => ({
      code: feature.featureCode,
      value: feature.value,
      status: feature.status,
      importance: feature.importance || undefined,
      operator: feature.operator || 'EQ',
    });
    return {
      name: person.name,
      expectedVersion: person.version,
      self: features.filter((feature) => feature.section === 'SELF').map(row),
      preferences: features.filter((feature) => feature.section === 'PREFERENCE').map(row),
      behavior: features.filter((feature) => feature.section === 'BEHAVIOR').map(row),
    };
  }

  return (
    <>
      <header className="lab-top"><h1>用户画像</h1><span>不连接真实账号</span></header>
      <div className="lab-split">
        <ul className="lab-people">
          {profiles.map((item) => (
            <li key={item.userId}>
              <button type="button" onClick={() => setSelected(item.userId)}>{item.name}</button>
              <small>#{item.userId} · v{item.version}</small>
            </li>
          ))}
        </ul>
        <div>
          {person ? (
            <>
              <p>{person.fixtureSource} · v{person.version} · 摘要 {person.digest}</p>
              <p className="lab-note">证据状态只用领域里的 CONFIRMED、PROVISIONAL、CONTESTED。没有单独写入的记忆值会留空。</p>
              {layers.map(([section, title]) => {
                const rows = person.features.filter((feature) => feature.section === section);
                return (
                  <section key={section}>
                    <h2>{title}</h2>
                    {rows.length === 0 ? <p>这一层没有写入。</p> : (
                      <table>
                        <thead><tr><th>特征</th><th>值</th><th>操作符</th><th>强度</th><th>证据来源</th><th>状态</th><th>可匹配</th></tr></thead>
                        <tbody>
                          {rows.map((feature) => (
                            <tr key={feature.section + feature.featureCode}>
                              <td>{feature.displayName}<small>{feature.featureCode}</small></td>
                              <td>{feature.value || '空'}</td>
                              <td>{feature.operator || '—'}</td>
                              <td>{feature.importance || '—'}</td>
                              <td>{feature.evidenceSource}</td>
                              <td>{feature.status}</td>
                              <td>{feature.usableForMatching ? '允许' : '不允许'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </section>
                );
              })}
            </>
          ) : <p>还没有画像。</p>}
          <form className="lab-form" onSubmit={(event) => {
            event.preventDefault();
            labApi.create({
              name,
              self: [{ code, value, status: 'CONFIRMED', operator: 'EQ' }],
              preferences: [{ code: 'partner.pace', value: 'slow', status: 'CONFIRMED', importance: 'SUPER', operator: 'EQ' }],
            }).then(() => load()).catch(setError);
          }}>
            <input value={name} onChange={(event) => setName(event.target.value)} aria-label="画像名称" />
            <select value={code} onChange={(event) => setCode(event.target.value)} aria-label="特征码">
              {registry.filter((item) => item.sections.includes('SELF') && item.valueType === 'ENUM').map((item) => (
                <option key={item.featureCode} value={item.featureCode}>{item.displayName}</option>
              ))}
            </select>
            <select value={value} onChange={(event) => setValue(event.target.value)} aria-label="合法值">
              {(definition?.allowedValues ?? []).map((item) => <option key={item}>{item}</option>)}
            </select>
            <button type="submit">创建虚构画像</button>
          </form>
          {person && (
            <form className="lab-form" onSubmit={(event) => {
              event.preventDefault();
              const body = payload(editCode, editValue, editStatus);
              if (!body) return;
              labApi.update(person.userId, body).then(() => load()).catch(setError);
            }}>
              <select value={editCode} onChange={(event) => {
                const next = event.target.value;
                setEditCode(next);
                const feature = person.features.find((item) => item.featureCode === next);
                if (feature?.value) setEditValue(feature.value);
                if (feature?.status) setEditStatus(feature.status);
              }} aria-label="要修改的特征">
                {person.features.map((item) => <option key={item.featureCode} value={item.featureCode}>{item.displayName}</option>)}
              </select>
              <select value={editValue} onChange={(event) => setEditValue(event.target.value)} aria-label="修改后的合法值">
                {(editDefinition?.allowedValues ?? []).map((item) => <option key={item}>{item}</option>)}
              </select>
              <select value={editStatus} onChange={(event) => setEditStatus(event.target.value)} aria-label="证据状态">
                <option>CONFIRMED</option>
                <option>PROVISIONAL</option>
                <option>CONTESTED</option>
              </select>
              <button type="submit">保存修改</button>
            </form>
          )}
          <div className="lab-form">
            <select value={other ?? ''} onChange={(event) => setOther(Number(event.target.value))} aria-label="对照画像">
              <option value="">选择对照</option>
              {profiles.map((item) => <option key={item.userId} value={item.userId}>{item.name}</option>)}
            </select>
            <button type="button" onClick={() => {
              if (selected && other) labApi.diff(selected, other).then(setDiff).catch(setError);
            }}>比较特征</button>
          </div>
          {diff.length > 0 && (
            <table>
              <thead><tr><th>特征</th><th>左侧</th><th>右侧</th></tr></thead>
              <tbody>{diff.map((row) => <tr key={row.section + row.featureCode}><td>{row.featureCode}</td><td>{row.left || '空'}</td><td>{row.right || '空'}</td></tr>)}</tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}

function CandidatesPage() {
  const [data, setData] = useState<Recall | null>(null);
  const [scheme, setScheme] = useState('FULL_POOL_M02');
  const [split, setSplit] = useState('dev');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const current = data?.schemes.find((item) => item.scheme === scheme);
  return (
    <>
      <header className="lab-top"><h1>候选发现</h1><span>冻结人群 · 本次引擎执行</span></header>
      <p className="lab-note">寻求者来自冻结开发集或保留集，不是沙箱画像，也不是生产用户。NO_ROUTE_SIGNAL、截断、硬约束和证据不足分开显示。</p>
      <div className="lab-form">
        <select value={split} onChange={(event) => setSplit(event.target.value)} aria-label="冻结寻求者">
          <option value="dev">开发集寻求者</option>
          <option value="holdout">保留集寻求者</option>
        </select>
        <select value={scheme} onChange={(event) => setScheme(event.target.value)} aria-label="候选策略">
          <option value="RRF">RRF-only</option>
          <option value="RRF_M02">RRF + M02</option>
          <option value="FULL_POOL_M02">完整合法池 + M02</option>
          <option value="SHORTLIST_M02">完整画像短名单 + M02</option>
        </select>
        <button type="button" disabled={busy} onClick={() => {
          setBusy(true);
          setError(null);
          labApi.recall(split).then(setData).catch(setError).finally(() => setBusy(false));
        }}>{busy ? '正在计算' : '执行冻结对照'}</button>
        <button type="button" disabled={busy} onClick={() => {
          setBusy(true);
          setError(null);
          labApi.recallSandbox(71001).then(setData).catch(setError).finally(() => setBusy(false));
        }}>执行沙箱候选</button>
      </div>
      {error ? <Gate error={error} /> : null}
      {!data && !error && !busy ? <p>还没有执行。选择一个冻结寻求者后再计算。</p> : null}
      {data && (
        <>
          <p>{data.population} · {data.routeContract} · {data.executionMode} · {data.dataset} · 寻求者 {data.seekerId} · 初始池 {data.people} 人 · 合法边 {data.legalPool} · 未进合法池 {data.hardBlocked} · 运行 {data.runId}</p>
          <section className="lab-cards">
            {data.schemes.map((item) => (
              <article key={item.scheme}><b>{item.recommended}</b><span>{item.scheme} 可推荐</span><small>证据不足 {item.insufficient} · 硬约束 {item.hardRejected}</small></article>
            ))}
          </section>
          <h2>初始池与合法资格</h2>
          <table>
            <thead><tr><th>用户</th><th>标签</th><th>资格</th><th>R1–R5 来源</th><th>未入选</th></tr></thead>
            <tbody>
              {data.pool.map((person) => (
                <tr key={person.userId}>
                  <td>{person.userId}</td>
                  <td>{person.label}</td>
                  <td>{person.legality}</td>
                  <td>{person.routes || '—'}</td>
                  <td>{person.miss || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      {current && (
        <table>
          <thead><tr><th>用户</th><th>标签</th><th>召回名次</th><th>最终名次</th><th>双向分</th><th>覆盖</th><th>资格</th><th>未入选</th></tr></thead>
          <tbody>
            {current.rows.map((row) => (
              <tr key={row.userId}>
                <td>{row.userId}</td>
                <td>{row.label}</td>
                <td>{row.recallRank}</td>
                <td>{row.finalRank}</td>
                <td>{row.scoreAB ?? '—'} / {row.scoreBA ?? '—'}</td>
                <td>{row.coverageAB ?? '—'} / {row.coverageBA ?? '—'}</td>
                <td>{row.recommendationEligible ? '可推荐' : '拒绝'}</td>
                <td>{row.miss}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

function ScorePage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [left, setLeft] = useState(71001);
  const [right, setRight] = useState(71002);
  const [before, setBefore] = useState<Score | null>(null);
  const [after, setAfter] = useState<Score | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => { labApi.profiles().then(setProfiles).catch(setError); }, []);
  if (error) return <Gate error={error} />;

  async function run() {
    const result = await labApi.score(left, right);
    if (!before) setBefore(result);
    else setAfter(result);
  }

  return (
    <>
      <header className="lab-top"><h1>双向评分</h1><span>引擎 B5 · 分数变化不是关系效果提升</span></header>
      <div className="lab-form">
        <select value={left} onChange={(event) => setLeft(Number(event.target.value))} aria-label="用户 A">{profiles.map((item) => <option key={item.userId} value={item.userId}>{item.name}</option>)}</select>
        <select value={right} onChange={(event) => setRight(Number(event.target.value))} aria-label="用户 B">{profiles.map((item) => <option key={item.userId} value={item.userId}>{item.name}</option>)}</select>
        <button type="button" onClick={() => run().catch(setError)}>执行双向匹配</button>
        <button type="button" onClick={() => {
          const person = profiles.find((item) => item.userId === right);
          if (!person) return;
          const row = (feature: Profile['features'][number], value: string) => ({
            code: feature.featureCode,
            value,
            status: feature.status,
            importance: feature.importance || 'SUPER',
            operator: feature.operator || 'EQ',
          });
          const self = person.features.filter((feature) => feature.section === 'SELF').map((feature) => row(feature, feature.value));
          const preferences = person.features.filter((feature) => feature.section === 'PREFERENCE').map((feature) => row(
            feature,
            feature.featureCode === 'partner.pace' ? 'fast' : feature.value,
          ));
          const behavior = person.features.filter((feature) => feature.section === 'BEHAVIOR').map((feature) => row(feature, feature.value));
          labApi.update(right, { name: person.name, expectedVersion: person.version, self, preferences, behavior }).then(() => labApi.score(left, right)).then((result) => {
            setAfter(result);
            return labApi.profiles();
          }).then(setProfiles).catch(setError);
        }}>把 B 的相处节奏改成 fast 并重算</button>
      </div>
      <div className="lab-split">
        <ScoreCard title="执行结果" score={after ?? before} />
        <ScoreCard title="修改前" score={before} />
      </div>
    </>
  );
}

function ScoreCard({ title, score }: { title: string; score: Score | null }) {
  const [item, setItem] = useState<ScoreItem | null>(null);
  if (!score) return <p>还没有执行。</p>;
  return (
    <section>
      <h2>{title}</h2>
      <p>{score.executionMode} · {score.formulaVersion} · {score.policyVersion}</p>
      <p>A→B {score.scoreAB ?? '无'} · B→A {score.scoreBA ?? '无'} · 聚合 {score.aggregate ?? '无'}</p>
      <p>覆盖 {score.coverageAB ?? '无'} / {score.coverageBA ?? '无'}</p>
      <p>画像版本 {score.versionA} / {score.versionB} · 摘要 {score.digestA} / {score.digestB}</p>
      <p>{score.recommendationEligible ? '可推荐' : '拒绝推荐'} · {score.reasons.join('，') || '无拒绝原因'}</p>
      <p className="lab-note">分数变化只说明算法行为变了，不代表真实关系效果提升。</p>
      {score.hardFilter.length > 0 && <p>硬约束 {score.hardFilter.join('，')}</p>}
      <table>
        <thead><tr><th>方向</th><th>偏好</th><th>目标</th><th>重要性</th><th>满足</th><th>分数</th><th></th></tr></thead>
        <tbody>
          {score.items.map((row) => (
            <tr key={row.direction + row.preferenceCode}>
              <td>{row.direction}</td><td>{row.preferenceCode}</td><td>{row.targetFeatureCode}</td>
              <td>{row.importance}</td><td>{row.satisfaction}</td><td>{row.score ?? '无'}</td>
              <td><button type="button" onClick={() => setItem(row)}>证据</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      {item && (
        <aside className="lab-drawer">
          <h2>证据</h2>
          <p>{item.preferenceCode} → {item.targetFeatureCode}</p>
          <p>状态 {item.evidenceStatus} · 原因 {item.reasonCode || '无'}</p>
          <p>满足 {item.satisfaction} · 单项 {item.score ?? '无'}</p>
          <button type="button" onClick={() => setItem(null)}>关闭</button>
        </aside>
      )}
      <details>
        <summary>原始 JSON</summary>
        <pre>{JSON.stringify(score, null, 2)}</pre>
      </details>
    </section>
  );
}

function ReasoningPage() {
  const [data, setData] = useState<Reasoning | null>(null);
  const [error, setError] = useState<unknown>(null);
  return (
    <>
      <header className="lab-top"><h1>AI 关系推理</h1><span>真实模型 NOT_RUN · C1/C2 关闭</span></header>
      <button type="button" onClick={() => labApi.reasoning().then(setData).catch(setError)}>回放冻结判断</button>
      {error ? <Gate error={error} /> : null}
      {data && (
        <>
          <p>{data.executionMode} · {data.modelName} · {data.promptVersion} · 真实模型 {data.realModelStatus}</p>
          <p>{data.kind} · {data.note}</p>
          <p>M02 聚合 {data.m02Score ?? '无'} · AI 加分 {data.aiAdjustment ? '开启' : '关闭'}</p>
          <div className="lab-split">
            <Agent title="Agent A" status={data.statusA} side={data.agentA} />
            <Agent title="Agent B" status={data.statusB} side={data.agentB} />
          </div>
        </>
      )}
    </>
  );
}

function Agent({ title, status, side }: { title: string; status: string; side: Reasoning['agentA'] }) {
  return (
    <section>
      <h2>{title}</h2>
      <p>校验 {status}</p>
      <p>证据 {side.evidence.join('，') || '无'}</p>
      <ul>{side.supported.map((item) => <li key={item}>判断 {item}</li>)}</ul>
      <ul>{side.complements.map((item) => <li key={item}>互补 {item}</li>)}</ul>
      <ul>{side.conflicts.map((item) => <li key={item}>冲突 {item}</li>)}</ul>
      <ul>{side.unknown.map((item) => <li key={item}>未知 {item}</li>)}</ul>
      <ul>{side.rejected.map((item) => <li key={item}>拒绝 {item}</li>)}</ul>
      <ul>{side.unsupported.map((item) => <li key={item}>无依据 {item}</li>)}</ul>
      {side.unsupported.length === 0 && <p>无依据推断：这条回放没有写出无依据判断。</p>}
    </section>
  );
}

export function MatchLabRoutes() {
  return (
    <>
      <Route path="/match-lab" element={<Shell><OverviewPage /></Shell>} />
      <Route path="/match-lab/profiles" element={<Shell><ProfilesPage /></Shell>} />
      <Route path="/match-lab/candidates" element={<Shell><CandidatesPage /></Shell>} />
      <Route path="/match-lab/score" element={<Shell><ScorePage /></Shell>} />
      <Route path="/match-lab/reasoning" element={<Shell><ReasoningPage /></Shell>} />
      <Route path="/match-lab/global" element={<Shell><GlobalPage /></Shell>} />
      <Route path="/match-lab/feedback" element={<Shell><FeedbackPage /></Shell>} />
      <Route path="/match-lab/learning" element={<Shell><LearningPage /></Shell>} />
      <Route path="/match-lab/strategy" element={<Shell><StrategyPage /></Shell>} />
      <Route path="/match-lab/replay" element={<Shell><ReplayPage /></Shell>} />
      <Route path="/match-lab/safety" element={<Shell><SafetyPage /></Shell>} />
    </>
  );
}
