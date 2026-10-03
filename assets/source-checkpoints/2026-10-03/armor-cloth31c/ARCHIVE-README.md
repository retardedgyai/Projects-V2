# armor-cloth31c

Status: **ART_NOT_MET_EDITABLE_CLOTH31C_REPRESENTATIVE_CHECKPOINT**. This is a source recovery checkpoint, not a quality approval.

Open cloth-supported-31/model/model.bbmodel directly: four textures are embedded; OBJ/MTL and PNG sources are also present. Optional source replay: python cloth-supported-31/build31.py, then python cloth-supported-31/verify31.py --portable. The fixed manifest includes baseline30, replay-inputs.json and mesh/check helpers with the original relative topology. Run builders only in a copied working folder. Existing approved/original source and cloth29/30 snapshots are preserved. This representative revision is ART NOT MET; no game-wear or material/aesthetic equivalence approval is inferred.

All selected members were verified against fixed hashes before and after copying; originals and earlier snapshots were preserved. Consult source-snapshot.json and the original handoff for precise limits. Follow-up production revisions are excluded. No original reference works, secrets, world userdata, tool installs or large frame caches were added. No main merge, force push or deployment is part of this checkpoint.

Independent recovery: in a fresh temporary copy with original-producer/original-archive reads rejected, verify31.py --portable passed on the saved model (seven frozen inputs unchanged, four embedded/exported textures equal, 56,662 UV samples with zero used transparent texels). build31.py then regenerated all eight model/texture output files byte-exactly. No original files or native/game runtime were touched. This is bounded neutral technical verification, not aesthetic approval. Evidence: recovery-verification.json.
