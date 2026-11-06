import assert from "node:assert/strict";
import {after, before, describe, it} from "node:test";
import {deleteApp, initializeApp} from "firebase/app";
import {connectAuthEmulator, getAuth, signInAnonymously} from "firebase/auth";
import {connectFunctionsEmulator, getFunctions, httpsCallable} from "firebase/functions";

const app = initializeApp({
  projectId: "plain-local",
  apiKey: "plain-local-api-key",
  appId: "1:1234567890:web:plain-local",
});
const unauthenticatedApp = initializeApp({
  projectId: "plain-local",
  apiKey: "plain-local-api-key",
  appId: "1:1234567890:web:plain-local-unauthenticated",
}, "unauthenticated");
const otherVehicleApp = initializeApp({
  projectId: "plain-local",
  apiKey: "plain-local-api-key",
  appId: "1:1234567890:web:plain-local-other-vehicle",
}, "other-vehicle");
const auth = getAuth(app);
const functions = getFunctions(app, "asia-northeast3");
const unauthenticatedFunctions = getFunctions(unauthenticatedApp, "asia-northeast3");
const otherVehicleAuth = getAuth(otherVehicleApp);
const otherVehicleFunctions = getFunctions(otherVehicleApp, "asia-northeast3");

describe("calendar callable functions", () => {
  before(async () => {
    connectAuthEmulator(auth, "http://127.0.0.1:9099", {disableWarnings: true});
    connectFunctionsEmulator(functions, "127.0.0.1", 5001);
    connectFunctionsEmulator(unauthenticatedFunctions, "127.0.0.1", 5001);
    connectAuthEmulator(otherVehicleAuth, "http://127.0.0.1:9099", {disableWarnings: true});
    connectFunctionsEmulator(otherVehicleFunctions, "127.0.0.1", 5001);
    await signInAnonymously(auth);
    await signInAnonymously(otherVehicleAuth);
  });

  after(async () => {
    await deleteApp(app);
    await deleteApp(unauthenticatedApp);
    await deleteApp(otherVehicleApp);
  });

  it("미인증 요청을 거부한다", async () => {
    const list = httpsCallable(unauthenticatedFunctions, "getCalendarEvents");
    await assert.rejects(
      () => list({startAtMillis: 0, endAtMillis: 1_000}),
      (error: unknown) =>
        typeof error === "object" &&
        error !== null &&
        "code" in error &&
        error.code === "functions/unauthenticated",
    );
  });

  it("인증된 차량이 일정을 생성·조회·수정·삭제한다", async () => {
    const now = Date.now();
    const create = httpsCallable(functions, "createCalendarEvent");
    const list = httpsCallable(functions, "getCalendarEvents");
    const update = httpsCallable(functions, "updateCalendarEvent");
    const remove = httpsCallable(functions, "deleteCalendarEvent");

    const created = (await create({
      title: "PLEOS 점검",
      startAtMillis: now + 60_000,
      endAtMillis: now + 3_660_000,
      allDay: false,
      location: "서울 서비스 센터",
    })).data as {id: string; title: string};
    assert.ok(created.id);
    assert.equal(created.title, "PLEOS 점검");

    const otherVehicleList = httpsCallable(otherVehicleFunctions, "getCalendarEvents");
    const isolated = (await otherVehicleList({
      startAtMillis: now,
      endAtMillis: now + 86_400_000,
    })).data as {events: unknown[]};
    assert.equal(isolated.events.length, 0);

    const listed = (await list({
      startAtMillis: now,
      endAtMillis: now + 86_400_000,
    })).data as {events: Array<{id: string; title: string}>};
    assert.equal(listed.events.length, 1);
    assert.equal(listed.events[0].id, created.id);

    const updated = (await update({
      eventId: created.id,
      title: "PLEOS 정기 점검",
    })).data as {title: string};
    assert.equal(updated.title, "PLEOS 정기 점검");

    const deleted = (await remove({eventId: created.id})).data as {deleted: boolean};
    assert.equal(deleted.deleted, true);

    const empty = (await list({
      startAtMillis: now,
      endAtMillis: now + 86_400_000,
    })).data as {events: unknown[]};
    assert.equal(empty.events.length, 0);
  });
});
