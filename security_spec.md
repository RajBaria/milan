# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Profiles (`/profiles/{profileId}`)**:
   - `profileId` must match `request.auth.uid` and `incoming().userId == request.auth.uid`.
   - No PII (`email`, `phone`, `address`) is stored in `/profiles`.
   - `visibility` must be `'public'` or `'hidden'`. `allow list` requires `resource.data.visibility == 'public'`.
   - `age` must be an integer between `18` and `100`.
   - `createdAt` and `userId` are immutable on update.
2. **Likes (`/likes/{likeId}`)**:
   - `fromUserId` must match `request.auth.uid` and must not equal `toUserId`.
   - Both `fromUserId` and `toUserId` profiles must exist in `/profiles`.
   - Only `fromUserId` or `toUserId` may read/list the like document; only `fromUserId` may delete it; updates are forbidden.
3. **Blocks (`/blocks/{blockId}`)**:
   - `userId` (the blocker) must match `request.auth.uid` and must not equal `blockedUserId`.
   - The blocker's profile `/profiles/$(incoming().userId)` must exist.
   - Only `userId` may read, list, create, or delete their block records; updates are forbidden.
4. **Messages (`/messages/{messageId}`)**:
   - `senderId` must match `request.auth.uid` and must not equal `receiverId`.
   - Both sender and receiver profiles must exist in `/profiles`.
   - Only `senderId` or `receiverId` may read/list the message; updates and deletes are forbidden.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Profile Read**: `unauthDb.collection("profiles").where("visibility", "==", "public").get()` -> Reject (`request.auth != null`).
2. **Identity Spoofing on Profile Create**: Authenticated as `alice`, creating `/profiles/bob` or setting `userId: "bob"` -> Reject.
3. **Underage Profile Creation**: Creating a profile with `age: 16` -> Reject (`age >= 18 && age <= 100`).
4. **Shadow Field Injection on Profile Update**: Updating `/profiles/alice` with `isVerifiedAdmin: true` -> Reject (`affectedKeys().hasOnly(...)`).
5. **Immutable Field Tampering**: Updating `/profiles/alice` with a modified `createdAt` or `userId` -> Reject.
6. **Unfiltered Profile List Query**: `aliceDb.collection("profiles").get()` without `.where("visibility", "==", "public")` -> Reject.
7. **Orphaned Like Creation**: Creating a like where `fromUserId` or `toUserId` does not have an existing `/profiles/{uid}` document -> Reject (`exists(...)`).
8. **Cross-User Like Read**: User `charlie` reading a like between `alice` and `bob` -> Reject.
9. **Like Mutation Attempt**: Updating an existing `/likes/{likeId}` document -> Reject (`allow update: if false`).
10. **Cross-User Block Inspection**: User `bob` listing `alice`'s `/blocks` collection -> Reject (`resource.data.userId == request.auth.uid`).
11. **Spoofed Message Sender**: User `alice` creating a message with `senderId: "bob"` -> Reject.
12. **Oversized Message Payload**: Creating a message with `text` > 1000 characters or empty string -> Reject.
