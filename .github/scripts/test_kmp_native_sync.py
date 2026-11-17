import copy
import unittest

from prove_ios_install_epoch_sync import validate_operations


class NativeSyncTraceTest(unittest.TestCase):
    def setUp(self):
        self.simulator = "owned-simulator"
        self.nonce = "run-nonce"
        self.directory = "/Devices/owned-simulator/data/tmp/fresh/WorkeeperInstallation"
        self.candidate = self.directory + "/candidate"
        self.rows = []
        for operation, path, target, inode, directory, size in [
            ("activate", "", "", 0, False, 0),
            ("openat", self.candidate, "", 12, False, 0),
            ("write", self.candidate, "", 12, False, 36),
            ("fsync", self.candidate, "", 12, False, 36),
            ("linkat", self.candidate, self.directory + "/install_epoch", 12, False, 36),
            ("fsync", self.directory, "", 11, True, 0),
            ("unlinkat", self.candidate, "", 12, False, 36),
            ("fsync", self.directory, "", 11, True, 0),
        ]:
            self.rows.append(dict(operation=operation, path=path, target=target, inode=inode,
                                  directory=directory, regular=not directory and inode == 12, size=size, sequence=len(self.rows) + 1,
                                  device=1, result={"write": 36, "openat": 7}.get(operation, 0),
                                  create=operation == "openat", exclusive=operation == "openat",
                                  no_follow=operation == "openat", errno=0, pid=42, nonce=self.nonce))

    def check(self, rows):
        for i, row in enumerate(rows, 1):
            row["sequence"] = i
        return validate_operations(rows, self.nonce, self.simulator)

    def test_completed_real_operation_protocol(self):
        self.assertIn("before publication", self.check(self.rows))

    def test_every_durability_boundary_is_required(self):
        for missing in range(len(self.rows)):
            with self.subTest(missing=missing):
                rows = copy.deepcopy(self.rows)
                del rows[missing]
                with self.assertRaises(ValueError):
                    self.check(rows)

    def test_cleanup_sync_cannot_stand_in_for_publication_sync(self):
        del self.rows[5]
        with self.assertRaisesRegex(ValueError, "publication directory"):
            self.check(self.rows)

    def test_reordered_candidate_and_directory_sync_fail(self):
        for first, second in [(1, 2), (2, 3), (3, 4), (4, 5), (6, 7)]:
            with self.subTest(swap=(first, second)):
                rows = copy.deepcopy(self.rows)
                rows[first], rows[second] = rows[second], rows[first]
                with self.assertRaises(ValueError):
                    self.check(rows)

    def test_wrong_nonce_process_file_or_result_fail(self):
        for index, key, value in [(2, "nonce", "old"), (3, "pid", 43), (3, "inode", 13),
                                  (3, "result", -1), (4, "result", -1), (5, "result", -1),
                                  (6, "result", -1), (7, "inode", 13), (3, "size", 35),
                                  (2, "result", 0), (2, "inode", 14), (1, "result", -1),
                                  (1, "inode", 13), (1, "regular", False), (1, "size", 1)]:
            with self.subTest(index=index, key=key):
                rows = copy.deepcopy(self.rows)
                rows[index][key] = value
                with self.assertRaises(ValueError):
                    self.check(rows)

    def test_writes_through_the_published_alias_cannot_follow_sync(self):
        overwrite = copy.deepcopy(self.rows[2])
        overwrite["path"] = self.directory + "/install_epoch"
        self.rows.insert(6, overwrite)
        with self.assertRaisesRegex(ValueError, "candidate write followed fsync"):
            self.check(self.rows)

    def test_creation_flags_are_individually_required(self):
        for flag in ("create", "exclusive", "no_follow"):
            with self.subTest(flag=flag):
                rows = copy.deepcopy(self.rows)
                rows[1][flag] = False
                with self.assertRaisesRegex(ValueError, "exclusive and symlink-safe"):
                    self.check(rows)

    def test_extra_open_flags_do_not_reject_required_flag_subset(self):
        self.rows[1]["flags"] = 12345
        self.check(self.rows)

    def test_duplicate_successful_candidate_open_is_ambiguous(self):
        self.rows.insert(2, copy.deepcopy(self.rows[1]))
        with self.assertRaisesRegex(ValueError, "ambiguous candidate creation"):
            self.check(self.rows)

    def test_equivalent_candidate_name_and_fixture_root(self):
        for row in self.rows:
            row["path"] = row["path"].replace("/fresh/", "/renamed/").replace("/candidate", "/other-owned-name")
            row["target"] = row["target"].replace("/fresh/", "/renamed/")
        self.check(self.rows)


if __name__ == "__main__":
    unittest.main()
