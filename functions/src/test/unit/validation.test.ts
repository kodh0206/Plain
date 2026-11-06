import assert from "node:assert/strict";
import {describe, it} from "node:test";
import {HttpsError} from "firebase-functions/v2/https";
import {
  validateCreateSchedule,
  validateScheduleRange,
  validateUpdateSchedule,
} from "../../calendar/validation";

describe("schedule validation", () => {
  it("일정 생성 값과 위치를 정규화한다", () => {
    const result = validateCreateSchedule({
      requestId: "request_12345678",
      title: "  정비 예약  ",
      description: "  정기 점검  ",
      startAtMillis: 1_800_000_000_000,
      endAtMillis: 1_800_003_600_000,
      timeZoneId: "Asia/Seoul",
      isAllDay: false,
      location: {address: "  서울 서비스센터 ", latitude: 37.5, longitude: 127.0},
      tags: ["1", "1"],
    });
    assert.equal(result.title, "정비 예약");
    assert.equal(result.location?.address, "서울 서비스센터");
    assert.deepEqual(result.tags, ["1"]);
  });

  it("동일하거나 역전된 종료 시각을 거부한다", () => {
    assert.throws(
      () => validateCreateSchedule({
        requestId: "request_12345678",
        title: "잘못된 일정",
        startAtMillis: 200,
        endAtMillis: 100,
        timeZoneId: "Asia/Seoul",
        isAllDay: false,
      }),
      (error: unknown) => error instanceof HttpsError && error.code === "invalid-argument",
    );
  });

  it("위도와 경도가 함께 없는 위치를 거부한다", () => {
    assert.throws(
      () => validateCreateSchedule({
        requestId: "request_12345678",
        title: "좌표 오류",
        startAtMillis: 100,
        endAtMillis: 200,
        timeZoneId: "Asia/Seoul",
        isAllDay: false,
        location: {address: "서울", latitude: 37.5},
      }),
      HttpsError,
    );
  });

  it("366일을 넘는 조회 범위를 거부한다", () => {
    assert.throws(
      () => validateScheduleRange({
        startAtMillis: 0,
        endAtMillis: 367 * 24 * 60 * 60 * 1_000,
      }),
      HttpsError,
    );
  });

  it("수정할 필드가 없는 요청을 거부한다", () => {
    assert.throws(
      () => validateUpdateSchedule({
        scheduleId: "schedule_12345678",
        expectedRevision: 1,
      }),
      HttpsError,
    );
  });
});
