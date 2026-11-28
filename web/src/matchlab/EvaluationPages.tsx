import { useEffect, useState } from 'react';
import { ApiError } from '../api/http';
import { labApi, type CatalogEntry, type CompareResult, type LabRun, type ProductionGate, type ReplayResult, type SafetyReport, type StrategyReport } from './api';

function Gate({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return <p className="lab-error">加载失败。</p>;
  return <p className="lab-error">{error.message}</p>;
}

function JsonBlock({ value }: { value: unknown }) {
  return <details><summary>原始 JSON</summary><pre>{JSON.stringify(value, null, 2)}</pre></details>;
}

export function StrategyPage() {
  const [source, setSource] = useState('FROZEN_EXPERIMENT');
  const [data, setData] = useState<StrategyReport | null>(null);
  const [error, setError] = useState<unknown>(null);

  function load(next: string) {
    setSource(next);
    setError(null);
    labApi.strategy(next).then(setData).catch(setError);
  }

  useEffect(() => { load('FROZEN_EXPERIMENT'); }, []);

  async function activate() {
    setError(null);
    try {
      await labApi.activateStrategy();
    } catch (failure) {
      setError(failure);
    }
  }

  return (
    <>
      <header className="lab-top"><h1>策略学习</h1><span>{data?.source ?? '读取中'} · 标签 {data?.labelVersion ?? ''}</span></header>
      <p className="lab-note">冻结实验和沙箱观察分开。未知、过期、争议和撤回保持原状态。这里不能激活策略，也不能改 M02 或 M04 的权重。</p>
      {error ? <Gate error={error} /> : null}
      <div className="lab-form">
        <button type="button" onClick={() => load('FROZEN_EXPERIMENT')}>冻结实验</button>
        <button type="button" onClick={() => load('SYNTHETIC_SANDBOX')}>沙箱观察</button>
        <button type="button" onClick={activate}>尝试激活策略</button>
      </div>
      {!data ? <p>正在读取观察。</p> : (
        <>
          <p>来源 {data.source} · 数据集 {data.datasetVersion} · 观察 {data.observations} · 当前选择 {source}</p>
          <p className="lab-note">{data.labelNote}</p>
          <p className="lab-note">{data.limitations}</p>
          <h2>匹配级计数</h2>
          <table>
            <thead><tr><th>指标</th><th>级别</th><th>分子</th><th>分母</th><th>窗口</th><th>缺失</th><th>争议</th><th>未成熟</th><th>撤回</th><th>授权</th></tr></thead>
            <tbody>
              {data.counts.map((row) => (
                <tr key={row.metric}><td>{row.metric}</td><td>{row.level}</td><td>{row.numerator}</td><td>{row.denominator}</td><td>{row.window}</td><td>{row.missing}</td><td>{row.contested}</td><td>{row.immature}</td><td>{row.retracted}</td><td>{row.authorization}</td></tr>
              ))}
            </tbody>
          </table>
          <h2>校准</h2>
          <p className="lab-note">{data.calibration[0]?.note}</p>
          <p className="lab-note">SCORE_AB_ACCEPT_A 是 A→B 分数对应的 A 接受。SCORE_BA_ACCEPT_B 是 B→A 分数对应的 B 接受。RECIPROCAL_MUTUAL、ATTENDANCE、CONTINUE 分别对应 Y1、Y2、Y3。数据不足时状态是 INSUFFICIENT，不画曲线。</p>
          <table>
            <thead><tr><th>目标</th><th>方法</th><th>状态</th><th>样本</th><th>Brier</th><th>Log Loss</th><th>校准误差</th></tr></thead>
            <tbody>
              {data.calibration.map((row) => (
                <tr key={row.target + row.method}><td>{row.target}</td><td>{row.method}</td><td>{row.status}</td><td>{row.sample}</td><td>{row.brier ?? '—'}</td><td>{row.logLoss ?? '—'}</td><td>{row.calibrationError ?? '—'}</td></tr>
              ))}
            </tbody>
          </table>
          <h2>策略对照</h2>
          <table>
            <thead><tr><th>策略</th><th>已发布</th><th>双方接受</th><th>双方拒绝</th><th>非二分类</th><th>反事实</th><th>真实提升</th></tr></thead>
            <tbody>
              {data.strategies.map((row) => (
                <tr key={row.strategy}><td>{row.strategy}</td><td>{row.published}</td><td>{row.bothAccepted}</td><td>{row.bothDeclined}</td><td>{row.notBinary}</td><td>{row.counterfactual ? '有' : '无'}</td><td>{row.realLift}</td></tr>
              ))}
            </tbody>
          </table>
          <p>提案 {data.proposal.proposalId} · {data.proposal.status} · {data.proposal.proposedChange}</p>
          <JsonBlock value={data} />
        </>
      )}
    </>
  );
}

export function ReplayPage() {
  const [runs, setRuns] = useState<LabRun[]>([]);
  const [catalog, setCatalog] = useState<CatalogEntry[]>([]);
  const [moduleFilter, setModuleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [selected, setSelected] = useState('');
  const [other, setOther] = useState('');
  const [replay, setReplay] = useState<ReplayResult | null>(null);
  const [compared, setCompared] = useState<CompareResult | null>(null);
  const [exported, setExported] = useState('');
  const [error, setError] = useState<unknown>(null);

  function refresh() {
    labApi.runs().then((rows) => {
      setRuns(rows);
      setSelected((current) => current || rows[0]?.runId || '');
      setOther((current) => current || rows[1]?.runId || rows[0]?.runId || '');
    }).catch(setError);
    labApi.overview().then((body) => setCatalog(body.catalog)).catch(setError);
  }

  useEffect(() => { refresh(); }, []);
  const current = runs.find((run) => run.runId === selected);
  const modules = ['ALL', ...Array.from(new Set(catalog.map((item) => item.module)))];
  const statuses = ['ALL', ...Array.from(new Set(catalog.map((item) => item.executionStatus)))];
  const types = ['ALL', ...Array.from(new Set(runs.map((item) => item.experimentType)))];
  const visibleCatalog = catalog.filter((item) => (moduleFilter === 'ALL' || item.module === moduleFilter) && (statusFilter === 'ALL' || item.executionStatus === statusFilter));
  const visibleRuns = runs.filter((item) => typeFilter === 'ALL' || item.experimentType === typeFilter);

  return (
    <>
      <header className="lab-top"><h1>实验回放</h1><span>冻结结果和本次引擎结果分开</span></header>
      <p className="lab-note">查看历史运行不会改当前画像。重新执行使用当前引擎和已保存输入，不检出旧的 Git 版本。父运行被回收时返回 HISTORY_UNAVAILABLE，不另造一份结果。</p>
      {error ? <Gate error={error} /> : null}
      <h2>冻结目录</h2>
      <div className="lab-form">
        <select value={moduleFilter} onChange={(event) => setModuleFilter(event.target.value)} aria-label="模块">
          {modules.map((item) => <option key={item} value={item}>{item === 'ALL' ? '全部模块' : item}</option>)}
        </select>
        <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} aria-label="实验状态">
          {statuses.map((item) => <option key={item} value={item}>{item === 'ALL' ? '全部状态' : item}</option>)}
        </select>
      </div>
      {visibleCatalog.length === 0 ? <p>没有符合筛选的冻结实验。</p> : (
        <table>
          <thead><tr><th>实验</th><th>模块</th><th>来源</th><th>模式</th><th>状态</th><th>限制</th></tr></thead>
          <tbody>
            {visibleCatalog.map((item) => (
              <tr key={item.experimentId}><td>{item.title}</td><td>{item.module}</td><td>{item.sourceType}</td><td>{item.executionMode}</td><td>{item.executionStatus}</td><td>{item.limitations}</td></tr>
            ))}
          </tbody>
        </table>
      )}
      <h2>本次工作区</h2>
      <div className="lab-form">
        <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)} aria-label="运行类型">
          {types.map((item) => <option key={item} value={item}>{item === 'ALL' ? '全部类型' : item}</option>)}
        </select>
      </div>
      {visibleRuns.length === 0 ? <p>这个工作区还没有运行。</p> : (
        <>
          <div className="lab-form">
            <select value={selected} onChange={(event) => setSelected(event.target.value)} aria-label="历史运行">
              {visibleRuns.map((run) => <option key={run.runId} value={run.runId}>{run.experimentType} {run.runId}</option>)}
            </select>
            <select value={other} onChange={(event) => setOther(event.target.value)} aria-label="对照运行">
              {visibleRuns.map((run) => <option key={run.runId} value={run.runId}>{run.experimentType} {run.runId}</option>)}
            </select>
            <button type="button" onClick={() => labApi.replay(selected).then((result) => { setReplay(result); refresh(); }).catch(setError)}>用当前引擎重放</button>
            <button type="button" onClick={() => labApi.compareRuns(selected, other).then(setCompared).catch(setError)}>对照两次运行</button>
            <button type="button" onClick={() => labApi.exportRun(selected).then((body) => setExported(body.document)).catch(setError)}>导出合成 JSON</button>
          </div>
          <h2>运行树</h2>
          <table>
            <thead><tr><th>运行</th><th>父运行</th><th>类型</th><th>模式</th><th>状态</th><th>输入摘要</th><th>结果摘要</th></tr></thead>
            <tbody>
              {visibleRuns.map((run) => (
                <tr key={run.runId}><td>{run.runId}</td><td>{run.parentRunId || '—'}</td><td>{run.experimentType}</td><td>{run.executionMode}</td><td>{run.status}</td><td>{run.inputHash}</td><td>{run.resultHash}</td></tr>
              ))}
            </tbody>
          </table>
          {current && (
            <>
              <h2>快照 {current.runId}</h2>
              <pre>{current.inputSnapshot}</pre>
              <pre>{current.result}</pre>
            </>
          )}
          {replay && <p>回放 {replay.replayRunId} · {replay.executionMode} · 语义一致 {replay.semanticMatch ? '是' : '否'} · {replay.limitations}</p>}
          {compared && <p>对照 {compared.leftRunId} / {compared.rightRunId} · 输入相同 {compared.sameInput ? '是' : '否'} · 策略相同 {compared.samePolicy ? '是' : '否'} · 语义一致 {compared.sameSemanticResult ? '是' : '否'} · {compared.note}</p>}
          {exported && <pre>{exported}</pre>}
        </>
      )}
    </>
  );
}

