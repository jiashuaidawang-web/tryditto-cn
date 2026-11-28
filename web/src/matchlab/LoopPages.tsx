import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../api/http';
import { labApi, type FeedbackView, type GlobalView, type LabRun, type LearningView, type Profile, type RematchView } from './api';

function Gate({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return <p className="lab-error">加载失败。</p>;
  return <p className="lab-error">{error.message}</p>;
}

function JsonBlock({ value }: { value: unknown }) {
  return (
    <details>
      <summary>原始 JSON</summary>
      <pre>{JSON.stringify(value, null, 2)}</pre>
    </details>
  );
}

export function GlobalPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [picked, setPicked] = useState<number[]>([]);
  const [objective, setObjective] = useState('G2');
  const [focus, setFocus] = useState<number | null>(null);
  const [data, setData] = useState<GlobalView | null>(null);
  const [history, setHistory] = useState<LabRun[]>([]);
  const [note, setNote] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    labApi.profiles().then((rows) => {
      setProfiles(rows);
      setPicked(rows.map((item) => item.userId));
      setFocus(rows[0]?.userId ?? null);
    }).catch(setError);
    labApi.runs().then((rows) => setHistory(rows.filter((item) => item.experimentType === 'GLOBAL_MATCH'))).catch(setError);
  }, []);

  function toggle(userId: number) {
    setPicked((current) => current.includes(userId) ? current.filter((item) => item !== userId) : [...current, userId]);
  }

  async function run(kind: 'sandbox' | 'math' | string) {
    setBusy(true);
    setError(null);
    try {
      if (kind === 'sandbox') {
        const result = await labApi.global(objective, picked);
        setData(result);
        setNote(result.limitations);
        setHistory(await labApi.runs());
      } else if (kind === 'math') {
        const result = await labApi.compare();
        setData(result);
        setNote('这是数学夹具对照，不是当前工作区画像。它不能进入反馈。');
      } else {
        const result = await labApi.runCase(kind);
        setNote(`${result.code} · ${result.executionMode} · ${result.observed ? '观察到预期差异' : '没有观察到预期差异'} · ${result.note}`);
      }
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  const visible = data?.edges.filter((edge) => focus == null || edge.lowId === focus || edge.highId === focus) ?? [];
  return (
    <>
      <header className="lab-top"><h1>全局匹配</h1><span>策略 E · 默认 G2 · 不改算法权重</span></header>
      <p className="lab-note">用户只能来自当前工作区。不合格边不会交给求解器。SAFETY_REJECTED、CAP_REACHED、TIMEOUT、ABORTED 和截断图不能进入反馈。</p>
      {error ? <Gate error={error} /> : null}
      <div className="lab-form">
        <select value={objective} onChange={(event) => setObjective(event.target.value)} aria-label="全局目标">
          <option value="G0">G0 贪心</option>
          <option value="G1">G1 最大总权重</option>
          <option value="G2">G2 匹配人数优先</option>
          <option value="G3">G3 质量层对照</option>
          <option value="G4">G4 公平对照</option>
        </select>
        <button type="button" disabled={busy} onClick={() => run('sandbox')}>执行沙箱全局匹配</button>
        <button type="button" disabled={busy} onClick={() => run('math')}>执行案例 A 数学对照</button>
        {['B', 'C', 'D', 'E', 'F'].map((code) => (
          <button key={code} type="button" disabled={busy} onClick={() => run(code)}>案例 {code}</button>
        ))}
      </div>
      <div className="lab-form">
        {profiles.map((person) => (
          <label key={person.userId}>
            <input type="checkbox" checked={picked.includes(person.userId)} onChange={() => toggle(person.userId)} />
            {person.name}
          </label>
        ))}
      </div>
      {!data && !busy ? <p>还没有执行。选择工作区里的虚构用户后再求解。</p> : null}
      {note ? <p className="lab-note">{note}</p> : null}
      <h2>历史运行</h2>
      {history.filter((item) => item.experimentType === 'GLOBAL_MATCH').length === 0 ? <p>还没有可回看的全局匹配。</p> : (
        <table>
          <thead><tr><th>运行</th><th>状态</th><th>输入摘要</th><th>结果摘要</th></tr></thead>
          <tbody>
            {history.filter((item) => item.experimentType === 'GLOBAL_MATCH').map((item) => (
              <tr key={item.runId}><td>{item.runId}</td><td>{item.status}</td><td>{item.inputHash}</td><td>{item.resultHash}</td></tr>
            ))}
          </tbody>
        </table>
      )}
      {data && (
        <>
          <section className="lab-cards">
            <article><b>{data.initialUsers}</b><span>初始用户</span></article>
            <article><b>{data.solverEdges}</b><span>推荐合格边</span></article>
            <article><b>{data.pairsCount}</b><span>成功配对</span></article>
            <article><b>{data.unmatchedCount}</b><span>未匹配</span></article>
            <article><b>{data.status}</b><span>求解状态</span></article>
            <article><b>{data.solveMs} ms</b><span>求解时间</span></article>
          </section>
          <p>{data.executionMode} · {data.objective} · 覆盖 {data.coverage} · 安全违规 {data.safetyViolations} · 平均契合 {data.averageFit} · 最低契合 {data.lowestFit} · 总权重 {data.totalWeight} · 可继续 {data.usablePlan ? '可以' : '不可以'}</p>
          <p>运行 {data.runId}{data.parentRunId ? ` · 上一次 ${data.parentRunId}` : ''}</p>
          <p>输入摘要 {data.inputHash}</p>
          <p>语义结果 {data.semanticResultHash}</p>
          <h2>配对</h2>
          <table>
            <thead><tr><th>低编号</th><th>高编号</th><th>契合</th><th>双向分数</th><th>覆盖</th><th></th></tr></thead>
            <tbody>
              {data.pairs.map((pair) => (
                <tr key={pair.key}>
                  <td>{pair.lowId}</td><td>{pair.highId}</td><td>{pair.fit ?? '无'}</td>
                  <td>{pair.scoreLowToHigh ?? '无'} / {pair.scoreHighToLow ?? '无'}</td>
                  <td>{pair.coverageLowToHigh ?? '无'} / {pair.coverageHighToLow ?? '无'}</td>
                  <td>{data.usablePlan ? <Link to={`/match-lab/feedback?run=${data.runId}&a=${pair.lowId}&b=${pair.highId}`}>进入反馈</Link> : '不可进入反馈'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <h2>未匹配</h2>
          {data.unmatched.length === 0 ? <p>这一次没有未匹配用户。</p> : (
            <table>
              <thead><tr><th>用户</th><th>求解器记录的原因</th><th>诊断</th></tr></thead>
              <tbody>{data.unmatched.map((person) => <tr key={person.userId}><td>{person.userId}</td><td>{person.reason}</td><td>{person.diagnostics.join('，')}</td></tr>)}</tbody>
            </table>
          )}
          <p className="lab-note">原因来自求解器写下的诊断。它不是另选一条边的全局代价证明。</p>
          <div className="lab-form">
            <label>查看这个用户的边
              <select value={focus ?? ''} onChange={(event) => setFocus(Number(event.target.value))} aria-label="焦点用户">
                {profiles.map((person) => <option key={person.userId} value={person.userId}>{person.name}</option>)}
              </select>
            </label>
          </div>
          <table>
            <thead><tr><th>边</th><th>进入求解</th><th>版本</th><th>代次</th><th>排除</th></tr></thead>
            <tbody>
              {visible.map((edge) => (
                <tr key={`${edge.lowId}-${edge.highId}-${edge.solver}`}>
                  <td>{edge.lowId}–{edge.highId}</td>
                  <td>{edge.solver ? '是' : '否'}</td>
                  <td>{edge.versionLow}/{edge.versionHigh}</td>
                  <td>{edge.generationLow}/{edge.generationHigh}</td>
                  <td>{edge.exclusion || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.comparison && <p>贪心对照运行 {data.comparison.runId}，配对 {data.comparison.pairs.map((pair) => pair.key).join('，') || '无'}。</p>}
          <JsonBlock value={data} />
        </>
      )}
    </>
  );
}

const actions = [
  ['view', '查看'], ['accept', '接受'], ['decline', '拒绝'], ['timeout', '超时'],
  ['schedule', '安排见面'], ['cancel', '取消见面'], ['attended', '报告已见面'], ['missed', '报告未见面'],
  ['continue', '愿意继续'], ['stop', '不愿继续'], ['correct', '更正上一条'], ['retract', '撤回'], ['revoke', '撤回授权'],
] as const;

export function FeedbackPage() {
  const [params] = useSearchParams();
  const [data, setData] = useState<FeedbackView | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [lastEvent, setLastEvent] = useState('');

  useEffect(() => {
    const run = params.get('run');
    const a = Number(params.get('a'));
    const b = Number(params.get('b'));
    if (!run || !a || !b) return;
    labApi.openFeedback(run, a, b).then(setData).catch(setError);
  }, [params]);

  async function send(action: string, actor: number) {
    if (!data) return;
    setError(null);
    try {
      const next = await labApi.act(data.ledgerKey, action, actor, action === 'correct' ? lastEvent : undefined);
      setData(next);
      const newest = next.events[next.events.length - 1];
      if (newest) setLastEvent(newest.eventId);
    } catch (failure) {
      setError(failure);
    }
  }

  return (
    <>
      <header className="lab-top"><h1>反馈事件</h1><span>F01 账本 · 系统事件由服务端写入</span></header>
      {error ? <Gate error={error} /> : null}
      {!data ? <p>还没有打开一对匹配。从全局匹配里选择一对可继续的结果。</p> : (
        <>
          <p>运行 {data.matchingRunId} · {data.userA} / {data.userB} · 匹配时画像 v{data.versionA} / v{data.versionB}</p>
          <p>响应 {data.projection.responseA} / {data.projection.responseB} · 互相接受 {data.projection.mutualAcceptance ? '是' : '否'} · 见面 {data.projection.schedule} · 出席 {data.projection.attendance} · 争议 {data.projection.contested ? 'CONTESTED' : '无'} · 继续 {data.projection.continueA} / {data.projection.continueB}</p>
          <p>未知 {data.projection.unknown.join('，') || '无'} · 学习资格 {data.projection.learningAllowed ? '允许' : '不允许'} {data.projection.learningReasons.join('，')}</p>
          <div className="lab-split">
            <section>
              <h2>用户 {data.userA}</h2>
              {actions.map(([action, label]) => <button key={action} type="button" onClick={() => send(action, data.userA)}>{label}</button>)}
            </section>
            <section>
              <h2>用户 {data.userB}</h2>
              {actions.map(([action, label]) => <button key={action} type="button" onClick={() => send(action, data.userB)}>{label}</button>)}
            </section>
          </div>
          <h2>时间线</h2>
          <table>
            <thead><tr><th>编号</th><th>类型</th><th>行动者</th><th>发生</th><th>记录</th><th>来源</th><th>有效</th><th>因果</th><th>更正</th></tr></thead>
            <tbody>
              {data.events.map((event) => (
                <tr key={event.eventId}>
                  <td>{event.eventId}</td><td>{event.type}</td><td>{event.actorUserId}</td>
                  <td>{event.occurredAt}</td><td>{event.recordedAt}</td><td>{event.source}</td>
                  <td>{event.effective ? '有效' : '无效'}</td><td>{event.causationEventId || '—'}</td>
                  <td>{event.superseded ? '已更正' : event.supersedesEventId || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p><Link to={`/match-lab/learning?ledger=${encodeURIComponent(data.ledgerKey)}&user=${data.userB}&run=${data.labRunId}`}>进入画像学习</Link></p>
          <button type="button" onClick={() => {
            const accept = data.events.find((event) => event.type === 'MATCH_ACCEPTED' && event.actorUserId === data.userA);
            if (!accept) {
              setError(new ApiError(422, 'SOURCE', '还没有这条匹配的接受事件'));
              return;
            }
            labApi.propose(data.userA, 'partner.pace', 'fast', accept.eventId, data.ledgerKey, 'FEEDBACK').catch(setError);
          }}>把接受事件当作偏好来源</button>
          <JsonBlock value={data} />
        </>
      )}
    </>
  );
}

export function LearningPage() {
  const [params] = useSearchParams();
  const [data, setData] = useState<LearningView | null>(null);
  const [rematch, setRematch] = useState<RematchView | null>(null);
  const [error, setError] = useState<unknown>(null);
  const ledger = params.get('ledger') || '';
  const userId = Number(params.get('user') || 0);
  const runId = params.get('run') || '';

  useEffect(() => {
    if (!ledger || !userId) return;
    labApi.learning(ledger, userId).then(setData).catch(setError);
  }, [ledger, userId]);

  async function propose() {
    setError(null);
    try {
      setData(await labApi.propose(userId, 'partner.pace', 'fast', ''));
    } catch (failure) {
      setError(failure);
    }
  }

  async function decide(action: string) {
    if (!data?.proposal) return;
    setError(null);
    try {
      setData(await labApi.decide(data.proposal.proposalId, action, userId));
    } catch (failure) {
      setError(failure);
    }
  }

  async function again() {
    if (!runId) return;
    setError(null);
    try {
      setRematch(await labApi.rematch(runId, 'G2'));
    } catch (failure) {
      setError(failure);
    }
  }

  return (
    <>
      <header className="lab-top"><h1>画像学习</h1><span>F02 · 待确认假设不进评分</span></header>
      {error ? <Gate error={error} /> : null}
      {!ledger ? <p>还没有选定反馈。从反馈页进入一个虚构用户。</p> : null}
      {data && (
        <>
          <p>用户 {data.userId} · 画像 v{data.profileVersion} · 代次 {data.generation}</p>
          <p className="lab-note">{data.limitations}</p>
          <h2>学习信号</h2>
          {data.signals.length === 0 ? <p>这条账本上还没有可展示的信号。</p> : (
            <table>
              <thead><tr><th>来源</th><th>类别</th><th>目标</th><th>证据</th><th>授权</th></tr></thead>
              <tbody>{data.signals.map((signal) => <tr key={signal.sourceEventId + signal.category}><td>{signal.sourceEventId}</td><td>{signal.category}</td><td>{signal.target || '—'}</td><td>{signal.evidenceState}</td><td>{signal.authorization}</td></tr>)}</tbody>
            </table>
          )}
          <p>不可直接学习：{data.unlearnable.join('；') || '无'}</p>
          <div className="lab-form">
            <button type="button" onClick={propose}>生成相处节奏提案</button>
            <button type="button" onClick={() => decide('confirm')}>模拟用户确认</button>
            <button type="button" onClick={() => decide('reject')}>拒绝提案</button>
            <button type="button" onClick={() => decide('expire')}>提案过期</button>
            <button type="button" onClick={again}>用新画像重新匹配</button>
          </div>
          {data.proposal && <p>提案 {data.proposal.proposalId} · {data.proposal.featureCode} {data.proposal.oldValue || '空'} → {data.proposal.proposedValue} · {data.proposal.status}</p>}
          <h2>变更前后</h2>
          <table>
            <thead><tr><th>特征</th><th>之前</th><th>之后</th></tr></thead>
            <tbody>
              {data.before.map((row) => {
                const after = data.after.find((item) => item.featureCode === row.featureCode);
                return <tr key={row.featureCode}><td>{row.featureCode}</td><td>{row.value}</td><td>{after?.value || '不再进入评分'}</td></tr>;
              })}
            </tbody>
          </table>
          <JsonBlock value={data} />
        </>
      )}
      {rematch && (
        <>
          <h2>重新匹配</h2>
          <p>旧运行 {rematch.parentRunId} 保持不变。新运行 {rematch.child.runId} · {rematch.child.status} · 父运行 {rematch.child.parentRunId}</p>
          <p>之前配对 {rematch.pairsBefore.join('，') || '无'}</p>
          <p>之后配对 {rematch.pairsAfter.join('，') || '无'}</p>
          <p className="lab-note">{rematch.limitations}</p>
          <JsonBlock value={rematch} />
        </>
      )}
    </>
  );
}
