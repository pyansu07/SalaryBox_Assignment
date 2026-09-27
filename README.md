# Attendance

A face-recognition-based staff attendance app for Android, built as a hiring assignment.
Admins enrol staff faces; staff check in with a live selfie that is matched against their
enrolled face before an attendance record (with GPS location) is saved.

## Tech stack

- **Language**: Java
- **Min SDK**: 24 (Android 7.0)
- **Architecture**: MVVM-ish layering
  - `ui` — Activities (`ui.admin`, `ui.staff`) + RecyclerView adapters + `ViewModel`s
  - `data` — Room entities (`Staff`, `Attendance`), DAOs, `AppDatabase`, `EmbeddingCodec`
  - `repository` — thin wrappers that move Room access off the main thread and expose `LiveData`
  - `ml` — `FaceDetectorHelper` (final-pass ML Kit detection + landmarks/classification),
    `FaceAligner` (eye-level rotation/crop), `FaceEmbedder` (TFLite MobileFaceNet embedding +
    cosine similarity), `GuidedCaptureAnalyzer` (live frame quality/liveness signals),
    `BlinkLivenessDetector`, `GuidedCaptureController` (ties the two together into one guided round)
  - `util` — `CameraXHelper`, `LocationHelper`, `GeocoderHelper`, `CryptoUtils`, `SessionManager`,
    `PermissionUtils`, `ImageUtils`
- **Camera**: CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), using
  both `ImageCapture` (the still photo) and `ImageAnalysis` (live guidance/liveness frames)
- **Face detection**: ML Kit Face Detection (on-device; fast+classification-only for live frames,
  accurate+landmarks+classification for the final captured photo)
- **Face recognition**: TensorFlow Lite running a MobileFaceNet model bundled at
  `app/src/main/assets/mobile_face_net.tflite` (112x112 RGB input, 192-d output embedding)
