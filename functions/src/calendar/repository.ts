import {FieldValue, Firestore, Timestamp} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {
  CalendarEventRange,
  CalendarEventValues,
  UpdateCalendarEventValues,
} from "./validation";

interface StoredCalendarEvent {
  title: string;
  startAt: Timestamp;
  endAt: Timestamp;
  allDay: boolean;
  location: string | null;
  notes: string | null;
  tagId: string | null;
}

/** Firestore Timestamp를 Android가 다루기 쉬운 epoch milliseconds로 직렬화합니다. */
function eventResponse(id: string, event: StoredCalendarEvent) {
  return {
    id,
    title: event.title,
    startAtMillis: event.startAt.toMillis(),
    endAtMillis: event.endAt.toMillis(),
    allDay: event.allDay,
    location: event.location,
    notes: event.notes,
    tagId: event.tagId,
  };
}

/** 차량 UID 경로 아래에서만 일정 문서를 관리하는 저장소입니다. */
export class CalendarEventRepository {
  constructor(private readonly firestore: Firestore) {}

  private events(uid: string) {
    return this.firestore.collection("vehicles").doc(uid).collection("events");
  }

  /** 새 일정을 생성하고 생성된 ID와 정규화된 값을 반환합니다. */
  async create(uid: string, values: CalendarEventValues) {
    const document = this.events(uid).doc();
    const stored: StoredCalendarEvent = {
      title: values.title,
      startAt: Timestamp.fromMillis(values.startAtMillis),
      endAt: Timestamp.fromMillis(values.endAtMillis),
      allDay: values.allDay,
      location: values.location,
      notes: values.notes,
      tagId: values.tagId,
    };
    await document.set({
      ...stored,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return eventResponse(document.id, stored);
  }

  /** 요청된 반열림 시각 범위 `[start, end)`에 시작하는 일정을 조회합니다. */
  async list(uid: string, range: CalendarEventRange) {
    const snapshot = await this.events(uid)
      .where("startAt", ">=", Timestamp.fromMillis(range.startAtMillis))
      .where("startAt", "<", Timestamp.fromMillis(range.endAtMillis))
      .orderBy("startAt", "asc")
      .limit(500)
      .get();

    return snapshot.docs.map((document) =>
      eventResponse(document.id, document.data() as StoredCalendarEvent),
    );
  }

  /** 기존 일정과 수정 값을 트랜잭션으로 병합해 시간 순서 불변식을 보장합니다. */
  async update(uid: string, input: UpdateCalendarEventValues) {
    const reference = this.events(uid).doc(input.eventId);
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (!snapshot.exists) {
        throw new HttpsError("not-found", "일정을 찾을 수 없습니다.");
      }
      const current = snapshot.data() as StoredCalendarEvent;
      const next: StoredCalendarEvent = {
        ...current,
        ...input.values,
        startAt: input.values.startAtMillis === undefined ?
          current.startAt : Timestamp.fromMillis(input.values.startAtMillis),
        endAt: input.values.endAtMillis === undefined ?
          current.endAt : Timestamp.fromMillis(input.values.endAtMillis),
      };
      if (next.endAt.toMillis() <= next.startAt.toMillis()) {
        throw new HttpsError("invalid-argument", "종료 시각은 시작 시각보다 늦어야 합니다.");
      }

      const updateValues: Record<string, unknown> = {
        ...input.values,
        updatedAt: FieldValue.serverTimestamp(),
      };
      if (input.values.startAtMillis !== undefined) {
        updateValues.startAt = next.startAt;
        delete updateValues.startAtMillis;
      }
      if (input.values.endAtMillis !== undefined) {
        updateValues.endAt = next.endAt;
        delete updateValues.endAtMillis;
      }
      transaction.update(reference, updateValues);
      return eventResponse(input.eventId, next);
    });
  }

  /** 자신의 차량 UID 경로에 있는 일정을 삭제합니다. */
  async delete(uid: string, eventId: string): Promise<void> {
    const reference = this.events(uid).doc(eventId);
    const snapshot = await reference.get();
    if (!snapshot.exists) {
      throw new HttpsError("not-found", "일정을 찾을 수 없습니다.");
    }
    await reference.delete();
  }
}
