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

describe("schedule callable functions", () => {
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
    const list = httpsCallable(unauthenticatedFunctions, "getSchedules");
    await assert.rejects(
      () => list({startAtMillis: 0, endAtMillis: 1_000}),
      (error: unknown) => typeof error === "object" && error !== null &&
        "code" in error && error.code === "functions/unauthenticated",
    );
  });

  it("생성 재시도 중복을 막고 차량 격리·조회·수정·삭제·충돌을 처리한다", async () => {
    const now = Date.now();
    const requestId = `request_${now}`;
    const create = httpsCallable(functions, "createSchedule");
    const list = httpsCallable(functions, "getSchedules");
    const get = httpsCallable(functions, "getSchedule");
    const update = httpsCallable(functions, "updateSchedule");
    const remove = httpsCallable(functions, "deleteSchedule");
    const createInput = {
      requestId,
      title: "PLEOS 점검",
      description: "정기 점검 일정",
      startAtMillis: now + 60_000,
      endAtMillis: now + 3_660_000,
      timeZoneId: "Asia/Seoul",
      isAllDay: false,
      location: {address: "서울 서비스센터", latitude: 37.5, longitude: 127.0},
      tags: ["tag_work_01"],
    };

    const created = (await create(createInput)).data as {id: string; revision: number};
    const retried = (await create(createInput)).data as {id: string; revision: number};
    assert.equal(retried.id, created.id);
    assert.equal(retried.revision, 1);

    const isolated = (await httpsCallable(otherVehicleFunctions, "getSchedules")({
      startAtMillis: now,
      endAtMillis: now + 86_400_000,
    })).data as {schedules: unknown[]};
    assert.equal(isolated.schedules.length, 0);

    const fetched = (await get({scheduleId: created.id})).data as {title: string};
    assert.equal(fetched.title, "PLEOS 점검");

    const listed = (await list({
      startAtMillis: now,
      endAtMillis: now + 86_400_000,
    })).data as {schedules: Array<{id: string}>};
    assert.equal(listed.schedules.length, 1);

    const updated = (await update({
      scheduleId: created.id,
      expectedRevision: 1,
      title: "PLEOS 정기 점검",
    })).data as {title: string; revision: number};
    assert.equal(updated.title, "PLEOS 정기 점검");
    assert.equal(updated.revision, 2);

    await assert.rejects(
      () => update({scheduleId: created.id, expectedRevision: 1, title: "오래된 수정"}),
      (error: unknown) => typeof error === "object" && error !== null &&
        "code" in error && error.code === "functions/aborted",
    );

    const deleted = (await remove({
      scheduleId: created.id,
      expectedRevision: 2,
    })).data as {deleted: boolean};
    assert.equal(deleted.deleted, true);
  });
});
