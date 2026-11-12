# Licensing decision

On 2026-11-12, project owner Lukas Jost approved MIT for the public NPC,
dialogue and cinematic libraries. This repository implements the NPC part of
that decision; the dialogue and cinematic libraries remain planned work.
The full MIT text is in `LICENSE`, with copyright held by Lukas Jost.

The extracted evaluator and renderer originate from Grounds resourcepacks and
minestom-lobby and initially retained those sources' AGPL-3.0 licence. Git history
at the pinned source revisions lists only Lukas Jost as author of the extracted
files, and no additional copyright/licence notices were found in those files.
The owner's explicit approval is the basis for offering this extracted library
under MIT; moving files into a new repository is not itself a licence change.
See [source provenance](source-provenance.md) for the exact source revisions.

This decision does not relicense the original Grounds repositories or earlier
AGPL distributions. Third-party dependencies retain their own licence terms.
Product models, animations, textures and other licensed content are excluded.
The existing Scene and Lobby projects retain their own licensing; an optional
adapter does not change the independent NPC library's MIT licence.

The project owner subsequently authorized GitHub publication and selected the
existing Grounds Release Please / GitHub Packages process. Release 0.1.0 is
published under MIT. Future outside contributions must have compatible licensing
and preserve provenance/notices.
