Project configuration and product requirements
## PROJECT IDENTITY
Engineers' Day 2026 — MIT TECH KERNEL.
Build a production-quality event platform combining registration, event discovery, live event control, and a cinematic cyberpunk experience.
## PRIMARY VISUAL REFERENCE
Strong Cyberpunk 2077 / Night City influence: dense neon megacity, rain, wet reflective asphalt, holographic signage, futuristic corporate megastructures, brutalist architecture, industrial infrastructure, cybernetic technology, aggressive neon lighting, high-tech/low-life atmosphere, HUD interfaces, glitch/scanline effects, fog, huge scale, and a futuristic high-performance car.
Use original MIT TECH KERNEL assets, artwork, world-building, layouts, vehicle designs, and UI. Capture the genre atmosphere without copying proprietary game assets.
## CORE PRODUCT GOAL
The website must feel like a cinematic cyberpunk game world, not a normal college registration site with neon styling.
Registration and event operations must remain clear and usable. Spectacle must never compromise practical navigation, accessibility, performance, or registration correctness.
## CRITICAL REGISTRATION / EVENT SEPARATION
Registration and event execution are separate states.
registered + event not started = no execution access
registered + event live = execution access
The frontend must never be authoritative for eligibility, capacity, registration, submissions, or results.
## CURRENT REGISTRATION PHASE
Registration deadline: September 29, 2026.
Until September 29, the primary priority is registration and event discovery. Do not build full execution environments first.
Actual event execution access is manually opened by an administrator on event day.
## EVENT STRUCTURE
First Year / Freshers:
- Non-tech: Chess, Tech Debate, FIX IT
- Tech: Ideathon, BuildX, Debugging
Second Year:
- Common: Chess, Tech Debate, FIX IT, Ideathon
- Exclusive: Rapid Research
## EVENT ELIGIBILITY
Chess: 1st + 2nd
Tech Debate: 1st + 2nd
FIX IT: 1st + 2nd
Ideathon: 1st + 2nd
BuildX: 1st only
Debugging: 1st only
Rapid Research: 2nd only
## EVENT PARTICIPATION TYPES
Chess: solo
BuildX: solo
Debugging: solo
Tech Debate: exactly 10
FIX IT: 1–4
Ideathon: 1–4
Rapid Research: 1–4
## TEAM REGISTRATION
Team-based events require server-side team validation. The system must enforce the exact team-size rules and prevent invalid registration states.
## BUILDX
BuildX is a first-year-only solo event. Participants receive a theme and can build anything they want locally in VS Code or their own environment.
The website provides registration, event-control, theme, instructions, and submission functionality. It is not a replacement for the participant's IDE.
## BUILDX CAPACITY
Hard maximum: 30 seats.
Capacity must be enforced server-side.
## DEBUGGING COMPETITION
Debugging is a first-year-only solo competition involving intentionally broken code/problems.
The system should eventually support:
- start/end time
- problem assignment
- attempts
- test execution
- submission tracking
- result evaluation
- submission locking
- result-pending state
- eventual winner/result publication
Participant-facing status should support messages such as SUBMISSION RECORDED and RESULT PENDING. Do not immediately reveal winners.
## DEBUGGING PROBLEM GENERATION
The debugging system should support two approaches:
1. A curated problem bank containing approximately 100+ prevalidated debugging problems across multiple programming languages.
2. AI-assisted generation using Google Gemini, where a problem can be generated for the participant's selected/preferred language.
Difficulty must be normalized across languages. The system should not simply translate the same code between languages; each generated problem should target an equivalent difficulty profile.
Every generated problem must pass an automated validation pipeline before being released to a participant. The pipeline should verify:
- the buggy program actually fails or produces incorrect behavior
- the corrected/reference solution passes
- tests distinguish buggy and corrected behavior
- the intended bug category is present
- expected difficulty constraints are satisfied
- the code compiles/runs in the target language
- the problem is deterministic and reproducible where required
AI generation must be treated as a content-generation service, not as an authority. The backend remains authoritative for problem assignment, timing, submissions, scoring, and results.
A future normalized difficulty model should consider factors such as bug category, code size, control-flow complexity, number of interacting concepts, debugging depth, test coverage, and expected fix complexity. Difficulty should be calibrated using benchmark problems across every supported language.
## FIX IT
FIX IT is a 1–4 member team event.
Participants receive a business/startup situation such as falling sales, high costs, weak marketing, low customer retention, poor brand positioning, increasing competition, or low product demand.
They analyze the situation, identify core problems, create a turnaround strategy, and present it to judges.
## IDEATHON
Ideathon is available to first- and second-year participants in teams of 1–4. It has a configurable limited capacity.
## RAPID RESEARCH
Rapid Research is a second-year-only team event with 1–4 members.
Participants receive a problem statement, receive a limited preparation window, prepare a research paper, and submit it. Papers are evaluated, selected teams present, and one winner is determined.
The actual workspace becomes available only after an administrator starts the event.
## TECH DEBATE
Tech Debate is available to first- and second-year participants. Teams must contain exactly 10 members.
## CHESS
Chess is available to first- and second-year participants and is solo.
## REGISTRATION CLOSURE
BuildX has a hard capacity of 30.
Ideathon has configurable capacity.
Debate, Chess, Rapid Research, Debugging, and FIX IT are manually closed by administrators. Do not assume automatic capacity limits for these events.
## ADMIN EVENT CONTROL
Administrators manually control event lifecycle.
Suggested event states:
-NOT_STARTED
-LIVE
-SUBMISSION_OPEN
-SUBMISSION_CLOSED
-UNDER_EVALUATION
-RESULTS_PUBLISHED
Suggested registration states:
REGISTRATION_OPEN
REGISTRATION_CLOSED
SOLD_OUT
## CORE USER FLOW
Discover the world → inspect events → understand eligibility/team rules → register → receive Engineer identity/pass → return for event-day access → enter event execution when admin opens it → submit → await evaluation → view published results.
## ENGINEER IDENTITY
The platform should give registered participants a persistent Engineer identity/profile that can later support event registrations, event passes, check-in, event-day access, and participation history.
## EVENT PASSES
Registered participants should receive event-specific passes/credentials suitable for event-day access and operational verification.
## MULTI-EVENT REGISTRATION
Participants can register for multiple eligible events, subject to each event's rules and capacity. The backend must validate each registration independently.
## CINEMATIC WORLD
Events are physical locations/districts in a cyberpunk city.
Scrolling should feel like travelling through the city rather than scrolling through a list of cards.
## CAR — PRIMARY VISUAL PROTAGONIST
A futuristic high-performance car is a major visual protagonist.
The car should start, accelerate, travel through the city, transition between event districts, and act as a visual anchor for the cinematic sequence. It should not merely be a background decoration.
## CINEMATIC SCENE STRUCTURE
Suggested sequence:
1. Black/system boot — MIT TECH KERNEL / ENGINEERS' DAY 2026 / initialization
2. Enter city
3. Car startup
4. High-speed city drive
5. Debugging district
6. Highway/tunnel transition
7. BuildX district
8. Ideathon district
9. Debate district
10. FIX IT district
11. Rapid Research district
12. City overview
13. Practical navigation/events/dashboard
The sequence should use camera movement, vehicle movement, acceleration, depth, perspective, lighting, environment transformation, and physical scene transitions rather than generic fade/slide animations.
## EVENT DISTRICTS
Debugging: servers, corrupted code, red warnings, recovery/stability visuals.
BuildX: construction, cranes, robotic arms, blueprints.
Ideathon: neural-network nodes, connected ideas, data structures.
Debate: large futuristic arena with opposing visual language converging.
FIX IT: corporate crisis environment, revenue graphs, business dashboards, turnaround visuals.
Rapid Research: research terminals, papers, data, countdown systems.
## EVENT INFORMATION
Event information should be embedded naturally into the world where possible: environmental HUDs, holographic displays, signage, dashboards, terminals, physical objects, or other diegetic interfaces.
Practical event information must remain easy to access even without consuming the cinematic sequence.
## PRACTICAL EVENTS PAGE
Provide a normal, highly usable events interface in addition to the cinematic experience. Users must be able to quickly compare events, inspect rules, understand eligibility, register, and manage registrations.
## NAVIGATION
Practical navigation must always remain accessible. The cinematic sequence must never trap the user.
## MOTION PHILOSOPHY
Avoid a website made primarily from fade-ins, slide-ins, generic parallax, and floating cards.
Use:
- camera movement
- vehicle movement
- acceleration/deceleration
- perspective/depth
- environmental transformation
- object assembly
- lighting changes
- physical transitions
- scroll-driven narrative progression
## SCROLL ARCHITECTURE
Scrolling should control the cinematic timeline. GSAP ScrollTrigger should coordinate scene progress, camera movement, car movement, environment transitions, and information reveals.
Lenis should provide smooth scrolling while remaining compatible with ScrollTrigger.
## TYPOGRAPHY
Typography should be integrated into the environment.
Examples:
- Debugging: corrupted → recovering → stable → title
- BuildX: letters assembling from components
- Ideathon: text formed from connected nodes
- Debate: opposing text converging
- FIX IT: metrics/corporate terminology
- Rapid Research: research terms/countdown
## TECHNOLOGY STACK
Frontend:
- React
- TypeScript
- Vite
- Tailwind CSS
- GSAP
- ScrollTrigger
- Lenis
- Three.js
- React Three Fiber
- Drei
Backend direction:
- Java
- Spring Boot
- PostgreSQL
- Spring Security
- REST APIs
- WebSockets
## DOM / CSS VS GSAP VS THREE.JS
Use DOM/CSS for practical UI, forms, registration, dashboards, accessibility, and stable content.
Use GSAP/ScrollTrigger for cinematic sequencing, scroll-driven timelines, camera-like movement, transitions, and orchestration.
Use Three.js/React Three Fiber only where genuine 3D/WebGL provides value: vehicle, city geometry, camera depth, large-scale environments, lighting, and selected 3D effects. Do not put the entire site into WebGL.
## PERFORMANCE
Target approximately 60 FPS on capable desktop hardware.
Avoid unnecessary WebGL complexity. Lazy-load heavy assets, optimize textures/models, reduce draw calls, reuse geometry/materials, and keep practical UI lightweight.
## MOBILE
Mobile should have a simplified 2.5D/DOM-first fallback rather than forcing the full desktop cinematic scene. Preserve the story, event discovery, registration, and usability.
## ACCESSIBILITY
Respect prefers-reduced-motion. Provide a reduced-motion experience that preserves all information and functionality without requiring cinematic animation.
## RESPONSIVE DESIGN
The experience must work across desktop, laptop, tablet, and mobile layouts. Practical registration flows must remain usable independently of the cinematic presentation.
## EVENT DATA ARCHITECTURE
Event definitions should be data-driven and separated from presentation.
A centralized event model should contain:
- id
- name
- description
- year eligibility
- participation type
- team size
- capacity
- registration state
- event state
- instructions
- theme/problem configuration
- submission configuration
## REGISTRATION STATE
registrationStatus:
REGISTRATION_OPEN
REGISTRATION_CLOSED
SOLD_OUT
-eventStatus:
-NOT_STARTED
-LIVE
-SUBMISSION_OPEN
-SUBMISSION_CLOSED
-UNDER_EVALUATION
-RESULTS_PUBLISHED
## CAPACITY
Capacity enforcement is server-side.
BuildX: 30 hard seats.
Ideathon: configurable capacity.
Other events: manual closure unless future requirements explicitly add capacity.
## BACKEND DIRECTION
The future Spring Boot backend will own:
- authentication
- authorization
- eligibility
- registration
- team management
- capacity
- event state
- Engineer identity
- event passes
- submissions
- scoring/evaluation
- results
- administrative controls
## WEBSOCKET / LIVE EVENT MODE
Future WebSockets should power synchronized countdowns, live event state, submission locks, administrative broadcasts, and other real-time event controls.
Do not fake authoritative live state entirely on the client.
## ADMIN CONTROL
Admin interfaces should prioritize speed, clarity, and operational correctness over cinematic effects.
Admins must be able to:
- open/close registration
- open event execution
- transition event states
- manage capacities where applicable
- control submissions
- publish results
- monitor event activity
## DEVELOPMENT PHASES
Phase 1: architecture and project foundation
Phase 2: cinematic visual prototype
Phase 3: event discovery and practical UI
Phase 4: registration flows
Phase 5: backend integration
Phase 6: event-day execution systems
Phase 7: admin control
Phase 8: debugging engine and AI problem generation
Phase 9: WebSocket live event mode
Phase 10: performance, accessibility, testing, deployment
## CURRENT PRIORITY
Registration/discovery is the immediate priority until September 29, 2026.
Do not overbuild event execution environments before registration is ready.
## DEVELOPMENT WORKFLOW
Inspect before modifying. Prefer architecture-first decisions. Build in meaningful milestones. Keep responsibilities modular. Avoid giant components and tightly coupled event-specific logic.
## GIT
Use meaningful commits at major milestones. Keep the repository clean and reversible.
## CODE QUALITY
Apply SOLID principles, modular architecture, clear TypeScript types, separation of concerns, reusable components, data-driven event definitions, and backend-authoritative business rules.
## DESIGN QUALITY BAR
The final website should look intentionally art-directed, immersive, coherent, and premium. Avoid generic SaaS/dashboard aesthetics for the cinematic layer.
## GAME-LIKE QUALITY BAR
The experience should feel like entering a fictional cyberpunk city and travelling through it, not browsing a themed college website.
## EASTER EGGS
Optional subtle easter eggs may reference developer culture, MIT TECH KERNEL, engineering, programming, systems, or the fictional city. They must not interfere with core usability.
## SECURITY
Never trust frontend validation for authorization, eligibility, capacity, timing, submission state, or results. Validate all critical operations server-side. Protect admin operations and participant data.
## FUTURE AUTHENTICATION
Future authentication should integrate with the Spring Boot backend and Spring Security. Engineer identity and event registrations should be associated with authenticated users.
## CRITICAL UX PRINCIPLE
The spectacle attracts the user; usability lets them complete the task.
## CRITICAL EVENT PRINCIPLE
Registration, execution, submission, evaluation, and results are separate lifecycle concerns.
## CURRENT DEVELOPMENT STAGE
Repository initialized. Frontend project foundation is next. Architecture should be reviewed before implementation.
## FIRST IMPLEMENTATION PRINCIPLE
Do not immediately build the entire city. First prove the cinematic architecture with one polished end-to-end district and the vehicle/camera system, then scale the world.
## FINAL PRODUCT PRINCIPLE
The website should feel like a game world built by engineers, while remaining a reliable event platform built for real students and administrators.
## DEBUGGING AI GENERATION — RECOMMENDED TECHNICAL DESIGN
Use a hybrid problem system rather than relying exclusively on either a static bank or live AI generation.
- Curated bank: maintain roughly 100+ prevalidated problems across supported languages and bug categories.
- AI generation: use Gemini to generate fresh variants in the participant's selected language.
- Difficulty calibration: define language-independent difficulty profiles and generate/validate against the same profile.
- Validation sandbox: compile/run the buggy and reference solutions against hidden tests before assigning a problem.
- Canonical test suite: use a language-neutral test specification where possible, then generate language-specific harnesses.
- Deterministic assignment: save the generated problem, seed/version, tests, reference solution, and metadata so the exact challenge can be reproduced.
- Safety: sandbox execution with strict CPU, memory, filesystem, network, and time limits.
- Fallback: if Gemini generation fails validation, automatically select a validated curated problem rather than exposing an unverified problem.
- Fairness: do not let one language receive systematically shorter code, simpler bug classes, fewer interacting concepts, or easier tests.
- Calibration: benchmark problems by empirical solve time and failure patterns after trial runs, then adjust difficulty metadata.
## SUGGESTED DEBUGGING PROBLEM METADATA
- language
- difficultyProfile
- bugCategory
- concepts
- codeSize
- controlFlowComplexity
- expectedFixComplexity
- testCount
- hiddenTestCount
- timeLimit
- memoryLimit
- problemVersion
- generationSource (CURATED / GEMINI)
- generationModelVersion
- validationStatus
- referenceSolutionHash
- seed
## IMPORTANT ARCHITECTURE RULE
Gemini generates candidate debugging content; the platform validates and controls it. Gemini must never decide whether a participant's submission is correct, whether a problem is valid, or who wins. Those decisions belong to deterministic backend evaluation and administrative rules.