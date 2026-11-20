# PRD: Chess Analyst — Android Application

## 1. Product Overview

**Chess Analyst** is an offline-first, Android-native chess game review and position analysis application inspired by Chess.com's Game Review workflow. Players can import PGN files or text, construct or paste FEN positions, or fetch their latest public Chess.com games using only their public username—with **zero user registration, zero login, zero cloud accounts, and zero local or remote databases**.

All game data lives strictly in memory for the current app session and is discarded on app exit. Only lightweight user preferences (engine depth, coordinates, Maia-3 model selection & target Elo, and last connected Chess.com username) are stored via Jetpack DataStore Preferences.

---

## 2. Core Constraints & Privacy Guarantees

- **No Registration / No Login / No Accounts / No Passwords.**
- **No Database**: No Room, SQLite, Firebase, Firestore, or Supabase.
- **Offline-First**: Board play, PGN/FEN import, local tactical evaluation, game review, phase analytics, and downloaded Maia-3 models work 100% offline.
- **Minimal Network Usage**: Internet is only used for:
  1. Fetching public profiles & recent game archives from the official Chess.com Published-Data API (`https://api.chess.com/pub/...`).
  2. Optional, user-initiated downloads of Maia-3 ONNX neural network models from Hugging Face (`bqrio/maia3-onnx`).
- **Zero Storage Permissions**: Uses Android Storage Access Framework (`OpenDocument`) for PGN file import and Android Share Sheet (`ACTION_SEND`) for annotated PGN export.

---

## 3. Feature Requirements & Milestones

### Milestone 1: Android UI & Tournament Chessboard
- Dark Obsidian & Tournament Emerald editorial theme (`#161512` background, `#EEEED2` cream / `#769656` forest green board squares).
- Original vector-drawn Staunton-style pieces on Jetpack Compose `Canvas`.
- Smooth spring piece animations, last-move highlight, legal-move dots/capture rings, king-in-check radial glow, coordinate labels (`a-h`, `1-8`), board flipping, full move navigation (`|<`, `<`, `>`, `>|`), best-move (emerald) and Maia-3 human-move (amber) arrows, and interactive analysis variation exploration.
- Vertical animated evaluation bar beside the board.
- Home Screen with 3 prominent action cards (**Import PGN**, **Import FEN**, **Chess.com Games**) plus **Session Recent Analyses** list.

### Milestone 2: Chess Core, PGN & FEN Import
- Pure-Kotlin complete chess rules engine: all legal moves, pins, double check, castling rights & through-check checks, en passant (including discovered-check edge case), pawn promotion picker, checkmate, stalemate, 50-move rule, insufficient material, threefold repetition.
- **PGN Import**: Open `.pgn` via Storage Access Framework, paste raw PGN text, multi-game PGN parsing & game picker, graceful handling of malformed/incomplete PGNs, built-in ECO opening classifier (120+ openings).
- **FEN Import & Board Editor**: Paste & validate FEN notation (strict validation of kings, pawn ranks, side to move, castling rights, en passant square, and illegal checks), interactive piece placement palette, clear/reset board, side-to-move toggle, castling rights checkboxes (`K`, `Q`, `k`, `q`), and instant position analysis with both Stockfish tactical lines and Maia-3 human predictions.

### Milestone 3: Stockfish / Tactical Engine Integration
- **Dual-Mode Local Engine Architecture** (100% Google Play compliant — no runtime `.so` downloading/DCL):
  - **Native Stockfish UCI Bridge (`StockfishUciClient`)**: Full UCI protocol driver (`uci`, `setoption`, `isready`, `position fen`, `go depth/movetime`, `stop`, `quit`, MultiPV parsing) that automatically activates if a bundled `libstockfish.so` binary is present in `nativeLibraryDir`.
  - **Built-In Local Multi-PV Tactical Engine (`LocalTacticalEngine`)**: Genuine pure-Kotlin iterative-deepening Negamax + Principal Variation Search (PVS) + Quiescence Search (MVV-LVA) + Zobrist Transposition Table + Killer/History heuristics + Null-Move Pruning + LMR + PeSTO Tapered Evaluation (middlegame/endgame piece-square tables, king safety, passed/isolated/doubled pawns, bishop pair, rook open files, endgame mop-up). Computes real centipawn evaluations, forced mates, and Top-3 Principal Variations locally on background threads with cooperative cancellation and conservative memory/thread limits.

