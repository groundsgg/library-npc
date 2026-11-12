# Source provenance

The numeric evaluator and matrix implementation originate from Grounds
`resourcepacks`, tag `v1.3.0`, commit
`c92fcaffe95e02bf4a6badba700a4c40b5724016`:

`resourcepacks-npc-runtime/src/main/kotlin/gg/grounds/resourcepacks/npc/NpcModel.kt`.

The matching runtime artefact used for baseline tests is
`gg.grounds:resourcepacks-npc-runtime:1.3.0`, SHA-256
`a9b2dcc1846b758cf1840f31be445528be7ba7434ba6981e4a2c5cde515441f4`.
Baseline comparison uses the artefact itself and synthetic test models. It does
not claim byte-identical reconstruction of its private product resources.

The original sources were licensed under AGPL-3.0. The ItemDisplay renderer,
cohort policy, pending metadata and TRSR decomposition come from Grounds
`minestom-lobby`, commit `020a370ff414f85bb6ac495785605a8789ffbb19`.
The extracted library is now offered under MIT following the project owner
Lukas Jost's approval on 2026-11-12; see [licensing decision](license-decision.md).
Git history for the extracted source files lists Lukas Jost as the sole author,
and no additional third-party copyright or licence notices were found in those
files. Earlier source releases and dependency licences are unchanged. External
publication and product-content acceptance remain separate decisions.

Changes at the core boundary remove Scene identifiers/vector types, implicit
Grounds namespace assumptions and the platform-specific ItemDisplay correction.
Definitions and matrix snapshots become immutable. The sampling formulas, bind
pose caches, clip interpolation and named-head handling are retained.
