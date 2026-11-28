import { http } from '../api/http';

export type CatalogEntry = {
  experimentId: string;
  module: string;
  title: string;
  description: string;
  datasetVersion: string;
  datasetIdentityHash: string;
  datasetContentHash: string;
  commitSha: string;
  catalogIntegrationCommit: string;
  policyVersion: string;
  sourceType: string;
  executionMode: string;
  executionStatus: string;
  qualityStatus: string;
  limitations: string;
  metrics: string[];
  caseIds: string[];
};

export type PolicyNote = { policy: string; experimentId: string; note: string };

export type Overview = {
  environment: string;
  enabled: boolean;
  commitSha: string;
  frozenCommit: string;
  researchEnvironment: string;
  storage: string;
  workspace: { workspaceId: string; ownerPrincipalId: number; mode: string; revision: number; expiresAt: string; storage: string };
  ciIndex: string;
  statusDocument: string;
  production: string;
  realRelationshipQuality: string;
  policies: PolicyNote[];
  catalog: CatalogEntry[];
  profiles: number;
  runs: number;
  limits: string[];
};

export type LabFeature = {
  featureCode: string;
  displayName: string;
  section: string;
  value: string;
  operator: string;
  importance: string;
  evidenceSource: string;
  status: string;
  usableForMatching: boolean;
};

export type Profile = {
  userId: number;
  name: string;
  version: number;
  digest: string;
  fixtureSource: string;
  mode: string;
  features: LabFeature[];
};

export type RegistryFeature = {
  featureCode: string;
  displayName: string;
  valueType: string;
  allowedValues: string[];
  sections: string[];
};

export type ScoreItem = {
  direction: string;
  preferenceCode: string;
  targetFeatureCode: string;
  importance: string;
  satisfaction: string;
  score: number | null;
  evidenceStatus: string;
  reasonCode: string;
};

export type Score = {
  runId: string;
  userA: number;
  userB: number;
  versionA: number;
  versionB: number;
  digestA: string;
  digestB: string;
  scoreAB: number | null;
  scoreBA: number | null;
  coverageAB: number | null;
  coverageBA: number | null;
  aggregate: number | null;
  recommendationEligible: boolean;
  reasons: string[];
  formulaVersion: string;
  policyVersion: string;
  hardFilter: string[];
  items: ScoreItem[];
  executionMode: string;
};

export type RecallRow = {
  userId: number;
  label: string;
  recallRank: number;
  finalRank: number;
  scoreAB: number | null;
  scoreBA: number | null;
  coverageAB: number | null;
  coverageBA: number | null;
  recommendationEligible: boolean;
  miss: string;
  profileVersion: number;
  generation: number;
};

export type PoolPerson = {
  userId: number;
  label: string;
  legality: string;
  routes: string;
  miss: string;
};

export type Recall = {
  runId: string;
  executionMode: string;
  dataset: string;
  people: number;
  seekerId: number;
  legalPool: number;
  hardBlocked: number;
  pool: PoolPerson[];
  schemes: { scheme: string; recommended: number; insufficient: number; hardRejected: number; safetyViolations: number; rows: RecallRow[] }[];
  population: string;
  routeContract: string;
};

export type Reasoning = {
  runId: string;
  executionMode: string;
  modelName: string;
  realModelStatus: string;
  promptVersion: string;
  kind: string;
  note: string;
  m02Score: number | null;
  aiAdjustment: boolean;
  agentA: AgentSide;
  agentB: AgentSide;
  statusA: string;
  statusB: string;
};

export type AgentSide = {
  supported: string[];
  complements: string[];
  conflicts: string[];
  unknown: string[];
  rejected: string[];
  unsupported: string[];
  evidence: string[];
};

