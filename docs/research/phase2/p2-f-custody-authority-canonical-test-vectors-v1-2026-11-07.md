# P2-F Canonical Test Vectors v1

> HISTORICAL PAYLOAD-ONLY FIXTURES / NON-AUTHORITATIVE. Superseded by the [complete v7 fixtures](p2-f-custody-authority-v7-closure-test-vectors-2026-11-07.md) under the [v7 correction closure](p2-f-custody-authority-v7-correction-closure-2026-11-07.md). Do not implement a verifier from these old fragments.

These vectors are frozen review fixtures. The bytes use deterministic CBOR: small maps with integer keys, minimal unsigned integers, and fixed bstr values. Digests are SHA-256 over the exact domain prefix plus the listed bytes. A future verifier must reproduce these values and reject the negative cases.

| Case | Canonical CBOR hex | Expected domain digest |
|---|---|---|
| CONTINUITY_CHALLENGE `{0:1,1:1,2:h'00'*32,3:h'01'*32,4:1}` | `a500010101025820000000000000000000000000000000000000000000000000000000000000000358200101010101010101010101010101010101010101010101010101010101010401` | `07018659acb1931ab5f1fc0bd3b446cf800561a4b94b80a2690d35a453adf603` |
| AUTHORITY_ISSUED payload `{0:1,1:h'02'*32,2:h'03'*32}` | `a30001582002020202020202020202020202020202020202020202020202020202020202582003030303030303030303030303030303030303030303030303030303030303` | `0b47584386d69066e601b268e654e5d8d771ce94a749d85151edc61de0d08849` |
| ESCAPE_COMMITTED payload `{0:h'04'*32,1:h'05'*32,2:7,3:h'06'*32,4:1,5:h'07'*32}` | `a600582004040404040404040404040404040404040404040404040404040404040404045820050505050505050505050505050505050505050505050505050505050505050502070358200606060606060606060606060606060606060606060606060606060606060604010558200707070707070707070707070707070707070707070707070707070707070707` | `f7c125c2e8fafd4926127643c1d49394a8a950b3a060975c7d4f1a9f20a667c1` |
| SEND_STARTED payload `{0:h'04'*32,1:h'07'*32,2:h'08'*32,3:h'09'*32}` | `a40058200404040404040404040404040404040404040404040404040404040404040404015820070707070707070707070707070707070707070707070707070707070707070702582008080808080808080808080808080808080808080808080808080808080808080358200909090909090909090909090909090909090909090909090909090909090909` | `0d735245a7b222a2999a925a4e26bc602cfa86268a63ffd25f3e7528637f408c` |

The `*` notation in the logical labels is expanded to the repeated byte before hashing; the hex columns are the review fixture source of truth. The digest prefix used above is `StrideLab/P2F/v1/event\0`. Policy-bridge and withdrawal vectors remain a required addition before this vector set can be called complete.

Mandatory rejection fixtures: duplicate key 0; unknown key 99; enum 7 for `OutputPurpose`; integer `0x18 0x01` for value 1; indefinite map; tag 0; float 1.0; null; missing required `request_digest`; stale coordinator or gateway epoch; second `SEND_STARTED` for the same output/release.
