import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, Route } from 'react-router';
import { ApiError } from '../api/http';
import { labApi, type Overview, type Profile, type Recall, type Reasoning, type RegistryFeature, type Score } from './api';
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
  useEffect(() => { labApi.overview().then(setData).catch(setError); }, []);
  if (error) return <Gate error={error} />;
  if (!data) return <p>正在读取实验目录。</p>;
  return (
    <>
      <header className="lab-top">
        <h1>实验总览</h1>
        <span>{data.environment}</span>
        <span>生产启用 {data.production}</span>
        <span>真实关系质量 {data.realRelationshipQuality}</span>
      </header>
      <section className="lab-cards">
        <article><b>{data.profiles}</b><span>虚构画像</span></article>
        <article><b>{data.runs}</b><span>本次运行</span></article>
        <article><b>{data.catalog.length}</b><span>目录条目</span></article>
        <article><b>{data.commitSha}</b><span>提交标记</span></article>
      </section>
      <ul className="lab-limits">{data.limits.map((item) => <li key={item}>{item}</li>)}</ul>
      <table>
        <thead><tr><th>实验</th><th>模块</th><th>来源</th><th>执行</th><th>质量</th><th>限制</th></tr></thead>
        <tbody>
          {data.catalog.map((entry) => (
            <tr key={entry.experimentId}>
              <td>{entry.title}<small>{entry.datasetVersion}</small></td>
              <td>{entry.module}</td>
              <td>{entry.sourceType}</td>
              <td>{entry.executionMode}</td>
              <td>{entry.qualityStatus}</td>
              <td>{entry.limitations}<small>{entry.metrics.join(' · ')}</small></td>
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

  async function load() {
    const [people, features] = await Promise.all([labApi.profiles(), labApi.registry()]);
    setProfiles(people);
    setRegistry(features);
    setSelected(people[0]?.userId ?? null);
  }
  useEffect(() => { load().catch(setError); }, []);
  if (error) return <Gate error={error} />;
  const person = profiles.find((item) => item.userId === selected);
  const definition = registry.find((item) => item.featureCode === code);

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
              <p>{person.fixtureSource} · 摘要 {person.digest}</p>
              <table>
                <thead><tr><th>特征</th><th>分区</th><th>值</th><th>状态</th><th>可匹配</th></tr></thead>
                <tbody>
                  {person.features.map((feature) => (
                    <tr key={feature.section + feature.featureCode}>
                      <td>{feature.displayName}<small>{feature.featureCode}</small></td>
                      <td>{feature.section}</td>
                      <td>{feature.value || '空'} {feature.operator} {feature.importance}</td>
                      <td>{feature.status}</td>
                      <td>{feature.usableForMatching ? '允许' : '不允许'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const current = data?.schemes.find((item) => item.scheme === scheme);
  return (
    <>
      <header className="lab-top"><h1>候选发现</h1><span>冻结开发集 · 本次引擎执行</span></header>
      <div className="lab-form">
        <select value={scheme} onChange={(event) => setScheme(event.target.value)} aria-label="候选策略">
          <option value="RRF">RRF-only</option>
          <option value="RRF_M02">RRF + M02</option>
          <option value="FULL_POOL_M02">完整合法池 + M02</option>
          <option value="SHORTLIST_M02">完整画像短名单 + M02</option>
        </select>
        <button type="button" disabled={busy} onClick={() => {
          setBusy(true);
          labApi.recall().then(setData).catch(setError).finally(() => setBusy(false));
        }}>{busy ? '正在计算' : '执行对照'}</button>
      </div>
      {error ? <Gate error={error} /> : null}
      {data && (
        <>
          <p>{data.executionMode} · {data.dataset} · {data.people} 人 · 运行 {data.runId}</p>
          <section className="lab-cards">
            {data.schemes.map((item) => (
              <article key={item.scheme}><b>{item.recommended}</b><span>{item.scheme} 可推荐</span><small>证据不足 {item.insufficient} · 硬约束 {item.hardRejected}</small></article>
            ))}
          </section>
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
          const self = person.features.filter((feature) => feature.section === 'SELF').map((feature) => ({ code: feature.featureCode, value: feature.value, status: feature.status, operator: 'EQ' }));
          const preferences = person.features.filter((feature) => feature.section === 'PREFERENCE').map((feature) => ({
            code: feature.featureCode,
            value: feature.featureCode === 'partner.pace' ? 'fast' : feature.value,
            status: feature.status,
            importance: feature.importance || 'SUPER',
            operator: 'EQ',
          }));
          labApi.update(right, { name: person.name, self, preferences }).then(() => labApi.score(left, right)).then(setAfter).catch(setError);
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
  if (!score) return <p>还没有执行。</p>;
  return (
    <section>
      <h2>{title}</h2>
      <p>{score.executionMode} · {score.formulaVersion} · {score.policyVersion}</p>
      <p>A→B {score.scoreAB ?? '无'} · B→A {score.scoreBA ?? '无'} · 聚合 {score.aggregate ?? '无'}</p>
      <p>覆盖 {score.coverageAB ?? '无'} / {score.coverageBA ?? '无'}</p>
      <p>画像版本 {score.versionA} / {score.versionB}</p>
      <p>{score.recommendationEligible ? '可推荐' : '拒绝推荐'} · {score.reasons.join('，')}</p>
      {score.hardFilter.length > 0 && <p>硬约束 {score.hardFilter.join('，')}</p>}
      <table>
        <thead><tr><th>方向</th><th>偏好</th><th>目标</th><th>重要性</th><th>满足</th><th>分数</th></tr></thead>
        <tbody>
          {score.items.map((item) => (
            <tr key={item.direction + item.preferenceCode}>
              <td>{item.direction}</td><td>{item.preferenceCode}</td><td>{item.targetFeatureCode}</td>
              <td>{item.importance}</td><td>{item.satisfaction}</td><td>{item.score ?? '无'}</td>
            </tr>
          ))}
        </tbody>
      </table>
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
    </>
  );
}
