#!/usr/bin/env python3
"""Focused local tests for the PixelLab MCP result pipeline."""

from __future__ import annotations

import base64
from io import BytesIO
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
from pixellab_generate import _official_api_generate, generate_assets  # noqa: E402


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
        repo = self.workspace / "repo"
        repo.mkdir()
        target = repo / "resource.png"
        target.write_bytes(b"keep me")

        with self.assertRaisesRegex(PipelineError, "does not exist"):
            adopt_candidate(self.root, request_id, 7, "missing.png", confirm_adopt=True, repo_root=repo)
        with self.assertRaisesRegex(PipelineError, "explicit overwrite"):
            adopt_candidate(self.root, request_id, 1, str(target), confirm_adopt=True, repo_root=repo)
        self.assertEqual(target.read_bytes(), b"keep me")
        with self.assertRaisesRegex(PipelineError, "explicit adoption"):
            adopt_candidate(self.root, request_id, 1, "new.png", repo_root=repo)

        result = adopt_candidate(
            self.root,
            request_id,
            1,
            str(target),
            confirm_adopt=True,
            overwrite=True,
            repo_root=repo,
        )
        self.assertTrue(result["overwritten"])
        self.assertNotEqual(target.read_bytes(), b"keep me")
        metadata = json.loads((self.root / "results" / request_id / "result.json").read_text(encoding="utf-8"))
        self.assertEqual(metadata["adoptions"][0]["candidate"], 1)

    def test_adoption_rejects_repo_escape_and_symlink_escape(self) -> None:
        request_id = self._request()
        candidate = self._fixture("candidate.png", (0, 0, 255, 255))
        save_candidates(self.root, request_id, [str(candidate)])
        repo = self.workspace / "repo"
        repo.mkdir()
        outside = self.workspace / "outside"
        outside.mkdir()

        with self.assertRaisesRegex(PipelineError, "inside the ProjectS repository"):
            adopt_candidate(
                self.root,
                request_id,
                1,
                str(outside / "direct.png"),
                confirm_adopt=True,
                repo_root=repo,
            )
        link = repo / "linked-outside"
        link.symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(PipelineError, "inside the ProjectS repository"):
            adopt_candidate(
                self.root,
                request_id,
                1,
                str(link / "symlink.png"),
                confirm_adopt=True,
                repo_root=repo,
            )
        self.assertFalse((outside / "direct.png").exists())
        self.assertFalse((outside / "symlink.png").exists())

    def test_token_file_cannot_be_reference_or_adoption_target(self) -> None:
        with self.assertRaisesRegex(PipelineError, "token file"):
            init_request(self.root, "request", references=["~/.config/projects/pixellab-token"], request_id="safe")
        request_id = self._request()
        candidate = self._fixture("candidate.png", (0, 0, 255, 255))
        save_candidates(self.root, request_id, [str(candidate)])
        repo = self.workspace / "repo"
        repo.mkdir()
        with self.assertRaisesRegex(PipelineError, "token file"):
            adopt_candidate(
                self.root,
                request_id,
                1,
                str(repo / "pixellab-token"),
                confirm_adopt=True,
                repo_root=repo,
            )
        self.assertFalse((repo / "pixellab-token").exists())

    def test_official_api_generation_saves_four_candidates_without_adoption(self) -> None:
        token = self.workspace / "pixellab-token"
        token.write_text("fixture-secret\n", encoding="utf-8")
        token.chmod(0o600)
        calls: list[tuple[Path | None, bool]] = []

        def fake_generator(
            _secret: str,
            _prompt: str,
            width: int,
            height: int,
            transparent: bool,
            reference: Path | None,
            seed: int,
        ) -> Image.Image:
            calls.append((reference, transparent))
            return Image.new("RGBA", (width, height), (seed % 255, 80, 90, 160))

        output = generate_assets(
            root=self.root,
            original_request="HP icon",
            normalized_prompt="clean modern HP stat icon",
            width=32,
            height=32,
            count=4,
            transparent=True,
            token_path=token,
            generator=fake_generator,
        )

        result_dir = Path(output["result_dir"])
        self.assertEqual(output["candidate_count"], 4)
        self.assertEqual([reference for reference, _ in calls], [None] * 4)
        self.assertTrue(all(transparent for _, transparent in calls))
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
            generator=fake_generator,
        )
        self.assertEqual(calls[-1][0], reference.resolve())
        self.assertTrue(reference_output["reference_used"])

    def test_official_api_rejects_insecure_token_permissions(self) -> None:
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
                generator=lambda *_args: Image.new("RGBA", (32, 32)),
            )

    def test_generation_rejects_secret_in_request_fields_before_metadata(self) -> None:
        token = self.workspace / "pixellab-token"
        secret = "fixture-secret-value"
        token.write_text(f"{secret}\n", encoding="utf-8")
        token.chmod(0o600)
        cases = (
            {"original_request": f"HP icon {secret}", "normalized_prompt": "HP icon", "reference": None},
            {"original_request": "HP icon", "normalized_prompt": f"HP icon {secret}", "reference": None},
            {"original_request": "HP icon", "normalized_prompt": "HP icon", "reference": f"/tmp/{secret}.png"},
        )

        for index, values in enumerate(cases):
            root = self.workspace / f"secret-case-{index}"
            with self.subTest(field=index), self.assertRaisesRegex(PipelineError, "protected credential material") as error:
                generate_assets(
                    root=root,
                    width=32,
                    height=32,
                    count=1,
                    transparent=True,
                    token_path=token,
                    generator=lambda *_args: Image.new("RGBA", (32, 32)),
                    **values,
                )
            self.assertNotIn(secret, str(error.exception))
            self.assertFalse((root / "requests").exists())
            self.assertFalse((root / "results").exists())

    def test_text_only_pixflux_rejects_dimensions_below_32(self) -> None:
        token = self.workspace / "pixellab-token"
        token.write_text("fixture-secret\n", encoding="utf-8")
        token.chmod(0o600)

        for width, height in ((31, 32), (32, 31), (16, 16)):
            with self.subTest(width=width, height=height), self.assertRaisesRegex(PipelineError, "32px master"):
                generate_assets(
                    root=self.workspace / f"small-{width}-{height}",
                    original_request="small icon",
                    normalized_prompt="small icon",
                    width=width,
                    height=height,
                    count=1,
                    transparent=True,
                    token_path=token,
                    generator=lambda *_args: Image.new("RGBA", (width, height)),
                )

    def test_official_api_failure_does_not_persist_remote_error_text(self) -> None:
        token = self.workspace / "pixellab-token"
        token.write_text("fixture-secret\n", encoding="utf-8")
        token.chmod(0o600)

        def failing_generator(*_args: object) -> Image.Image:
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
                generator=failing_generator,
            )

        result_files = list((self.root / "results").glob("*/result.json"))
        self.assertEqual(len(result_files), 1)
        stored = result_files[0].read_text(encoding="utf-8")
        self.assertIn("pixellab_generation_failed", stored)
        self.assertNotIn("remote failure", stored)
        self.assertNotIn("fixture-secret", stored)

    def test_official_api_decodes_image_without_exposing_authorization(self) -> None:
        buffer = BytesIO()
        Image.new("RGBA", (32, 32), (20, 40, 60, 128)).save(buffer, format="PNG")
        body = json.dumps(
            {"image": {"type": "base64", "base64": base64.b64encode(buffer.getvalue()).decode("ascii")}},
        ).encode("utf-8")
        captured: dict[str, object] = {}

        class FakeHttpResponse:
            def __enter__(self) -> "FakeHttpResponse":
                return self

            def __exit__(self, *_args: object) -> None:
                return None

            def read(self) -> bytes:
                return body

        def fake_opener(request: object, timeout: int) -> FakeHttpResponse:
            captured["url"] = request.full_url
            captured["timeout"] = timeout
            captured["payload"] = json.loads(request.data.decode("utf-8"))
            return FakeHttpResponse()

        image = _official_api_generate(
            "fixture-secret",
            "HP icon",
            32,
            32,
            True,
            None,
            7,
            opener=fake_opener,
        )

        self.assertEqual(image.size, (32, 32))
        self.assertEqual(captured["url"], "https://api.pixellab.ai/v2/create-image-pixflux")
        self.assertEqual(captured["payload"]["no_background"], True)
        self.assertNotIn("fixture-secret", json.dumps(captured["payload"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
