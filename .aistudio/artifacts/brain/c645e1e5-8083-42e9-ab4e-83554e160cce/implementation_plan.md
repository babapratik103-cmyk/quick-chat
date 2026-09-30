# Quick Chat — Regenerated Engineering Specification & UI/UX Architecture

---

## 1. App Purpose & Identity Philosophy

**Quick Chat** is a native Android messaging application built on a **Username-First Identity Architecture**.

### Core Principles
- **Username as Primary Identity**: The user's globally unique `@username` is their primary visual and functional identity across all screens (chat list, message headers, user discovery, profile tags, and conversations).
- **Secondary Registration Metadata**: Full Name and Age are collected strictly as profile records. They do not supersede or dilute the username in conversations or contact listings.
- **Verification-Only Email**: Email addresses are collected solely for account recovery and confirmation/verification workflows—never exposed as the user-facing identity or used as the login handle.
- **Pure Client & UI/UX Phase**: All current architectural work focuses on polished screen-by-screen UX, component design, interaction states, and local UI state machines. Production backend (Supabase) and final encryption systems are deferred to later phases.
- **Clean Architecture without Fake Data**: The product architecture specifies clean, real user accounts. Temporary dev mocks are isolated and removed from the final product design.

---

## 2. Authentication & Account Rules

### A. Registration Requirements (Exact 6 Fields)
The registration screen collects exactly six fields in sequential form order:
1. **Name** (Full Name, e.g. "Jordan Hayes") — Stored profile metadata.
2. **Username** (Handle, e.g. "jordan_hayes") — Primary identity handle.
3. **Age** (e.g. 24) — Numeric input, integer validation ($\ge 13$).
4. **Email Address** (e.g. "jordan@example.com") — For verification and recovery only.
5. **Password** (Minimum 6 characters, masked with visibility toggle).
6. **Confirm Password** (Must match Password exactly).

#### Frontend Validation Rules:
- **Name**: Not empty, max 50 characters.
- **Username**: Must match `^[a-z0-9_]{3,20}$` (lowercase letters, digits, underscores, 3 to 20 characters).
- **Age**: Integer between 13 and 120.
- **Email**: Standard email pattern (`^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`).
- **Password**: Minimum 6 characters.
- **Confirm Password**: Exact string match with password.

### B. Login Requirements (Username + Password Only)
The login screen accepts **only**:
1. **Username**: Primary account handle (leading `@` stripped automatically).
2. **Password**: Account password.
*(Email is strictly prohibited as a login identifier).*

### C. Logout Semantics
- Normal, clean logout action.
- Confirmation dialog (`Confirm Sign Out` / `Cancel`).
- On confirmation, the active session is cleared and the application returns immediately to the **Login Screen**.
- No unusual secondary credential checks or destructive wipes required on standard logout.

---

## 3. Data Requirements & Database Uniqueness

### Database Constraints (Room / SQLite & Future SQL Schema)
Username uniqueness is enforced at the database layer via unique indexing:

```sql
-- User Entity Table Definition
CREATE TABLE users (
    id TEXT PRIMARY KEY NOT NULL,
    username TEXT NOT NULL COLLATE NOCASE,
    name TEXT NOT NULL,
    age INTEGER NOT NULL,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    avatar_color_hex TEXT NOT NULL DEFAULT '#F59E0B',
    is_active_session INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

-- Database-Enforced Unique Index on Username
CREATE UNIQUE INDEX index_users_username ON users (username);
```

### Domain Data Models
```kotlin
data class User(
    val id: String,
    val username: String,       // Primary identity (e.g. "jordan_hayes")
    val name: String,           // Full Name
    val age: Int,               // Age
    val email: String,          // Verification email
    val avatarColorHex: String  // Theme color for avatar initials
)

data class Conversation(
    val peerUsername: String,   // Primary peer handle (e.g. "alex_rivera")
    val peerName: String,       // Secondary name for reference
    val peerAvatarColorHex: String,
    val lastMessageText: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)

data class Message(
    val id: String,
    val conversationUsername: String,
    val senderUsername: String,
    val recipientUsername: String,
    val text: String,
    val timestamp: Long,
    val status: MessageDeliveryStatus, // SENDING, SENT, DELIVERED
    val isOutgoing: Boolean
)
```

