import {HttpsError} from "firebase-functions/v2/https";

const TITLE_MAX_LENGTH = 100;
const LOCATION_MAX_LENGTH = 200;
const NOTES_MAX_LENGTH = 2_000;
const MAX_QUERY_RANGE_MILLIS = 366 * 24 * 60 * 60 * 1_000;

export interface CalendarEventValues {
  title: string;
  startAtMillis: number;
  endAtMillis: number;
  allDay: boolean;
  location: string | null;
  notes: string | null;
  tagId: string | null;
}

export interface UpdateCalendarEventValues {
  eventId: string;
  values: Partial<CalendarEventValues>;
}

export interface CalendarEventRange {
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
    throw new HttpsError(
      "invalid-argument",
      `${field} 길이는 1자 이상 ${maxLength}자 이하여야 합니다.`,
    );
  }
  return normalized;
}

/** 선택 문자열을 null 또는 길이가 제한된 문자열로 정규화합니다. */
function optionalString(value: unknown, field: string, maxLength: number): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${field} 형식이 올바르지 않습니다.`);
  }
  const normalized = value.trim();
  if (normalized.length > maxLength) {
    throw new HttpsError(
      "invalid-argument",
      `${field} 길이는 ${maxLength}자 이하여야 합니다.`,
    );
  }
  return normalized.length === 0 ? null : normalized;
}

/** 밀리초 시각을 안전한 정수로 검증합니다. */
function epochMillis(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new HttpsError("invalid-argument", `${field} 형식이 올바르지 않습니다.`);
  }
  return value;
}

/** 일정 생성 요청을 Firestore에 저장 가능한 값으로 변환합니다. */
export function validateCreateCalendarEvent(data: unknown): CalendarEventValues {
  const input = asRecord(data);
  const startAtMillis = epochMillis(input.startAtMillis, "시작 시각");
  const endAtMillis = epochMillis(input.endAtMillis, "종료 시각");
  if (endAtMillis <= startAtMillis) {
    throw new HttpsError("invalid-argument", "종료 시각은 시작 시각보다 늦어야 합니다.");
  }

  return {
    title: requiredString(input.title, "제목", TITLE_MAX_LENGTH),
    startAtMillis,
    endAtMillis,
    allDay: input.allDay === undefined ? false : Boolean(input.allDay),
    location: optionalString(input.location, "장소", LOCATION_MAX_LENGTH),
    notes: optionalString(input.notes, "메모", NOTES_MAX_LENGTH),
    tagId: optionalString(input.tagId, "태그 ID", 128),
  };
}

/** 날짜 범위 조회 요청을 최대 366일 범위로 제한합니다. */
export function validateCalendarEventRange(data: unknown): CalendarEventRange {
  const input = asRecord(data);
  const startAtMillis = epochMillis(input.startAtMillis, "조회 시작 시각");
  const endAtMillis = epochMillis(input.endAtMillis, "조회 종료 시각");
  const duration = endAtMillis - startAtMillis;
  if (duration <= 0 || duration > MAX_QUERY_RANGE_MILLIS) {
    throw new HttpsError("invalid-argument", "조회 범위는 0일 초과 366일 이하여야 합니다.");
  }
  return {startAtMillis, endAtMillis};
}

/** 일정 수정 요청에서 허용된 필드만 추출합니다. */
export function validateUpdateCalendarEvent(data: unknown): UpdateCalendarEventValues {
  const input = asRecord(data);
  const eventId = requiredString(input.eventId, "일정 ID", 128);
  const values: Partial<CalendarEventValues> = {};

  if (input.title !== undefined) values.title = requiredString(input.title, "제목", TITLE_MAX_LENGTH);
  if (input.startAtMillis !== undefined) values.startAtMillis = epochMillis(input.startAtMillis, "시작 시각");
  if (input.endAtMillis !== undefined) values.endAtMillis = epochMillis(input.endAtMillis, "종료 시각");
  if (input.allDay !== undefined) {
    if (typeof input.allDay !== "boolean") {
      throw new HttpsError("invalid-argument", "종일 여부 형식이 올바르지 않습니다.");
    }
    values.allDay = input.allDay;
  }
  if (input.location !== undefined) values.location = optionalString(input.location, "장소", LOCATION_MAX_LENGTH);
  if (input.notes !== undefined) values.notes = optionalString(input.notes, "메모", NOTES_MAX_LENGTH);
  if (input.tagId !== undefined) values.tagId = optionalString(input.tagId, "태그 ID", 128);

  if (Object.keys(values).length === 0) {
    throw new HttpsError("invalid-argument", "수정할 일정 값이 없습니다.");
  }
  return {eventId, values};
}

/** 일정 ID만 필요한 삭제 요청을 검증합니다. */
export function validateEventId(data: unknown): string {
  return requiredString(asRecord(data).eventId, "일정 ID", 128);
}
