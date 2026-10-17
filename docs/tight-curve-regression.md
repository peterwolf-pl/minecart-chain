# Steam consist tight-curve regression checklist

The steam path controller is authoritative for ordinary-rail followers, with or without a permanent tender. Mounted followers keep their Splinecart controller. Do not apply radial chain/buffer corrections to either steam consist: on a hairpin, neighbouring legs of the track can be spatially close but far apart along the path.

Changes covered:
- Fixed cumulative arc-length spacing (no chord-distance search that can jump up to 4.8 blocks).
- Long wagon body yaw/pitch sampled between its front and rear axle positions.
- No second vanilla physics step after a follower is positioned.
- Mounted follower front transported continuously along its local tangent rather than aligned to a remote target on another leg of a bend.

In-game checks (not yet performed):
1. Steam locomotive + three long wagons, with and without tender: traverse straight, 90-degree bend, hairpin and consecutive S bends at low and full throttle.
2. Repeat on vanilla and Splinecart tracks. Watch the second and third wagons, not just the tender.
3. Stop halfway through each bend, restart, brake and select reverse. Check no follower is pushed sideways or changes physical front when the locomotive is on the opposite leg of the bend.
4. Detach and reattach the last wagon; ensure its old tangent does not remain cached after detachment.
5. Include a sloped bend and chunk reload.

Limitations: fixed arc spacing does not guarantee body clearance on arbitrarily small radii for rigid two-block wagons. The existing reverse breadcrumb translation is not a complete tail-led route recorder; that architectural change still requires a separate implementation and in-game validation.
