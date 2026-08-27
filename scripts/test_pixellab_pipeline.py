#!/usr/bin/env python3
"""Focused local tests for the PixelLab MCP result pipeline."""

from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from pixellab_pipeline import (  # noqa: E402
    PipelineError,
    adopt_candidate,
    init_request,
    sanitize_request_id,
    save_candidates,
    _scaled_nearest,
)
from pixellab_generate import generate_assets  # noqa: E402


class PixelLabPipelineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.workspace = Path(self.tempdir.name)
        self.root = self.workspace / "pixellab"

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def _fixture(self, name: str, color: tuple[int, int, int, int]) -> Path:
        path = self.workspace / name
        image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        image.putpixel((4, 4), color)
        image.save(path, format="PNG")
        return path

    def _request(self) -> str:
        return init_request(
            self.root,
            "Twin Blades icon; Authorization: Bearer " + "fixture-value",
            normalized_prompt="32x32 transparent item icon",
            request_id="../../Twin Blades/../icon",
            width=32,
            height=32,
            count=2,
        )["request_id"]

    def test_request_id_is_one_safe_path_component(self) -> None:
        value = sanitize_request_id("../../Twin Blades/../icon")
        self.assertEqual(value, "twin-blades-icon")
        self.assertNotIn("/", value)
        self.assertNotIn("..", value)

    def test_request_id_does_not_copy_credential_shaped_request_text(self) -> None:
        output = init_request(self.root, "Authorization: Bearer fixture-value")

        self.assertNotIn("fixture-value", output["request_id"])

    def test_request_metadata_is_local_and_redacted(self) -> None:
        request_id = self._request()
        request_file = self.root / "requests" / f"{request_id}.json"
        stored = request_file.read_text(encoding="utf-8")
        self.assertNotIn("fixture-value", stored)
        self.assertIn("[REDACTED]", stored)
        self.assertTrue((self.root / "results" / request_id / "request.json").is_file())

    def test_fixture_contact_sheet_has_backgrounds_and_actual_size(self) -> None:
        request_id = self._request()
        first = self._fixture("first.png", (255, 0, 0, 255))
        second = self._fixture("second.png", (0, 255, 0, 128))
        output = save_candidates(
            self.root,
            request_id,
            [str(first), str(second)],
            tool_name="native-pixellab-tool",
            seed="42",
        )
        result_dir = Path(output["result_dir"])
        self.assertEqual(output["previews"]["resampling"], "nearest-neighbor")
        self.assertTrue((result_dir / "contact-sheet.png").is_file())
        self.assertTrue((result_dir / "contact-sheet-light.png").is_file())
        self.assertTrue((result_dir / "contact-sheet-dark.png").is_file())
        with Image.open(result_dir / "contact-sheet-light.png") as light_sheet:
            self.assertEqual(light_sheet.convert("RGB").getpixel((0, 0)), (245, 245, 238))
        with Image.open(result_dir / "contact-sheet-dark.png") as dark_sheet:
            self.assertEqual(dark_sheet.convert("RGB").getpixel((0, 0)), (30, 31, 38))
        self.assertTrue((result_dir / "actual-size-preview.png").is_file())
        with Image.open(result_dir / "contact-sheet.png") as sheet:
            self.assertGreater(sheet.width, 0)
            self.assertGreater(sheet.height, 0)
        metadata = json.loads((result_dir / "result.json").read_text(encoding="utf-8"))
        self.assertEqual([item["number"] for item in metadata["candidates"]], [1, 2])
        self.assertTrue(metadata["candidates"][1]["transparent"])
        self.assertEqual(metadata["generation"]["seed"], "42")

    def test_scaled_preview_uses_nearest_neighbor(self) -> None:
        source = Image.new("RGBA", (2, 1))
        source.putpixel((0, 0), (255, 0, 0, 255))
        source.putpixel((1, 0), (0, 0, 255, 255))
        scaled = _scaled_nearest(source, maximum=4)
        self.assertEqual(scaled.size, (4, 2))
        self.assertEqual(scaled.getpixel((1, 0)), (255, 0, 0, 255))
        self.assertEqual(scaled.getpixel((2, 0)), (0, 0, 255, 255))

    def test_adoption_requires_existing_candidate_and_no_implicit_overwrite(self) -> None:
        request_id = self._request()
        candidate = self._fixture("candidate.png", (0, 0, 255, 255))
        save_candidates(self.root, request_id, [str(candidate)])
        target = self.workspace / "resource.png"
        target.write_bytes(b"keep me")

        with self.assertRaisesRegex(PipelineError, "does not exist"):
            adopt_candidate(self.root, request_id, 7, str(self.workspace / "missing.png"), confirm_adopt=True)
        with self.assertRaisesRegex(PipelineError, "explicit overwrite"):
            adopt_candidate(self.root, request_id, 1, str(target), confirm_adopt=True)
        self.assertEqual(target.read_bytes(), b"keep me")
        with self.assertRaisesRegex(PipelineError, "explicit adoption"):
            adopt_candidate(self.root, request_id, 1, str(self.workspace / "new.png"))

        result = adopt_candidate(self.root, request_id, 1, str(target), confirm_adopt=True, overwrite=True)
        self.assertTrue(result["overwritten"])
        self.assertNotEqual(target.read_bytes(), b"keep me")
        metadata = json.loads((self.root / "results" / request_id / "result.json").read_text(encoding="utf-8"))
        self.assertEqual(metadata["adoptions"][0]["candidate"], 1)

    def test_token_file_cannot_be_reference_or_adoption_target(self) -> None:
        with self.assertRaisesRegex(PipelineError, "token file"):
            init_request(self.root, "request", references=["~/.config/projects/pixellab-token"], request_id="safe")

    def test_official_sdk_generation_saves_four_candidates_without_adoption(self) -> None:
        token = self.workspace / "pixellab-token"
        token.write_text("fixture-secret\n", encoding="utf-8")
        token.chmod(0o600)
        calls: list[tuple[str, dict[str, object]]] = []

        class FakeImage:
            def __init__(self, color: tuple[int, int, int, int]) -> None:
                self.color = color

            def pil_image(self) -> Image.Image:
                return Image.new("RGBA", (32, 32), self.color)

        class FakeResponse:
            def __init__(self, color: tuple[int, int, int, int]) -> None:
                self.image = FakeImage(color)

        class FakeClient:
            def generate_image_pixflux(self, **kwargs: object) -> FakeResponse:
                calls.append(("pixflux", kwargs))
                seed = int(kwargs["seed"])
                return FakeResponse((seed % 255, 80, 90, 160))

            def generate_image_bitforge(self, **kwargs: object) -> FakeResponse:
                calls.append(("bitforge", kwargs))
                return FakeResponse((40, 60, 220, 180))

        output = generate_assets(
            root=self.root,
            original_request="HP icon",
            normalized_prompt="clean modern HP stat icon",
            width=32,
            height=32,
            count=4,
            transparent=True,
            token_path=token,
            client_factory=lambda _secret: FakeClient(),
        )

        result_dir = Path(output["result_dir"])
        self.assertEqual(output["candidate_count"], 4)
        self.assertEqual([route for route, _ in calls], ["pixflux"] * 4)
        self.assertTrue(all(call["no_background"] is True for _, call in calls))
        self.assertTrue((result_dir / "contact-sheet.png").is_file())
        metadata = json.loads((result_dir / "result.json").read_text(encoding="utf-8"))
        self.assertEqual(len(metadata["candidates"]), 4)
        self.assertEqual(metadata["adoptions"], [])
        self.assertNotIn("fixture-secret", (result_dir / "result.json").read_text(encoding="utf-8"))

        reference = self._fixture("reference.png", (210, 210, 210, 255))
        reference_output = generate_assets(
            root=self.root,
            original_request="Mana icon",
            normalized_prompt="matching Mana stat icon",
            width=32,
            height=32,
            count=1,
            transparent=True,
            reference=str(reference),
            token_path=token,
            client_factory=lambda _secret: FakeClient(),
        )
        self.assertEqual(calls[-1][0], "bitforge")
        self.assertEqual(calls[-1][1]["style_image"].size, (32, 32))
        self.assertTrue(reference_output["reference_used"])

    def test_official_sdk_rejects_insecure_token_permissions(self) -> None:
        token = self.workspace / "pixellab-token"
        token.write_text("fixture-secret\n", encoding="utf-8")
        token.chmod(0o644)

        with self.assertRaisesRegex(PipelineError, "0600"):
            generate_assets(
                root=self.root,
                original_request="HP icon",
                normalized_prompt="HP icon",
                width=32,
                height=32,
                count=1,
                transparent=True,
                token_path=token,
                client_factory=lambda _secret: object(),
            )

    def test_official_sdk_failure_does_not_persist_remote_error_text(self) -> None:
        token = self.workspace / "pixellab-token"
        token.write_text("fixture-secret\n", encoding="utf-8")
        token.chmod(0o600)

        class FailingClient:
            def generate_image_pixflux(self, **_kwargs: object) -> object:
                raise RuntimeError("remote failure fixture-secret")

        with self.assertRaisesRegex(PipelineError, "request retained"):
            generate_assets(
                root=self.root,
                original_request="HP icon",
                normalized_prompt="HP icon",
                width=32,
                height=32,
                count=1,
                transparent=True,
                token_path=token,
                client_factory=lambda _secret: FailingClient(),
            )

        result_files = list((self.root / "results").glob("*/result.json"))
        self.assertEqual(len(result_files), 1)
        stored = result_files[0].read_text(encoding="utf-8")
        self.assertIn("pixellab_generation_failed", stored)
        self.assertNotIn("remote failure", stored)
        self.assertNotIn("fixture-secret", stored)


if __name__ == "__main__":
    unittest.main(verbosity=2)
