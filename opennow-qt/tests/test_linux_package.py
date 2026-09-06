import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "packaging"))
from verify_linux_package import verify_capabilities


class LinuxPackageCapabilitiesTest(unittest.TestCase):
    def setUp(self):
        self.message = {
            "type": "ready",
            "capabilities": {
                "videoBackends": [
                    {
                        "backend": "vaapi",
                        "available": False,
                        "codecs": [
                            {"codec": "h264", "available": False, "reason": "no render node"},
                            {"codec": "h265", "available": False},
                            {"codec": "av1", "available": False},
                        ],
                    },
                    {
                        "backend": "ffmpeg",
                        "codecs": [
                            {"codec": codec, "available": True}
                            for codec in ("h264", "h265", "av1")
                        ],
                    },
                ]
            },
        }

    def test_driverless_runner_can_validate_compiled_vaapi(self):
        verify_capabilities(self.message)

    def test_h264_hardware_can_be_available(self):
        backend = self.message["capabilities"]["videoBackends"][0]
        backend["available"] = True
        backend["codecs"][0] = {"codec": "h264", "available": True}
        verify_capabilities(self.message)

    def test_missing_vaapi_feature_is_rejected(self):
        self.message["capabilities"]["videoBackends"][0]["codecs"][0]["reason"] = (
            "crate was built without the vaapi feature"
        )
        with self.assertRaisesRegex(ValueError, "without native VAAPI"):
            verify_capabilities(self.message)

    def test_hevc_and_av1_native_vaapi_are_rejected(self):
        for index in (1, 2):
            with self.subTest(index=index):
                message = copy.deepcopy(self.message)
                message["capabilities"]["videoBackends"][0]["codecs"][index]["available"] = True
                with self.assertRaisesRegex(ValueError, "HEVC or AV1"):
                    verify_capabilities(message)

    def test_missing_ffmpeg_fallback_is_rejected(self):
        self.message["capabilities"]["videoBackends"][1]["codecs"][0]["available"] = False
        with self.assertRaisesRegex(ValueError, "FFmpeg software fallback"):
            verify_capabilities(self.message)

    def test_non_ready_response_is_rejected(self):
        self.message["type"] = "error"
        with self.assertRaisesRegex(ValueError, "did not return ready"):
            verify_capabilities(self.message)


if __name__ == "__main__":
    unittest.main()
