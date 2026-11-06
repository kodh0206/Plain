import {HttpsError} from "firebase-functions/v2/https";

const TITLE_MAX_LENGTH = 100;
const DESCRIPTION_MAX_LENGTH = 2_000;
const ADDRESS_MAX_LENGTH = 300;
const MAX_TAG_COUNT = 10;
const MAX_QUERY_RANGE_MILLIS = 366 * 24 * 60 * 60 * 1_000;
const identifierPattern = /^[A-Za-z0-9_-]{8,128}$/;
const tagIdentifierPattern = /^[A-Za-z0-9_-]{1,64}$/;

export interface ScheduleLocationValues {
  address: string;
  latitude: number | null;
  longitude: number | null;
}

export interface ScheduleValues {
  title: string;
  description: string;
  startAtMillis: number;
  endAtMillis: number;
  timeZoneId: string;
  isAllDay: boolean;
  location: ScheduleLocationValues | null;
  tags: string[];
}

export interface CreateScheduleValues extends ScheduleValues {
  requestId: string;
}

export interface UpdateScheduleValues {
  scheduleId: string;
  expectedRevision: number;
  values: Partial<ScheduleValues>;
}

export interface DeleteScheduleValues {
  scheduleId: string;
  expectedRevision: number;
}

export interface ScheduleRange {
  startAtMillis: number;
  endAtMillis: number;
}

/** 알 수 없는 callable 입력을 키-값 객체로 검증합니다. */
function asRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", "요청 본문 형식이 올바르지 않습니다.");
  }
  return value as Record<string, unknown>;
}

/** 필수 문자열을 공백 제거 후 길이 제한까지 검증합니다. */
function requiredString(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${field} 값이 필요합니다.`);
  }
  const normalized = value.trim();
  if (normalized.length === 0 || normalized.length > maxLength) {
    throw new HttpsError("invalid-argument", `${field} 길이는 1자 이상 ${maxLength}자 이하여야 합니다.`);
  }
  return normalized;
}

/** 빈 값이 허용되는 문자열을 길이 제한에 맞춰 정규화합니다. */
function optionalText(value: unknown, field: string, maxLength: number): string {
  if (value === undefined || value === null) return "";
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${field} 형식이 올바르지 않습니다.`);
  }
  const normalized = value.trim();
  if (normalized.length > maxLength) {
    throw new HttpsError("invalid-argument", `${field} 길이는 ${maxLength}자 이하여야 합니다.`);
  }
  return normalized;
}

/** 밀리초 시각을 안전한 정수로 검증합니다. */
function epochMillis(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new HttpsError("invalid-argument", `${field} 형식이 올바르지 않습니다.`);
  }
  return value;
}

/** 재시도와 소유권 경계에 사용하는 식별자를 안전한 문자 집합으로 제한합니다. */
function identifier(value: unknown, field: string): string {
  const normalized = requiredString(value, field, 128);
  if (!identifierPattern.test(normalized)) {
    throw new HttpsError("invalid-argument", `${field} 형식이 올바르지 않습니다.`);
  }
  return normalized;
}

/** Room 자동 증가 키를 포함한 짧은 태그 ID를 안전한 문자 집합으로 검증합니다. */
function tagIdentifier(value: unknown): string {
  const normalized = requiredString(value, "태그 ID", 64);
  if (!tagIdentifierPattern.test(normalized)) {
    throw new HttpsError("invalid-argument", "태그 ID 형식이 올바르지 않습니다.");
  }
  return normalized;
}

/** IANA 시간대 ID가 현재 런타임에서 지원되는지 확인합니다. */
function timeZoneId(value: unknown): string {
  const normalized = requiredString(value, "시간대", 64);
  try {
    new Intl.DateTimeFormat("ko-KR", {timeZone: normalized}).format();
  } catch {
    throw new HttpsError("invalid-argument", "지원하지 않는 시간대입니다.");
  }
  return normalized;
}

/** 태그 ID 배열을 중복 없이 최대 허용 개수로 정규화합니다. */
function tags(value: unknown): string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value) || value.length > MAX_TAG_COUNT) {
    throw new HttpsError("invalid-argument", `태그는 최대 ${MAX_TAG_COUNT}개까지 선택할 수 있습니다.`);
  }
  return [...new Set(value.map(tagIdentifier))];
}

/** 주소와 좌표가 함께 일관된 위치 객체인지 검증합니다. */
function location(value: unknown): ScheduleLocationValues | null {
  if (value === undefined || value === null) return null;
  const input = asRecord(value);
  const address = optionalText(input.address, "주소", ADDRESS_MAX_LENGTH);
  const latitude = input.latitude === undefined || input.latitude === null ? null : input.latitude;
  const longitude = input.longitude === undefined || input.longitude === null ? null : input.longitude;

  if ((latitude === null) !== (longitude === null)) {
    throw new HttpsError("invalid-argument", "위도와 경도는 함께 입력해야 합니다.");
  }
  if (latitude !== null && (typeof latitude !== "number" || !Number.isFinite(latitude) || latitude < -90 || latitude > 90)) {
    throw new HttpsError("invalid-argument", "위도 범위가 올바르지 않습니다.");
  }
  if (longitude !== null && (typeof longitude !== "number" || !Number.isFinite(longitude) || longitude < -180 || longitude > 180)) {
    throw new HttpsError("invalid-argument", "경도 범위가 올바르지 않습니다.");
  }
  if (address.length === 0 && latitude === null) return null;
  return {address, latitude, longitude};
}

