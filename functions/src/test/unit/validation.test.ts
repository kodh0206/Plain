import assert from "node:assert/strict";
import {describe, it} from "node:test";
import {HttpsError} from "firebase-functions/v2/https";
import {
  validateCalendarEventRange,
  validateCreateCalendarEvent,
  validateUpdateCalendarEvent,
} from "../../calendar/validation";

describe("calendar validation", () => {
  it("일정 생성 값을 정규화한다", () => {
    const result = validateCreateCalendarEvent({
      title: "  정비 예약  ",
      startAtMillis: 1_800_000_000_000,
      endAtMillis: 1_800_000_003_600,
      location: "  서비스 센터 ",
    });

    assert.equal(result.title, "정비 예약");
    assert.equal(result.location, "서비스 센터");
    assert.equal(result.allDay, false);
    assert.equal(result.notes, null);
  });

  it("종료 시각이 시작 시각보다 빠른 일정을 거부한다", () => {
    assert.throws(
      () => validateCreateCalendarEvent({
        title: "잘못된 일정",
        startAtMillis: 200,
        endAtMillis: 100,
      }),
      (error: unknown) => error instanceof HttpsError && error.code === "invalid-argument",
    );
  });

  it("366일을 넘는 조회 범위를 거부한다", () => {
    assert.throws(
      () => validateCalendarEventRange({
        startAtMillis: 0,
        endAtMillis: 367 * 24 * 60 * 60 * 1_000,
      }),
      HttpsError,
    );
  });

  it("수정할 필드가 없는 요청을 거부한다", () => {
    assert.throws(
      () => validateUpdateCalendarEvent({eventId: "event-1"}),
      HttpsError,
    );
  });
});