- **Persistence**: Room (SQLite), with the face-embedding field encrypted at rest (see
  [Enhancements](#enhancements-beyond-the-base-assignment))
- **Location**: `FusedLocationProviderClient` (Play Services Location) + `Geocoder` for reverse
  geocoding in the history view

### How face matching works

1. Once the camera opens, live preview frames are continuously graded by `GuidedCaptureAnalyzer`
   for blur, brightness, and detected-face size, and guidance text updates in real time ("Hold
   steady", "Move closer", "Too dark", ...). Capture won't fire until the frame passes this gate.
2. Once the gate is passing, `BlinkLivenessDetector` asks for an open→closed→open blink within an
   8s window ("Blink to continue") as a basic liveness check, then the still photo is captured
   automatically - no manual shutter tap.
3. ML Kit's `FaceDetector` re-runs on the captured photo (this time with landmarks) and
   `FaceAligner` uses the eye landmark positions to rotate the photo level and crop it centered
   on the face, before it's resized to 112x112, normalized to `(pixel - 127.5) / 128`, and run
   through the bundled MobileFaceNet TFLite model to produce a 192-d embedding (L2-normalized).
4. **Enrolment** (admin flow): the admin repeats steps 1-3 for 3 shots (straight, slight left,
   slight right turn); all 3 embeddings are encrypted and stored against the `Staff` row.
5. **Verification** (staff flow): the live embedding is compared against *every* one of the
   staff member's enrolled sample embeddings via **cosine similarity**, and the best (highest)
   score is used. A score `>= 0.6` is treated as a match, and the score is shown to the user as a
   percentage on both outcomes ("Matched - 82% confidence" / "Not matched - 41% confidence, try
   again"). Non-matches and "no face detected" show a "try again" message instead of crashing.
6. On a match, the selfie is encrypted at rest, the current location is fetched via
   `FusedLocationProviderClient`, and an `Attendance` row (`staffId`, `timestamp`,
   `selfiePath` pointing at the encrypted file, `latitude`, `longitude`) is inserted. The history
   screen reverse-geocodes the coordinates for display only; the DB always keeps raw lat/lng.

## How to run

1. Open the project root in Android Studio (Giraffe or newer recommended) and let Gradle sync —
   all dependencies (CameraX, ML Kit, TensorFlow Lite, Room, Play Services Location) are already
   declared in `app/build.gradle`.
2. Run on a **physical device** if possible — the emulator's virtual camera rarely produces a
   detectable face or plausible blink, which makes the enrolment/matching flow hard to demo. If
   you must use an emulator, use one with a webcam-backed front camera and a well-lit test face.
3. Grant Camera and Location permissions when prompted.
4. Log in with one of the demo credentials below.
5. **As Admin**: tap **+** to add a staff member (or use the pre-seeded demo staff), open their
   profile, and tap **Enrol Face**. The camera opens straight into guided capture:
   - Live guidance text tells you what to fix ("Move closer", "Too dark", "Hold steady").
   - Once the frame is good, it asks you to **blink** ("Blink to continue" → close → reopen);
     the photo is captured automatically the moment that's confirmed.
   - This repeats for **3 shots** ("Look straight ahead" → "Turn slightly left" → "Turn slightly
     right"), each shown in a banner at the top. If a shot fails (no face found, etc.) a **Retry**
     button appears for just that shot - you don't restart the whole enrolment.
6. **As Staff**: from the dashboard tap **Mark Attendance** - the same guided quality-gate +
   blink flow runs once, then shows **Matched - NN% confidence** (and records attendance with the
   current GPS location) or **Not matched - NN% confidence, try again**. **View Attendance
   History** shows past check-ins with a reverse-geocoded locality instead of raw coordinates.

### Demo credentials

| Role  | Username | Password  |
|-------|----------|-----------|
| Admin | `admin`  | `admin123` |
| Staff | `staff`  | `staff123` |

The Staff login is tied to a single pre-seeded `Staff` record (employee ID `EMP001`,
name "Demo Staff") so the staff flow always has someone to enrol/verify against — this record
also shows up in the Admin staff list and can be enrolled from there.

## Enhancements beyond the base assignment

The base assignment (login, staff CRUD, single-shot enrolment, cosine-similarity matching, Room,
location capture) has been extended with 7 additions aimed at making the face pipeline closer to
what a real attendance system would need:

1. **Live quality gate.** `GuidedCaptureAnalyzer` runs on every preview frame (via a CameraX
   `ImageAnalysis` use case alongside the existing `ImageCapture`) and rejects frames that are too
   blurry (a coarse discrete-Laplacian-variance proxy, not a textbook per-pixel Laplacian - fast
   enough to run continuously), too dark/bright (mean luminance out of `[50, 220]`), or where the
   face is too small relative to the frame (`< 20%` of frame area). Guidance text updates live
   instead of only failing after the fact. *Why it matters*: garbage in, garbage out - a blurry or
   badly lit capture produces an unreliable embedding no matter how good the matching logic is.
   **Limitation**: the blur/brightness thresholds are reasonable starting points, not tuned against
   real hardware - expect to adjust `GuidedCaptureAnalyzer`'s constants after device testing.
2. **Eye-landmark face alignment.** `FaceAligner` uses ML Kit's left/right eye landmark
   coordinates to compute the inter-eye rotation angle, rotates the photo so the eyes are level,
   and crops centered on the eye midpoint - done for both enrolment and verification.
   *Why it matters*: head-tilt variation between the enrolment and verification shot is a common
   source of embedding drift; aligning both to the same reference frame makes comparisons more
   consistent. **Limitation**: falls back to a plain bounding-box crop if eye landmarks aren't
   detected (e.g. a very oblique pose), which loses the alignment benefit for that one shot.
3. **Blink-liveness challenge.** `BlinkLivenessDetector` requires an open→closed→open eye-state
   sequence (via ML Kit's classification mode) within an 8s window before a capture is accepted,
   with live "Blink to continue" prompts. *Why it matters*: a static printed photo can't blink on
   cue, so this blocks the most trivial spoofing attempt. **Limitation - read carefully**: this is
   a basic liveness signal, **not real anti-spoofing**. It does **not** defend against a video
   replay of a real person blinking (e.g. playing a recording on a second screen in front of the
   camera), a high-quality mask, or more advanced presentation attacks. Treat it as raising the bar
   above "hold up a photo", not as a security guarantee.
4. **Match confidence display.** The actual cosine similarity is shown as a percentage on both
   outcomes ("Matched - 82% confidence" / "Not matched - 41% confidence, try again") instead of a
   bare pass/fail. *Why it matters*: an admin debugging false rejects/accepts needs to see how
   close a borderline attempt actually was, not just a boolean.
5. **Multi-sample enrolment.** Enrolment captures 3 shots (straight, slight left, slight right)
   and stores **all 3 embeddings** (not their average) against the `Staff` row. *Why all 3 instead
   of one averaged vector*: averaging embeddings from different poses can land in a "blended" point
   that's not actually close to any real pose of the person, diluting the signal; keeping all
   samples and taking the **best** match at verification time (`FaceEmbedder.bestCosineSimilarity`)
   lets whichever enrolled angle is closest to the live pose win, which better tolerates the
   staff member's head angle varying day to day. **Limitation**: this triples the enrolled data
   size and verification does 3x the comparisons - a non-issue at this scale (single-digit
   comparisons), but wouldn't scale as-is to comparing against many people (see the 1:1 vs 1:N
   note below - it doesn't need to, here).
6. **Encryption at rest.** Face embeddings and attendance selfies are encrypted with AES-256-GCM
   backed by an Android Keystore key (`CryptoUtils`) - the key is generated on first use, marked
   non-exportable, and never leaves the keystore in plaintext. **Why Keystore-backed field/file
   encryption over SQLCipher** (whole-database encryption): it required no change to Room's SQLite
   driver, schema, or query layer - only wrapping the two places plaintext would otherwise land
   (the embedding field, the selfie file) - which was the cleaner, lower-risk change to get right
   in the time available. SQLCipher would be the better choice if *all* columns needed protecting
   or a security review specifically required whole-database-at-rest encryption.
   **Limitation**: this protects data *at rest* (e.g. someone pulling the app's data directory off
   a rooted/backed-up device) - it does not protect data while the app holds it decrypted in
   memory during capture/verification, and the Keystore key is tied to this device+app install
   (uninstalling loses the key, so previously-encrypted rows/files become unrecoverable, which is
   consistent with this being local-only storage in the first place).
7. **Reverse-geocoded location display.** Attendance history shows a resolved "locality, region,
   country" string (via `GeocoderHelper` wrapping Android's `Geocoder`) instead of raw lat/lng, with
   the raw coordinates unchanged in the DB and shown as a fallback if geocoding fails (e.g. no
   network - the platform `Geocoder` is typically backed by a network service). *Why it matters*:
   "Warehouse District, CA, USA" is far more useful to a manager reviewing attendance than
   "37.42400, -122.08420".

### Design note: 1:1 verification, not 1:N identification

This app deliberately does **1:1 verification** (compare the live selfie only against the
already-claimed identity's enrolled samples) rather than **1:N identification** (search across
every enrolled staff member's embeddings to figure out who this is). That's the architecturally
correct choice here, not a shortcut: staff already authenticate (log in as `staff`) and their
identity is already known before the camera even opens - `MarkAttendanceActivity` is handed a
specific `staffId` and only ever loads and compares against that one person's samples. There is no
scenario in this app's flow where the system needs to answer "whose face is this, out of everyone
enrolled?" - only "does this face match the person who just logged in?". 1:N search would add
real cost (comparing against every staff member, and materially higher false-accept risk as the
enrolled population grows) to solve a problem this app doesn't have.

## Assumptions & limitations

- **Two hardcoded accounts, no real auth.** There's no signup, password hashing, or backend —
  credentials are literal strings in `LoginActivity`. This is a UI/ML demo, not a production
  auth system.
- **Single staff login maps to one seeded `Staff` row.** The assignment describes one "Staff"
  role, not per-employee accounts, so all staff-side actions (enrol/mark attendance) operate on
  the one demo staff record created on first launch. In a real system each employee would have
  their own login tied to their own `Staff` row.
- **Single face per photo.** `FaceDetectorHelper` picks the largest detected face and ignores
  the rest. Group selfies or bystanders in frame can pick the wrong face.
- **Matching threshold (0.6 cosine similarity)** is a reasonable starting point for MobileFaceNet
  embeddings but has **not been tuned on real device photos**. Expect to raise it (fewer false
  accepts) or lower it (fewer false rejects) after testing with real staff faces and lighting
  conditions. It's a single constant (`MarkAttendanceActivity.MATCH_THRESHOLD`) for easy tuning.
- **Local-only storage.** Face embeddings and attendance records all live on-device (Room +
  app-private files dir), encrypted at rest as described above. Nothing is uploaded anywhere;
  uninstalling the app deletes all enrolment/attendance data (and the Keystore key that protects
  it).
- **Liveness is a blink challenge, not full anti-spoofing** - see point 3 above for exactly what
  it does and doesn't defend against.
- **No re-enrolment history/versioning.** Re-enrolling a face overwrites the previous 3 embeddings;
  there's no audit trail of past enrolments.
- **Bundled model provenance.** `mobile_face_net.tflite` is a publicly available, pre-trained
  MobileFaceNet TFLite export (112x112 input, 192-d output) used purely for on-device inference
  in this demo — it was not trained or fine-tuned as part of this project.
- **No offline/queueing story for location.** If GPS/location can't be resolved (permission
  denied, no fix), attendance is not saved and the user sees an error rather than a
  best-effort fallback (e.g. last-known location).
- **Reverse geocoding needs a resolvable `Geocoder` backend** (typically network access on the
  device). If it's unavailable, attendance history just falls back to showing raw coordinates -
  nothing breaks, but the friendlier display degrades silently.
- **Patched `tensorflow-lite-api` AAR bundled locally** (`app/libs/tensorflow-lite-api-2.12.0-patched.aar`).
  The upstream `tensorflow-lite` and `tensorflow-lite-api` artifacts both declare the same AAR
  manifest namespace, which this project's AGP version rejects as a hard build error. Fixed by
  repackaging a local copy of `tensorflow-lite-api` with a de-duplicated (but functionally
  identical) manifest namespace — see the dependency substitution comment in `app/build.gradle`.
  Purely a build-tooling workaround; doesn't affect app behavior.
- **Attendance DB writes retry once, then surface the failure.** `AttendanceRepository.insert()`
  previously had no error handling at all — an exception on the background executor (e.g. a
  transient SQLite/disk error) was swallowed silently and the UI never found out the record
  wasn't saved. It now retries the write once, and if that also fails, calls an optional
  `onFailed` callback; `MarkAttendanceActivity` deletes the now-orphaned encrypted selfie and
  shows a retry message instead of finishing the screen as if attendance had been recorded.

## Permissions handled

- **Camera** — requested at runtime before opening either camera screen; denial shows a message
  and returns to the previous screen instead of crashing.
- **Location** (fine/coarse) — requested at runtime before marking attendance; if denied, the
  attendance capture flow stops with an explanatory message rather than silently saving a
  record with no location.
- **No face detected** / **face detection error** / **blink timeout** — surfaced as a status
  banner with a **Retry** button, and the user can simply try that shot again.
