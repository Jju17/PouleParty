import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

struct ChallengeSubmissionModelTests {

    private func decode(_ payload: [String: Any]) throws -> ChallengeSubmission {
        try Firestore.Decoder().decode(ChallengeSubmission.self, from: payload)
    }

    @Test func decodesPendingSubmission() throws {
        let sub = try decode([
            "challengeId": "c1",
            "hunterId": "h1",
            "type": "oneShot",
            "submittedAt": Timestamp(seconds: 1_700_000_000, nanoseconds: 0),
            "mediaUrl": "gs://bucket/games/g1/sub1.jpg",
            "status": "pending",
        ])
        #expect(sub.challengeId == "c1")
        #expect(sub.hunterId == "h1")
        #expect(sub.type == .oneShot)
        #expect(sub.status == .pending)
        #expect(sub.mediaUrl == "gs://bucket/games/g1/sub1.jpg")
        #expect(sub.validatedBy == nil)
        #expect(sub.validatedAt == nil)
    }

    @Test func decodesValidatedSubmission() throws {
        let sub = try decode([
            "challengeId": "c1",
            "hunterId": "h1",
            "type": "repeatable",
            "mediaUrl": "url",
            "status": "validated",
            "validatedBy": "gm-uid",
            "validatedAt": Timestamp(seconds: 1_700_000_500, nanoseconds: 0),
        ])
        #expect(sub.status == .validated)
        #expect(sub.type == .repeatable)
        #expect(sub.validatedBy == "gm-uid")
        #expect(sub.validatedAt != nil)
    }

    @Test func unknownStatusFallsBackToPending() throws {
        let sub = try decode([
            "challengeId": "c1",
            "hunterId": "h1",
            "type": "oneShot",
            "mediaUrl": "url",
            "status": "future-state",
        ])
        #expect(sub.status == .pending)
    }

    @Test func roundTripPreservesEveryField() throws {
        let original = ChallengeSubmission(
            firestoreId: "sub-id",
            challengeId: "c-bar-cirio",
            hunterId: "hunter-1",
            type: .repeatable,
            submittedAt: Timestamp(seconds: 1_700_000_000, nanoseconds: 0),
            mediaUrl: "gs://bucket/games/g1/sub-id.jpg",
            status: .pending,
            validatedBy: nil,
            validatedAt: nil
        )
        let encoded = try Firestore.Encoder().encode(original)
        let decoded = try Firestore.Decoder().decode(ChallengeSubmission.self, from: encoded)
        #expect(decoded.challengeId == original.challengeId)
        #expect(decoded.hunterId == original.hunterId)
        #expect(decoded.type == original.type)
        #expect(decoded.submittedAt == original.submittedAt)
        #expect(decoded.mediaUrl == original.mediaUrl)
        #expect(decoded.status == original.status)
        #expect(decoded.validatedBy == original.validatedBy)
        #expect(decoded.validatedAt == original.validatedAt)
    }

    @Test func statusRawValuesStayInLockstepWithAndroid() {
        #expect(ChallengeSubmission.SubmissionStatus.pending.rawValue == "pending")
        #expect(ChallengeSubmission.SubmissionStatus.validated.rawValue == "validated")
        #expect(ChallengeSubmission.SubmissionStatus.rejected.rawValue == "rejected")
        #expect(ChallengeSubmission.SubmissionStatus.allCases.count == 3)
    }
}
