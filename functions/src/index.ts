import {initializeApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {setGlobalOptions} from "firebase-functions/v2";
import {CallableRequest, HttpsError, onCall} from "firebase-functions/v2/https";
import {ScheduleRepository} from "./calendar/repository";
import {
  validateCreateSchedule,
  validateDeleteSchedule,
  validateScheduleId,
  validateScheduleRange,
  validateUpdateSchedule,
} from "./calendar/validation";

initializeApp();
setGlobalOptions({
  region: "asia-northeast3",
  maxInstances: 1,
  minInstances: 0,
  memory: "256MiB",
  timeoutSeconds: 30,
});

const repository = new ScheduleRepository(getFirestore());

/** 인증된 callable 요청에서 차량 식별자로 사용할 Firebase UID를 반환합니다. */
function requireVehicleUid(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "차량 인증이 필요합니다.");
  return uid;
}

/** 인증된 차량의 새 일정을 중복 없이 생성합니다. */
export const createSchedule = onCall(async (request) =>
  repository.create(requireVehicleUid(request), validateCreateSchedule(request.data)),
);

/** 인증된 차량의 지정 기간 일정을 시작 시각 순으로 반환합니다. */
export const getSchedules = onCall(async (request) => ({
  schedules: await repository.list(
    requireVehicleUid(request),
    validateScheduleRange(request.data),
  ),
}));

/** 인증된 차량의 단일 일정을 반환합니다. */
export const getSchedule = onCall(async (request) =>
  repository.get(requireVehicleUid(request), validateScheduleId(request.data)),
);

/** 기대 리비전이 일치할 때 인증된 차량의 일정을 수정합니다. */
export const updateSchedule = onCall(async (request) =>
  repository.update(requireVehicleUid(request), validateUpdateSchedule(request.data)),
);

/** 기대 리비전이 일치할 때 인증된 차량의 일정을 삭제합니다. */
export const deleteSchedule = onCall(async (request) => {
  await repository.delete(requireVehicleUid(request), validateDeleteSchedule(request.data));
  return {deleted: true};
});