---

## 4. Final Screen Map

```
                           [ App Launch ]
                                 │
                           [ Splash Screen ]
                            ├── Has Active Session ──► [ Home Screen ]
                            └── No Session              ├── Profile Nav ──► [ Profile Screen ]
                                 ├── Login              ├── Search Nav  ──► [ User Search Screen ]
                                 └── Register           │                        │
                                                        │                        ▼
                                                        └──── Select Chat ──► [ Chat Screen ]
```

### Complete Screen Breakdown:
1. **Splash Screen (`splash`)**:
   - Quick Chat emblem, branding, and value proposition.
   - Automatic session resolution: directs logged-in users to `home`, otherwise reveals `Sign In` and `Create Account` actions.
2. **Register Screen (`register`)**:
   - Dedicated 6-field registration form: Name, Username, Age, Email, Password, Confirm Password.
   - Real-time inline field validation and helpful hint for username constraints (`^[a-z0-9_]{3,20}$`).
   - "Create Account" action button and link to "Sign In".
3. **Login Screen (`login`)**:
   - Clean 2-field form: Username and Password.
   - "Sign In" primary action button and link to "Create Account".
4. **Home / Chat List Screen (`home`)**:
   - Header with active `@username`, avatar, profile button, and search button.
   - Filter bar to filter conversations in real-time by peer `@username`.
   - Conversations list displaying:
     - Avatar circle with initials.
     - **Primary Title**: `@peer_username` (bold, white).
     - **Secondary Subtitle**: Peer Name (muted text).
     - Last message preview and relative timestamp (`Just now`, `5m ago`, `10:45 AM`).
     - Unread count pill badge.
   - Empty state when no conversations exist with a prominent "Find Users" call to action.
   - Floating Action Button (`+`) to open User Search.
5. **User Search Screen (`user_search`)**:
   - Strict username discovery interface: Search by `@username`.
   - Results list displaying matched `@username` as the dominant header, initials avatar, and direct "Chat" action button.
   - Tapping an account opens a 1-to-1 conversation on the Chat Screen.
6. **Chat Screen (`chat/{peerUsername}`)**:
   - Top Bar displaying:
     - Back navigation button.
     - Peer avatar circle.
     - **Primary Title**: `@peer_username`.
     - **Secondary Subtitle**: Peer Name / Online status.
   - Scrollable message timeline with automatic scroll-to-bottom.
   - Outgoing and incoming message bubbles with delivery status indicators:
     - `SENDING`: Gray clock icon.
     - `SENT`: Single checkmark.
     - `DELIVERED`: Double checkmarks.
   - Input bar with "Type a message..." text field and amber send button.
7. **User Profile Screen (`profile`)**:
   - Large avatar with dynamic initials.
   - 5 color preset chips for custom avatar styling (`#F59E0B`, `#10B981`, `#6366F1`, `#EC4899`, `#06B6D4`).
   - **Primary Handle Display**: `@username` prominently featured.
   - Profile Details Card: Full Name, Age, Registered Email, and Unique Account ID with copy button.
   - "Save Changes" button for display name and avatar color updates.
   - Standard "Log Out" button with confirmation modal returning directly to the Login Screen.

---

## 5. Navigation Topology

Using Android Jetpack Navigation Compose with a centralized type-safe route definition:

```kotlin
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Register : Screen("register")
    object Login : Screen("login")
    object Home : Screen("home")
    object UserSearch : Screen("user_search")
    object Profile : Screen("profile")
    object Chat : Screen("chat/{peerUsername}") {
        fun createRoute(peerUsername: String): String = "chat/$peerUsername"
    }
}
```

