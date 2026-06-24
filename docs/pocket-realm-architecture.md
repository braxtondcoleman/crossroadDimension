# Pocket Realm Architecture

## Version Choice

This project currently targets Minecraft/NeoForge `26.1.2` with NeoForge `26.1.2.76`.

The matching Infiniverse release is `infiniverse-26.1.0.1.jar`, published for `26.1.2` on NeoForge. The CurseForge project id is `568341`, and the file id used by CurseMaven is `8283158`.

The public Commoble Maven contains `26.1.0.0`, but the CurseForge page lists `26.1.0.1` as the current `26.1.2` NeoForge file, so the build uses the exact CurseMaven artifact:

```gradle
implementation "curse.maven:infiniverse-568341:${infiniverse_file_id}"
```

## How Infiniverse Works

Infiniverse is a server-side API mod for adding and removing dimensions while a Minecraft server is already running. It does not add gameplay content by itself. Its job is to expose an API that other mods can call when dimensions must be created from runtime data, such as a player id, a selected template, or another user-driven choice.

For static dimensions, normal datapack dimension JSON is still the right approach. Infiniverse is useful when the dimension key and dimension definition are not known until the server is running.

Infiniverse is primarily required on the server. If installed on the client too, newly added or removed dimensions become visible immediately in command suggestions.

## Runtime Dimension Creation

Runtime dimension creation should be server-owned.

The flow should eventually be:

1. Build a stable dimension id such as `crossroaddimension:pocket_realm/<player_uuid>`.
2. Build or select the dimension definition for that realm.
3. Ask Infiniverse to register/load that dimension on the running server.
4. Cache the resulting server level lookup in mod-side state only as a convenience, not as the source of truth.
5. Persist enough metadata to recreate or locate the dimension after server restart.

The current code intentionally does not create a dimension yet. The new realm classes only define ownership and service boundaries.

## Player-Owned Dimensions

A player-owned realm should be represented by durable metadata, not by a live `ServerLevel` reference.

Recommended fields:

- `owner`: the player's UUID.
- `dimensionId`: the stable namespaced dimension id.
- `lastReturnLocation`: the last known overworld/source position for returning.
- Later: creation time, template id, generation settings, spawn position, access list, display name, and migration/version fields.

`PocketRealmData` is the first skeleton of this metadata.

## Recommended Architecture

`PocketRealmData` is the serializable record of one realm. It should eventually be stored in saved data or another server-persistent store.

`PocketRealmManager` owns lookup, id construction, persistence, and Infiniverse integration. It should be the only class that knows how a UUID maps to a dimension id.

`PocketRealmService` owns game-facing use cases. Travel packets and commands should call this service instead of directly touching Infiniverse or saved data.

The packet handler should stay thin:

1. Receive travel request.
2. Get `ServerPlayer` from network context.
3. Validate request.
4. Call `PocketRealmService.requestTravelToRealm(player)`.

## Future Travel Flow

Creation:

1. Player confirms travel.
2. Server receives the travel packet.
3. `PocketRealmService` asks `PocketRealmManager` for the player's realm.
4. If none exists, `PocketRealmManager` creates `PocketRealmData` with a stable dimension id.
5. The manager persists that metadata.
6. A later implementation will call Infiniverse to register the runtime dimension.

Lookup:

1. Use `ServerPlayer#getUUID()`.
2. Look up `PocketRealmData` by UUID.
3. Derive the expected dimension id if the realm has not been persisted yet.

Loading:

1. Resolve the dimension id from `PocketRealmData`.
2. Check whether the server already has the level loaded.
3. If missing, call Infiniverse to add/load the runtime dimension.
4. Re-query the server for the loaded level.

Teleporting Into:

1. Save the player's current position as a `GlobalPos`.
2. Ensure the realm dimension exists and is loaded.
3. Choose a safe spawn/arrival position inside the realm.
4. Teleport the player server-side.

Returning:

1. Confirm the player is currently in their own pocket realm.
2. Read `lastReturnLocation`.
3. Ensure the return dimension is loaded.
4. Teleport the player back server-side.
5. Clear or update transient travel state as needed.

No full realm creation or teleportation is implemented yet.
