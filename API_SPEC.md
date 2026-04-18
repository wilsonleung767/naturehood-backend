# Naturehood Backend API Specification

Base URL: `/`

All REST endpoints are under `/api/**` and require a Bearer JWT, except `/actuator/health`.

## Authentication

- Header: `Authorization: Bearer <jwt>`
- Principal user id comes from JWT `sub` claim.

## Standard REST Response Envelope

All REST endpoints (except SSE stream) return:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

Failure format:

```json
{
  "success": false,
  "data": null,
  "error": "error message"
}
```

## Common Models

### `PostDTO`

```json
{
  "id": "string",
  "authorId": "string",
  "content": "string",
  "images": ["string"],
  "contentType": "string",
  "createdAt": "2026-04-18T12:00:00Z",
  "likeCount": 0,
  "commentCount": 0,
  "repostCount": 0,
  "likedByMe": false
}
```

### `CommentDTO`

```json
{
  "id": "string",
  "postId": "string",
  "parentCommentId": "string or null",
  "authorId": "string",
  "content": "string",
  "images": ["string"],
  "contentType": "string",
  "createdAt": "2026-04-18T12:00:00Z",
  "likeCount": 0,
  "likedByMe": false,
  "replies": []
}
```

Notes:

- For top-level comments, `replies` is present (possibly empty).
- For reply items, `replies` is `null`/omitted.

### `FeedResponse<T>`

```json
{
  "data": [],
  "nextCursor": "opaque-cursor-or-null",
  "hasNext": true
}
```

## Endpoints

## Posts & Comments

### `POST /api/posts`

Create a post.

Request body (`CreatePostRequest`):

```json
{
  "content": "string",
  "images": ["string"],
  "contentType": "string"
}
```

Validation:

- `content` max length: 5000
- `images` max items: 4
- At least one of `content` or `images` must be non-empty

Response: `201 Created`

```json
{
  "success": true,
  "data": { "...PostDTO": "..." },
  "error": null
}
```

---

### `GET /api/posts/{postId}`

Get a single post.

Response: `200 OK` with `ApiResponse<PostDTO>`

---

### `DELETE /api/posts/{postId}`

Soft-delete post (author only).

Response: `200 OK` with `ApiResponse<null>`

---

### `POST /api/posts/{postId}/like`

Toggle like on post.

Response: `200 OK`

```json
{
  "success": true,
  "data": { "liked": true },
  "error": null
}
```

---

### `POST /api/posts/{postId}/comments/{commentId}/like`

Toggle like on comment.

Response: `200 OK`

```json
{
  "success": true,
  "data": { "liked": true },
  "error": null
}
```

---

### `POST /api/posts/{postId}/comments`

Create a top-level comment or reply to any existing comment (unlimited depth).

Request body (`CreateCommentRequest`):

```json
{
  "parentCommentId": "string or null",
  "content": "string",
  "images": ["string"],
  "contentType": "string"
}
```

Validation:

- `content` required, not blank
- `content` max length: 300
- `images` max items: 2
- if `parentCommentId` is provided, it must be an existing comment belonging to the same post

Response: `201 Created` with `ApiResponse<CommentDTO>`

---

### `GET /api/posts/{postId}/comments`

Get paginated top-level comments with recursive reply tree (preview: up to 3 direct children per node).

Query params:

- `cursor` (optional string)
- `limit` (optional int, default `20`)

Response: `200 OK` with `ApiResponse<FeedResponse<CommentDTO>>`

## Feed

### `GET /api/feed`

Get home feed.

Query params:

- `cursor` (optional string)
- `limit` (optional int, default `20`, max `50`)

Response: `200 OK` with `ApiResponse<FeedResponse<PostDTO>>`

---

### `GET /api/feed/stream`

Server-Sent Events stream for real-time feed updates.

Headers:

- `Accept: text/event-stream`
- `Authorization: Bearer <jwt>`

Response type:

- `text/event-stream`

Events:

- Initial keepalive event:
  - `event: ping`
  - `data: ""`
- New post event:
  - `event: new-post`
  - `data` JSON (serialized `NewPostEvent`):

```json
{
  "id": "string",
  "authorId": "string",
  "content": "string",
  "images": ["string"],
  "createdAt": "2026-04-18T12:00:00Z",
  "likeCount": 0,
  "commentCount": 0,
  "repostCount": 0
}
```

## Follow / Social Graph

### `PUT /api/users/{userId}/follow`

Follow target user (idempotent).

Response: `200 OK`

```json
{
  "success": true,
  "data": { "following": true },
  "error": null
}
```

---

### `DELETE /api/users/{userId}/follow`

Unfollow target user (idempotent).

Response: `200 OK`

```json
{
  "success": true,
  "data": { "following": false },
  "error": null
}
```

---

### `GET /api/users/{userId}/followers`

List follower IDs.

Response: `200 OK`

```json
{
  "success": true,
  "data": { "followerIds": ["string"] },
  "error": null
}
```

---

### `GET /api/users/{userId}/following`

List following IDs.

Response: `200 OK`

```json
{
  "success": true,
  "data": { "followingIds": ["string"] },
  "error": null
}
```

---

### `GET /api/users/{userId}/follow-counts`

Get follower/following counts.

Response: `200 OK`

```json
{
  "success": true,
  "data": {
    "followerCount": 0,
    "followingCount": 0
  },
  "error": null
}
```

---

### `GET /api/users/{userId}/is-following`

Check if current user follows target user.

Response: `200 OK`

```json
{
  "success": true,
  "data": { "following": false },
  "error": null
}
```

---

### `GET /api/users/{userId}/posts`

Get paginated posts by target user (newest first).

Query params:

- `cursor` (optional string)
- `limit` (optional int, default `20`, max `50`)

Response: `200 OK` with `ApiResponse<FeedResponse<PostDTO>>`

## Error Statuses

Common HTTP statuses:

- `400 Bad Request` - validation errors, illegal arguments
- `401 Unauthorized` - missing/invalid JWT
- `403 Forbidden` - access denied / security exception
- `404 Not Found` - resource not found
- `500 Internal Server Error` - unexpected server error

REST error body always uses `ApiResponse` failure envelope.