/** 양의 정수 리비전을 검증합니다. */
function revision(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 1) {
    throw new HttpsError("invalid-argument", "리비전 값이 올바르지 않습니다.");
  }
  return value;
}

/** 시작·종료 시각의 순서를 검증합니다. */
function validateTimeOrder(startAtMillis: number, endAtMillis: number): void {
  if (endAtMillis <= startAtMillis) {
    throw new HttpsError("invalid-argument", "종료 시각은 시작 시각보다 늦어야 합니다.");
  }
}

/** 일정 생성 요청을 서버 저장 값으로 변환합니다. */
export function validateCreateSchedule(data: unknown): CreateScheduleValues {
  const input = asRecord(data);
  const startAtMillis = epochMillis(input.startAtMillis, "시작 시각");
  const endAtMillis = epochMillis(input.endAtMillis, "종료 시각");
  validateTimeOrder(startAtMillis, endAtMillis);
  if (typeof input.isAllDay !== "boolean") {
    throw new HttpsError("invalid-argument", "종일 여부 형식이 올바르지 않습니다.");
  }
  return {
    requestId: identifier(input.requestId, "요청 ID"),
    title: requiredString(input.title, "제목", TITLE_MAX_LENGTH),
    description: optionalText(input.description, "설명", DESCRIPTION_MAX_LENGTH),
    startAtMillis,
    endAtMillis,
    timeZoneId: timeZoneId(input.timeZoneId),
    isAllDay: input.isAllDay,
    location: location(input.location),
    tags: tags(input.tags),
  };
}

/** 날짜 범위 조회 요청을 최대 366일 범위로 제한합니다. */
export function validateScheduleRange(data: unknown): ScheduleRange {
  const input = asRecord(data);
  const startAtMillis = epochMillis(input.startAtMillis, "조회 시작 시각");
  const endAtMillis = epochMillis(input.endAtMillis, "조회 종료 시각");
  const duration = endAtMillis - startAtMillis;
  if (duration <= 0 || duration > MAX_QUERY_RANGE_MILLIS) {
    throw new HttpsError("invalid-argument", "조회 범위는 0일 초과 366일 이하여야 합니다.");
  }
  return {startAtMillis, endAtMillis};
}

/** 일정 수정 요청에서 허용된 필드와 기대 리비전만 추출합니다. */
export function validateUpdateSchedule(data: unknown): UpdateScheduleValues {
  const input = asRecord(data);
  const values: Partial<ScheduleValues> = {};
  if (input.title !== undefined) values.title = requiredString(input.title, "제목", TITLE_MAX_LENGTH);
  if (input.description !== undefined) values.description = optionalText(input.description, "설명", DESCRIPTION_MAX_LENGTH);
  if (input.startAtMillis !== undefined) values.startAtMillis = epochMillis(input.startAtMillis, "시작 시각");
  if (input.endAtMillis !== undefined) values.endAtMillis = epochMillis(input.endAtMillis, "종료 시각");
  if (input.timeZoneId !== undefined) values.timeZoneId = timeZoneId(input.timeZoneId);
  if (input.isAllDay !== undefined) {
    if (typeof input.isAllDay !== "boolean") {
      throw new HttpsError("invalid-argument", "종일 여부 형식이 올바르지 않습니다.");
    }
    values.isAllDay = input.isAllDay;
  }
  if (input.location !== undefined) values.location = location(input.location);
  if (input.tags !== undefined) values.tags = tags(input.tags);
  if (Object.keys(values).length === 0) {
    throw new HttpsError("invalid-argument", "수정할 일정 값이 없습니다.");
  }
  return {
    scheduleId: identifier(input.scheduleId, "일정 ID"),
    expectedRevision: revision(input.expectedRevision),
    values,
  };
}

/** 단일 일정 조회 요청의 ID를 검증합니다. */
export function validateScheduleId(data: unknown): string {
  return identifier(asRecord(data).scheduleId, "일정 ID");
}

/** 일정 삭제 요청의 ID와 기대 리비전을 검증합니다. */
export function validateDeleteSchedule(data: unknown): DeleteScheduleValues {
  const input = asRecord(data);
  return {
    scheduleId: identifier(input.scheduleId, "일정 ID"),
    expectedRevision: revision(input.expectedRevision),
  };
}

/** 수정 후 병합된 일정의 시간 순서를 저장 직전에 다시 검증합니다. */
export function assertValidScheduleTime(startAtMillis: number, endAtMillis: number): void {
  validateTimeOrder(startAtMillis, endAtMillis);
}
