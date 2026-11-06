import {Firestore, Timestamp} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {
  assertValidScheduleTime,
  CreateScheduleValues,
  DeleteScheduleValues,
  ScheduleLocationValues,
  ScheduleRange,
  UpdateScheduleValues,
} from "./validation";

interface StoredSchedule {
  title: string;
  description: string;
  startAt: Timestamp;
  endAt: Timestamp;
  timeZoneId: string;
  isAllDay: boolean;
  location: ScheduleLocationValues | null;
  tags: string[];
  createdAt: Timestamp;
  updatedAt: Timestamp;
  revision: number;
}

/** Firestore 문서를 Android callable 응답 형식으로 직렬화합니다. */
function scheduleResponse(id: string, schedule: StoredSchedule) {
  return {
    id,
    title: schedule.title,
    description: schedule.description,
    startAtMillis: schedule.startAt.toMillis(),
    endAtMillis: schedule.endAt.toMillis(),
    timeZoneId: schedule.timeZoneId,
    isAllDay: schedule.isAllDay,
    location: schedule.location,
    tags: schedule.tags,
    createdAtMillis: schedule.createdAt.toMillis(),
    updatedAtMillis: schedule.updatedAt.toMillis(),
    revision: schedule.revision,
  };
}

/** Firebase UID 소유 경계 아래에서만 일정 문서를 관리하는 저장소입니다. */
export class ScheduleRepository {
  constructor(private readonly firestore: Firestore) {}

  private schedules(uid: string) {
    return this.firestore.collection("users").doc(uid).collection("schedules");
  }

  /** 요청 ID를 문서 ID로 사용해 동일 생성 요청의 재시도를 중복 없이 처리합니다. */
  async create(uid: string, input: CreateScheduleValues) {
    const reference = this.schedules(uid).doc(input.requestId);
    return this.firestore.runTransaction(async (transaction) => {
      const existing = await transaction.get(reference);
      if (existing.exists) {
        return scheduleResponse(reference.id, existing.data() as StoredSchedule);
      }
      const now = Timestamp.now();
      const schedule: StoredSchedule = {
        title: input.title,
        description: input.description,
        startAt: Timestamp.fromMillis(input.startAtMillis),
        endAt: Timestamp.fromMillis(input.endAtMillis),
        timeZoneId: input.timeZoneId,
        isAllDay: input.isAllDay,
        location: input.location,
        tags: input.tags,
        createdAt: now,
        updatedAt: now,
        revision: 1,
      };
      transaction.create(reference, schedule);
      return scheduleResponse(reference.id, schedule);
    });
  }

  /** 요청된 반열림 시각 범위 `[start, end)`에 시작하는 일정을 조회합니다. */
  async list(uid: string, range: ScheduleRange) {
    const snapshot = await this.schedules(uid)
      .where("startAt", ">=", Timestamp.fromMillis(range.startAtMillis))
      .where("startAt", "<", Timestamp.fromMillis(range.endAtMillis))
      .orderBy("startAt", "asc")
      .limit(500)
      .get();
    return snapshot.docs.map((document) =>
      scheduleResponse(document.id, document.data() as StoredSchedule),
    );
  }

  /** 인증된 UID 경로에서 단일 일정을 조회합니다. */
  async get(uid: string, scheduleId: string) {
    const snapshot = await this.schedules(uid).doc(scheduleId).get();
    if (!snapshot.exists) throw new HttpsError("not-found", "일정을 찾을 수 없습니다.");
    return scheduleResponse(snapshot.id, snapshot.data() as StoredSchedule);
  }

  /** 기대 리비전과 일치할 때만 일정을 수정해 오래된 클라이언트의 덮어쓰기를 막습니다. */
  async update(uid: string, input: UpdateScheduleValues) {
    const reference = this.schedules(uid).doc(input.scheduleId);
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (!snapshot.exists) throw new HttpsError("not-found", "일정을 찾을 수 없습니다.");
      const current = snapshot.data() as StoredSchedule;
      if (current.revision !== input.expectedRevision) {
        throw new HttpsError(
          "aborted",
          "다른 곳에서 일정이 변경되었습니다. 새로고침 후 다시 시도해 주세요.",
          {currentRevision: current.revision},
        );
      }

      const nextStartAt = input.values.startAtMillis === undefined ?
        current.startAt : Timestamp.fromMillis(input.values.startAtMillis);
      const nextEndAt = input.values.endAtMillis === undefined ?
        current.endAt : Timestamp.fromMillis(input.values.endAtMillis);
      assertValidScheduleTime(nextStartAt.toMillis(), nextEndAt.toMillis());
      const next: StoredSchedule = {
        ...current,
        ...input.values,
        startAt: nextStartAt,
        endAt: nextEndAt,
        updatedAt: Timestamp.now(),
        revision: current.revision + 1,
      };
      const updateValues: Record<string, unknown> = {...input.values};
      delete updateValues.startAtMillis;
      delete updateValues.endAtMillis;
      if (input.values.startAtMillis !== undefined) updateValues.startAt = nextStartAt;
      if (input.values.endAtMillis !== undefined) updateValues.endAt = nextEndAt;
      updateValues.updatedAt = next.updatedAt;
      updateValues.revision = next.revision;
      transaction.update(reference, updateValues);
      return scheduleResponse(reference.id, next);
    });
  }

  /** 기대 리비전과 일치하는 자신의 일정만 삭제합니다. */
  async delete(uid: string, input: DeleteScheduleValues): Promise<void> {
    const reference = this.schedules(uid).doc(input.scheduleId);
    await this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (!snapshot.exists) throw new HttpsError("not-found", "일정을 찾을 수 없습니다.");
      const current = snapshot.data() as StoredSchedule;
      if (current.revision !== input.expectedRevision) {
        throw new HttpsError(
          "aborted",
          "다른 곳에서 일정이 변경되었습니다. 새로고침 후 다시 시도해 주세요.",
          {currentRevision: current.revision},
        );
      }
      transaction.delete(reference);
    });
  }
}
