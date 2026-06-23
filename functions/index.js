const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

exports.joinAsAdmin = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const circleCode = String(request.data.circleCode || "");
  const adminCode = String(request.data.adminCode || "");

  const snap = await admin.firestore().collection("default").doc(circleCode).get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Group does not exist.");
  }
  if (snap.get("adminCode") !== adminCode) {
    throw new HttpsError("permission-denied", "Incorrect admin code.");
  }

  await admin.auth().setCustomUserClaims(request.auth.uid, { admin: circleCode });
  return { ok: true };
});

exports.createCircle = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const circleCode = String(request.data.circleCode || "");
  const adminCode = String(request.data.adminCode || "");

  const ref = admin.firestore().collection("default").doc(circleCode);
  if ((await ref.get()).exists) {
    throw new HttpsError("already-exists", "Group already exists.");
  }
  await ref.set({ adminCode });
  await admin.auth().setCustomUserClaims(request.auth.uid, { admin: circleCode });
  return { ok: true };
});
