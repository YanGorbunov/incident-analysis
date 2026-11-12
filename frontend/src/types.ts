// Типы API — зеркало DTO бэкенда.

export type Role = 'ADMIN' | 'ANALYST' | 'VIEWER';
export type Phase = 'NEW' | 'ACTIVE' | 'RESOLVED' | 'CLOSED';

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface UserView {
  id: number;
  username: string;
  role: Role;
  enabled: boolean;
  totpEnabled: boolean;
  lockedUntil?: string;
  employeeId?: number;
  employeeName?: string;
  postName?: string;
  createdAt: string;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
  location?: string;
}
export interface Post {
  id: number;
  code: string;
  departmentId: number;
  departmentName: string;
  name: string;
  description?: string;
}
export interface Priority {
  id: number;
  code: string;
  name: string;
  description?: string;
  sortOrder: number;
  color: string;
  responseSlaMinutes?: number;
  resolutionSlaMinutes?: number;
}
export interface Status {
  id: number;
  code: string;
  name: string;
  description?: string;
  sortOrder: number;
  phase: Phase;
}
export interface Institution {
  id: number;
  code: string;
  name: string;
  phoneNumber?: string;
  address?: string;
}
export interface CiType {
  id: number;
  code: string;
  name: string;
}
export interface Software {
  id: number;
  code: string;
  name: string;
  version?: string;
  vendor?: string;
  licenseKey?: string;
}
export interface Employee {
  id: number;
  code: string;
  externalId?: string;
  lastName: string;
  firstName: string;
  middleName?: string;
  email?: string;
  postId: number;
  postName: string;
  departmentId: number;
  departmentName: string;
  active: boolean;
  shortName: string;
}

export interface ReferenceBundle {
  departments: Department[];
  posts: Post[];
  priorities: Priority[];
  statuses: Status[];
  institutions: Institution[];
  ciTypes: CiType[];
  software: Software[];
  employees: Employee[];
}

export interface IncidentCriteria {
  q?: string;
  registeredFrom?: string;
  registeredTo?: string;
  priorityIds?: number[];
  statusIds?: number[];
  assigneeIds?: number[];
  unassigned?: boolean;
  ciIds?: number[];
  institutionIds?: number[];
  slaBreached?: boolean;
}

export interface IncidentListItem {
  id: number;
  code: string;
  externalId?: string;
  title: string;
  registeredAt: string;
  closedAt?: string;
  priorityId: number;
  priorityName: string;
  priorityColor: string;
  statusId: number;
  statusName: string;
  statusPhase: Phase;
  assigneeId?: number;
  assigneeName?: string;
  cis?: string;
  institutions?: string;
  slaBreached: boolean;
  resolutionSeconds?: number;
}

export interface CiRef {
  id: number;
  code: string;
  name: string;
  institutionName: string;
}

export interface HistoryEntry {
  changedAt: string;
  changedBy?: string;
  fromStatus?: string;
  toStatus: string;
}

export interface IncidentDetails {
  id: number;
  code: string;
  externalId?: string;
  source: 'MANUAL' | 'IMPORT';
  title: string;
  description: string;
  resolution?: string;
  registeredAt: string;
  firstResponseAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  priorityId: number;
  statusId: number;
  assigneeId?: number;
  createdAt: string;
  createdBy?: string;
  updatedAt: string;
  updatedBy?: string;
  version: number;
  cis: CiRef[];
  history: HistoryEntry[];
}

export interface IncidentRequest {
  title: string;
  description: string;
  resolution?: string | null;
  registeredAt: string;
  firstResponseAt?: string | null;
  resolvedAt?: string | null;
  closedAt?: string | null;
  priorityId: number;
  statusId: number;
  assigneeId?: number | null;
  ciIds: number[];
  externalId?: string | null;
  version?: number;
}

export interface CiListItem {
  id: number;
  code: string;
  name: string;
  ciTypeId: number;
  ciTypeName: string;
  institutionId: number;
  institutionName: string;
  ipAddress?: string;
  incidentCount: number;
}

export interface CiDetails {
  id: number;
  code: string;
  externalId?: string;
  name: string;
  ciTypeId: number;
  ciTypeName: string;
  institutionId: number;
  institutionName: string;
  ipAddress?: string;
  technicalSpecs?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  software: Software[];
}

export interface Category {
  id: number;
  code: string;
  name: string;
  description?: string;
  criteria: IncidentCriteria;
  createdAt: string;
  createdBy?: string;
  snapshotAt?: string;
  incidentCount: number;
}

export interface Summary {
  total: number;
  closed: number;
  resolved: number;
  closedPct?: number;
  avgResolutionSeconds?: number;
  medianResolutionSeconds?: number;
  p90ResolutionSeconds?: number;
  avgResponseSeconds?: number;
  resolutionSlaTotal: number;
  resolutionSlaBreached: number;
  responseSlaTotal: number;
  responseSlaBreached: number;
}

export interface PriorityStat {
  id: number;
  name: string;
  color: string;
  total: number;
  closed: number;
  avgResolutionSeconds?: number;
  slaTotal: number;
  slaBreached: number;
}

export interface StatusStat {
  id: number;
  name: string;
  phase: Phase;
  total: number;
}

export interface GroupStat {
  id?: number;
  name: string;
  total: number;
  closed: number;
  slaBreached: number;
  avgResolutionSeconds?: number;
}

export interface TrendPoint {
  weekStart: string;
  registered: number;
  closed: number;
}

export interface Report {
  category: Category;
  generatedAt: string;
  author: { username?: string; name?: string; post?: string };
  summary: Summary;
  byPriority: PriorityStat[];
  byStatus: StatusStat[];
  byInstitution: GroupStat[];
  topCis: GroupStat[];
  byAssignee: GroupStat[];
  trend: TrendPoint[];
  incidents: IncidentListItem[];
}

export type ReportField =
  | 'CODE'
  | 'TITLE'
  | 'REGISTERED_AT'
  | 'CLOSED_AT'
  | 'PRIORITY'
  | 'STATUS'
  | 'ASSIGNEE'
  | 'CIS'
  | 'INSTITUTIONS'
  | 'RESOLUTION_TIME'
  | 'SLA';

export interface ImportResult {
  outcome: 'CREATED' | 'UPDATED' | 'NOT_FOUND' | 'FAILED';
  entityId?: number;
  code?: string;
  warnings: string[];
}

export interface ImportRun {
  id: number;
  kind: 'INCIDENT' | 'CI';
  externalId: string;
  outcome: ImportResult['outcome'];
  message?: string;
  entityId?: number;
  requestedBy?: string;
  startedAt: string;
  durationMs?: number;
}

export interface AuditEvent {
  id: number;
  occurredAt: string;
  username?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  details?: string;
  remoteAddr?: string;
}
