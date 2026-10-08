import { initializeApp } from "firebase-admin/app";

// Application Default Credentials: each deployed function talks to the
// project it was deployed to.
initializeApp();

export {
  setGameMasterPassword,
  clearGameMasterPassword,
  joinAsGameMaster,
} from "./gameMaster";
export { computeZoneConfiguration } from "./zoneCalculation";
export {
  createPendingRegistration,
  confirmRegistrationPayment,
  validateRegistrationCode,
} from "./registrations";
export { processAccountDeletion } from "./accountDeletion";
export { submitFoundCode, getFoundCode } from "./gameplay";
export { activatePowerUp, collectPowerUp } from "./powerUps";
export { validateChallengeSubmission, applyOutOfZonePenalty } from "./validation";
export { renderChallengesSheet } from "./challengesSheet";
export { launchGame } from "./launchGame";
export { debugAdvanceGame } from "./debugAdvanceGame";
export { mirrorGameMetaToRtdb } from "./rtdbMirror";
export { joinGame, designateChicken, leaveGame } from "./roles";
export { sendGameNotification } from "./notifications";
export { transitionGameStatus } from "./lifecycleTasks";
export { spawnPowerUpBatch } from "./powerUpSpawnTask";
export { onGameCreated, onGameDeleted, onGameUpdated } from "./gameTriggers";
export { evaluateOutOfZone } from "./outOfZone";