### Milestone 4: Game Review & Documented Move Classification
Every move is classified using a transparent, open algorithm (explicitly documented as an original open-source formula, *not* Chess.com's proprietary formula):
- **Win Probability Curve**:
  `Win%(cp) = 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)`
  (Forced mate scores map to `100%` or `0%`.)
- **Win-Probability Loss**: `ΔW = max(0, Win%_before - Win%_after)` from the mover's perspective.
- **Classification Thresholds**:
  - **Brilliant (`!!`)**: Material sacrifice (net material drop >= 150 cp or piece offered en prise) that is top/near-top (`ΔW <= 1.5%`), position was not already trivially won (`evalBefore < +600 cp`), and second-best move is significantly worse (`gap >= 120 cp`).
  - **Great (`!`)**: Critical only-move (`ΔW <= 2.0%` and second-best gap `>= 150 cp`) that finds a sharp tactic or saves a losing position.
  - **Best (`★`)**: Matches engine's #1 move or `ΔW <= 1.0%` and `CPL <= 10`.
  - **Excellent**: `ΔW <= 2.5%` (or `CPL <= 25`).
  - **Good**: `ΔW <= 5.5%` (or `CPL <= 55`).
  - **Inaccurate (`?!`)**: `ΔW <= 10.0%` (or `CPL <= 110`).
  - **Miss**: Best move achieved/maintained a winning position (`bestEval >= +250 cp` or forced mate), played move failed to win (`playedEval >= -50 cp`, `ΔW >= 10%`) without blundering into a loss.
  - **Mistake (`?`)**: `ΔW <= 20.0%` (or `CPL <= 220`).
  - **Blunder (`??`)**: `ΔW > 20.0%`, `CPL > 220`, or allowing/missing forced checkmate.
- Interactive Evaluation Graph + per-move tactical explanations + best alternative PV playback.

### Milestone 5: Accuracy & Estimated Performance Rating
- **Per-Move Accuracy**: `acc = clamp(103.1668 * exp(-0.04354 * ΔW) - 3.1669, 0, 100)`
- **Player Accuracy**: Volatility-weighted harmonic/arithmetic blend of move accuracies for White and Black independently.
- **Estimated Performance Rating**: Derived from accuracy and ACPL (and calibrated against opponent Elo when present in PGN headers).

### Milestone 6: Game Phase Analysis
Divides every game into **OPENING**, **MIDDLEGAME**, and **ENDGAME**:
- **Opening**: Starts at move 1; transitions to Middlegame once total non-pawn material drops below 50 (from 62), queens are traded, both sides have developed >= 3 minor pieces / castled past move 10, or after move 15.
- **Middlegame**: Continues until Endgame trigger.
- **Endgame**: Triggered when total non-pawn material `<= 26` points (Q=9, R=5, B=3, N=3) or both sides individually have `<= 13` non-pawn points.
- Calculates separate accuracy, ACPL, move count, and classification breakdown for White and Black in each reached phase.
- Any unreached phase explicitly displays: **"Not reached in this game."**

### Milestone 7: Maia-3 Human-Like Engine (ONNX Runtime)
- Integrates `com.microsoft.onnxruntime:onnxruntime-android` for local on-device inference.
- Optional, user-controlled model downloads from `bqrio/maia3-onnx` with clear storage & RAM descriptions:
  - **Maia-3 3M (FP16)** — ~6.5 MB (Fast / Low RAM)
  - **Maia-3 5M (FP16)** — ~10.8 MB (Recommended for mobile)
  - **Maia-3 23M (FP16)** — ~46 MB (High accuracy)
  - **Maia-3 79M (FP16)** — ~158 MB (Large — opt-in only, never loaded by default)
- Encodes board state into Maia-3's `[1, 64, 12]` piece-plane tokens (mirrored for Black) + `[1]` `elo_self` & `elo_oppo`, runs ONNX session lazily on background threads with conservative thread cap, masks logits over legal moves in the 4,352-entry vocabulary (`4096` from-to + `256` promotion entries), and outputs top human-like move predictions with exact probabilities and WDL expectations.
- Never replaces Stockfish tactical evaluation; displayed side-by-side as "Human-Like Insight (Maia-3)".

### Milestone 8: Chess.com Published-Data API Integration
- Public username connection screen (zero password fields).
- Sends required `User-Agent` header.
- Workflow: Validate username -> Fetch `/pub/player/{username}` -> Fetch `/pub/player/{username}/games/archives` -> Fetch newest monthly archive(s) until latest 10 standard chess games are gathered (no unnecessary historical downloads) -> Parse PGNs -> Sort descending by `end_time`.
- Displays opponent username, player & opponent ratings, exact time control (`3+0`, `3+2`, `5+0`, `10+0`, `15+10`, etc.) + category (`Bullet`, `Blitz`, `Rapid`, `Classical`, `Custom`), formatted date, opening name, perspective-based `WIN`/`LOSS`/`DRAW` badge, Refresh button, and one-tap **Analyze** button.
- Graceful handling of invalid usernames (404), closed/private profiles (410), unavailable archives, network offline, and rate limits (429).

### Milestone 9: Performance, Testing & Verification
- Lazy engine initialization, background `Dispatchers.Default`/`IO` execution, cooperative cancellation, low-memory (`onTrimMemory`) model unloading, conservative thread/hash limits.
- Automated JUnit & Robolectric test suite covering chess rules, FEN/PGN parsing, move classification, accuracy/phase math, Chess.com DTO mapping, and Compose UI journeys.
