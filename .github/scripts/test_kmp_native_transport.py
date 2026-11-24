from pathlib import Path
import subprocess
import unittest


class NativeArtifactTransportTest(unittest.TestCase):
    def test_javascript_transport_controls(self):
        result = subprocess.run(['node', '--test', str(Path(__file__).with_suffix('.cjs'))],
                                capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('pass 8', result.stdout)


if __name__ == '__main__':
    unittest.main()