export const labApi = {
  overview: () => http<Overview>('GET', '/api/match-lab/overview'),
  registry: () => http<RegistryFeature[]>('GET', '/api/match-lab/registry'),
  profiles: () => http<Profile[]>('GET', '/api/match-lab/profiles'),
  create: (body: unknown) => http<Profile>('POST', '/api/match-lab/profiles', body as object),
  update: (userId: number, body: unknown) => http<Profile>('PUT', `/api/match-lab/profiles/${userId}`, body as object),
  diff: (left: number, right: number) => http<{ featureCode: string; section: string; left: string; right: string }[]>('GET', `/api/match-lab/diff?left=${left}&right=${right}`),
  score: (userA: number, userB: number) => http<Score>('POST', '/api/match-lab/score', { userA, userB }),
  recall: (split: string) => http<Recall>('POST', '/api/match-lab/recall', { split }),
  recallSandbox: (seekerId: number) => http<Recall>('POST', '/api/match-lab/recall', { mode: 'SYNTHETIC_SANDBOX', seekerId }),
  resetWorkspace: () => http<{ workspaceId: string }>('POST', '/api/match-lab/workspace/reset'),
  reasoning: () => http<Reasoning>('POST', '/api/match-lab/reasoning'),
  global: (objective: string, userIds: number[]) => http<GlobalView>('POST', '/api/match-lab/global', { objective, userIds }),
  compare: () => http<GlobalView>('POST', '/api/match-lab/global/compare'),
  openFeedback: (runId: string, userA: number, userB: number) => http<FeedbackView>('POST', '/api/match-lab/feedback/open', { runId, userA, userB }),
  act: (ledgerKey: string, action: string, actorUserId: number, supersedesEventId?: string) => http<FeedbackView>('POST', '/api/match-lab/feedback/act', { ledgerKey, action, actorUserId, supersedesEventId }),
  learning: (ledgerKey: string, userId: number) => http<LearningView>('GET', `/api/match-lab/learning?ledgerKey=${encodeURIComponent(ledgerKey)}&userId=${userId}`),
  propose: (userId: number, featureCode: string, newValue: string, sourceEventId: string, ledgerKey?: string, origin?: string) => http<LearningView>('POST', '/api/match-lab/learning/propose', { userId, featureCode, newValue, sourceEventId, ledgerKey, origin }),
  runs: () => http<LabRun[]>('GET', '/api/match-lab/runs'),
  decide: (proposalId: string, action: string, actorUserId: number) => http<LearningView>('POST', '/api/match-lab/learning/decide', { proposalId, action, actorUserId }),
  retract: (userId: number, sourceRef: string) => http<LearningView>('POST', '/api/match-lab/learning/retract', { userId, sourceRef }),
  rematch: (parentRunId: string, objective: string) => http<RematchView>('POST', '/api/match-lab/rematch', { parentRunId, objective }),
  runCase: (code: string) => http<CaseView>('POST', `/api/match-lab/cases/${code}`),
};

export type GlobalView = {
  runId: string;
  parentRunId: string | null;
  executionMode: string;
  objective: string;
  status: string;
  statusDetail: string;
  usablePlan: boolean;
  initialUsers: number;
  eligibleEdges: number;
  consideredPairs: number;
  solverEdges: number;
  pairsCount: number;
  unmatchedCount: number;
  averageFit: number;
  lowestFit: number;
  totalWeight: number;
  coverage: string;
  solveMs: number;
  safetyViolations: number;
  pairs: { lowId: number; highId: number; key: string; fit: number | null; scoreLowToHigh: number | null; scoreHighToLow: number | null; coverageLowToHigh: number | null; coverageHighToLow: number | null }[];
  unmatched: { userId: number; reason: string; diagnostics: string[] }[];
  edges: { lowId: number; highId: number; fit: number | null; solver: boolean; exclusion: string; versionLow: number; versionHigh: number; generationLow: number; generationHigh: number; diagnostics: string[] }[];
  limitations: string;
  inputHash: string;
  resultHash: string;
  semanticResultHash: string;
  comparison: GlobalView | null;
};

export type LabRun = {
  runId: string;
  parentRunId: string | null;
  experimentType: string;
  inputSnapshot: string;
  inputHash: string;
  resultHash: string;
  executionMode: string;
  status: string;
};

export type FeedbackView = {
  ledgerKey: string;
  matchingRunId: string;
  userA: number;
  userB: number;
  versionA: number;
  versionB: number;
  events: { eventId: string; type: string; actorUserId: number; occurredAt: string; recordedAt: string; source: string; effective: boolean; causationEventId: string | null; supersedesEventId: string | null; superseded: boolean }[];
  projection: { responseA: string; responseB: string; mutualAcceptance: boolean; schedule: string; attendance: string; contested: boolean; continueA: string; continueB: string; unknown: string[]; learningAllowed: boolean; learningReasons: string[] };
  labRunId: string;
  globalObjectiveVersion: string;
};

export type LearningView = {
  ledgerKey: string;
  userId: number;
  profileVersion: number;
  signals: { sourceEventId: string; category: string; target: string; evidenceState: string; authorization: string }[];
  unlearnable: string[];
  proposal: { proposalId: string; userId: number; featureCode: string; oldValue: string | null; proposedValue: string; status: string; profileVersion: number; evidenceStatus: string } | null;
  before: { featureCode: string; value: string; status: string }[];
  after: { featureCode: string; value: string; status: string }[];
  generation: number;
  limitations: string;
};

export type RematchView = { parentRunId: string; child: GlobalView; pairsBefore: string[]; pairsAfter: string[]; limitations: string };
export type CaseView = { code: string; executionMode: string; observed: boolean; leftRunId: string; rightRunId: string; note: string };
