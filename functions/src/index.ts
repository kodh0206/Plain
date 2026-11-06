import {initializeApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {setGlobalOptions} from "firebase-functions/v2";
import {CallableRequest, HttpsError, onCall} from "firebase-functions/v2/https";
import {CalendarEventRepository} from "./calendar/repository";
import {
  validateCalendarEventRange,
  validateCreateCalendarEvent,
  validateEventId,
  validateUpdateCalendarEvent,
} from "./calendar/validation";

initializeApp();
setGlobalOptions({
  region: "asia-northeast3",
  maxInstances: 1,
  minInstances: 0,
  memory: "256MiB",
  timeoutSeconds: 30,
});

const repository = new CalendarEventRepository(getFirestore());

/** 인증된 callable 요청에서 차량 식별자로 사용할 Firebase UID를 반환합니다. */
function requireVehicleUid(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "차량 인증이 필요합니다.");
  }
  return uid;
}

/** 인증된 차량의 새 일정을 생성합니다. */
export const createCalendarEvent = onCall(async (request) => {
  const uid = requireVehicleUid(request);
  return repository.create(uid, validateCreateCalendarEvent(request.data));
});

/** 인증된 차량의 지정 기간 일정을 시작 시각 순으로 반환합니다. */
export const getCalendarEvents = onCall(async (request) => {
  const uid = requireVehicleUid(request);
  const events = await repository.list(uid, validateCalendarEventRange(request.data));
  return {events};
});

/** 인증된 차량의 기존 일정을 부분 수정합니다. */
export const updateCalendarEvent = onCall(async (request) => {
  const uid = requireVehicleUid(request);
  return repository.update(uid, validateUpdateCalendarEvent(request.data));
});

/** 인증된 차량의 일정을 삭제합니다. */
export const deleteCalendarEvent = onCall(async (request) => {
  const uid = requireVehicleUid(request);
  await repository.delete(uid, validateEventId(request.data));
  return {deleted: true};
});
