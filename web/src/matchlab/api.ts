import { http } from '../api/http';

export type CatalogEntry = {
  experimentId: string;
  module: string;
  title: string;
  description: string;
  datasetVersion: string;
  datasetHash: string;
  commitSha: string;
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
  reasoning: () => http<Reasoning>('POST', '/api/match-lab/reasoning'),
};