### Backstack & Transition Rules:
- **`Register` $\rightarrow$ `Home`**: Clears `Register` and `Splash` from the backstack so pressing Back exits the app rather than reopening registration.
- **`Login` $\rightarrow$ `Home`**: Clears `Login` and `Splash` from the backstack.
- **`Profile (Logout)` $\rightarrow$ `Login`**: Clears `Home` and `Profile` from the backstack, landing the user on a clean Login destination.
- **Secondary Screens (`Register`, `Login`, `UserSearch`, `Chat`, `Profile`)**: Equipped with explicit Jetpack Compose `BackHandler` and top bar navigation arrows.

---

## 6. UI/UX Architecture & Design System

The visual design system adheres strictly to a clean, modern, dark minimal palette:

### Color Palette
- **Background (`DarkBackground`)**: `#121212`
- **Surface Level 1 (`DarkSurface`)**: `#1A1A1A`
- **Surface Level 2 (`DarkSurfaceElevated`)**: `#242424`
- **Borders & Dividers (`DarkBorder`)**: `#2A2A2A`
- **Primary Accent (`QuickChatPrimary`)**: `#F59E0B` (Amber)
- **Sender Bubble (`BubbleSender`)**: `#2B2113` (Amber Tint Dark)
- **Sender Border (`BubbleBorderSender`)**: `#523D19`
- **Recipient Bubble (`BubbleRecipient`)**: `#222224`
- **Recipient Border (`BubbleBorderRecipient`)**: `#333336`
- **Primary Text (`TextPrimary`)**: `#FFFFFF`
- **Secondary Text (`TextSecondary`)**: `#9CA3AF`
- **Muted Text (`TextMuted`)**: `#6B7280`
- **Error / Warning (`StatusError`)**: `#EF4444`

### Typography Hierarchy & Sizing
- **Screen Title**: `20.sp`, Bold, `TextPrimary`
- **Primary Username Header**: `16.sp`, SemiBold, `TextPrimary` (`@username`)
- **Secondary Name Subtitle**: `13.sp`, Regular, `TextSecondary`
- **Body / Bubble Text**: `15.sp`, Regular, line height `20.sp`
- **Timestamp / Metadata**: `11.sp`, Regular, `TextMuted`
- **Form Label / Helper**: `12.sp`, Medium, `TextSecondary`

### Touch Target & Component Standards
- **Touch Target Size**: Minimum `48.dp x 48.dp` across all buttons, icon buttons, list items, and avatar pickers.
- **Corner Radii**: Consistent `12.dp` rounded corners for all inputs, dialogs, cards, and message bubbles.
- **Input Fields**: Outlined/filled dark containers (`#1A1A1A`) with subtle border (`#2A2A2A`), amber focus borders, and clear leading icons.

---

## 7. Phased Roadmap (Step-by-Step Alignment)

- **STEP 1 [COMPLETED]**: Quick Chat Architectural Specification.
- **STEP 2 [CURRENT]**: Specification Verification & Alignment against user requirements:
  - Exact 6-field registration.
  - Username-focused identity & username-only login.
  - Database-enforced username uniqueness index.
  - Standard clean logout.
  - Explicit UI/UX focus without premature backend/encryption code.
- **STEP 3**: Build UI/UX Screen by Screen (Refactor Register with 6 fields, update Login to username-only, align Home/Search/Chat/Profile to username-primary styling).
- **STEP 4**: Frontend Functionality & Local Reactive Pipeline (Room uniqueness validation, input error states, conversation tracking).
- **STEP 5 [FUTURE]**: OpenCode + Supabase Backend Integration (PostgREST & Realtime relay).
- **STEP 6 [FUTURE]**: Secure Messaging (NIST P-256 ECDH, AES-256-GCM, ACK deletion, 7-day TTL).