export function SafetyPage() {
  const [data, setData] = useState<SafetyReport | null>(null);
  const [gate, setGate] = useState<ProductionGate | null>(null);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    labApi.safety().then(setData).catch(setError);
    labApi.productionGate().then(setGate).catch(setError);
  }, []);
  return (
    <>
      <header className="lab-top"><h1>安全审计</h1><span>生产启用 {gate?.production ?? '读取中'}</span></header>
      <p className="lab-note">运行级结果、历史风险和质量闸门分开显示。这个页面不能修改生产开关。</p>
      {error ? <Gate error={error} /> : null}
      {!data || !gate ? <p>正在读取审计。</p> : (
        <>
          <section className="lab-cards">
            <article><b>{data.plans}</b><span>工作区计划</span></article>
            <article><b>{data.safetyViolations}</b><span>安全违规</span></article>
            <article><b>{data.production}</b><span>生产启用</span></article>
            <article><b>{data.realRelationshipQuality}</b><span>真实关系质量</span></article>
            <article><b>{data.realLift}</b><span>真实策略提升</span></article>
            <article><b>{data.algorithmQuality}</b><span>最终算法质量</span></article>
          </section>
          <h2>质量闸门</h2>
          <table>
            <thead><tr><th>闸门</th><th>状态</th><th>说明</th></tr></thead>
            <tbody>
              {data.gates.map((gate) => (
                <tr key={gate.name}><td>{gate.name}</td><td>{gate.status}</td><td>{gate.note}</td></tr>
              ))}
            </tbody>
          </table>
          <h2>接入前条件</h2>
          <ul className="lab-limits">{gate.conditions.map((item) => <li key={item}>{item}</li>)}</ul>
          <h2>风险登记</h2>
          <table>
            <thead><tr><th>编号</th><th>模块</th><th>优先级</th><th>说明</th><th>状态</th><th>范围</th><th>解决条件</th><th>来源</th></tr></thead>
            <tbody>
              {data.risks.map((risk) => (
                <tr key={risk.riskId}><td>{risk.riskId}</td><td>{risk.module}</td><td>{risk.priority}</td><td>{risk.statement}</td><td>{risk.status}</td><td>{risk.scope}</td><td>{risk.resolution}</td><td>{risk.source}</td></tr>
              ))}
            </tbody>
          </table>
          <JsonBlock value={{ data, gate }} />
        </>
      )}
    </>
  );
}
