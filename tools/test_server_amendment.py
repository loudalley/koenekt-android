from pathlib import Path
import importlib.util
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'server-amendment/apply_remembered_sessions.py'
spec = importlib.util.spec_from_file_location('amendment', SCRIPT)
amendment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(amendment)


class AmendmentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Synthetic source fixtures: the public app repo does not need private server history.
        cls.auth = '''def issue_session(adms_home, user, hours=None, *, seconds=None):
    return "synthetic-token"
'''
        cls.original = '''import console_auth as cauth

SESSION_COOKIE_MAX_AGE = 30 * 60

def https_session(request):
    return True

def _set_session_cookie(
    response: JSONResponse, request: Request, token: str, *, role: str | None,
) -> None:
    kwargs = {
        "key": "adms_session",
        "max_age": cookie_max_age_for_role(role),
    }
    response.set_cookie(**kwargs)

async def api_login(request: Request, payload: dict):
    # Synthetic fixture; no users, credentials or live server source.
    user = authenticate_synthetic(payload)
    body = make_response(user)
''' + amendment.OLD_LOGIN + '\n    return body\n'

    def test_surgical_result_and_idempotence(self):
        result, changed = amendment.amend(self.original, self.auth)
        self.assertTrue(changed)
        self.assertIn('payload.get("remember_device") is True and https_session(request)', result)
        self.assertIn('cauth.issue_session(base_dir(), user, seconds=duration)', result)
        self.assertIn(amendment.NEW_AGE, result)
        self.assertEqual(result.count(amendment.MARKER), 1)
        compile(result, '<synthetic amended source>', 'exec')
        self.assertEqual(amendment.amend(result, self.auth), (result, False))

    def test_other_runtime_edits_are_preserved(self):
        extra = '\n# Synthetic unrelated change must survive the amendment.\ndef unrelated_synthetic():\n    return 42\n'
        result, changed = amendment.amend(self.original + extra, self.auth)
        self.assertTrue(changed)
        self.assertTrue(result.endswith(extra))

    def test_unexpected_or_partial_login_source_is_refused(self):
        with self.assertRaises(ValueError): amendment.amend(self.original.replace('def _set_session_cookie(', 'def other_cookie('), self.auth)
        with self.assertRaises(ValueError): amendment.amend(amendment.MARKER + '\n' + self.original, self.auth)

    def test_older_server_helpers_are_refused(self):
        # Each of these would still compile after patching, then fail every People login at runtime.
        older_auth = self.auth.replace(', *, seconds=None', '')
        cases = (
            (self.original, older_auth),
            (self.original, None),
            (self.original, ''),
            (self.original.replace('def https_session(', 'def other_https('), self.auth),
            (self.original.replace('import console_auth as cauth', 'import console_auth'), self.auth),
        )
        for source, auth in cases:
            with self.subTest(auth=auth):
                with self.assertRaisesRegex(ValueError, 'No change made'): amendment.amend(source, auth)

    def test_dry_run_apply_backup_and_repeat_are_safe(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp)/'adms_console.py'
            target.write_text(self.original, encoding='utf-8')
            auth = Path(temp)/'console_auth.py'
            auth.write_text(self.auth.replace(', *, seconds=None', ''), encoding='utf-8')
            command = [sys.executable, str(SCRIPT), '--console', str(target)]
            refused = subprocess.run(command + ['--apply'], capture_output=True, text=True)
            self.assertNotEqual(refused.returncode, 0)
            self.assertIn('does not accept seconds', refused.stderr)
            self.assertEqual(target.read_text(encoding='utf-8'), self.original)
            self.assertEqual(list(Path(temp).glob('*.before-mobile-*')), [])
            auth.write_text(self.auth, encoding='utf-8')
            run = lambda *args: subprocess.run(command + list(args), capture_output=True, text=True, check=True)
            self.assertIn('Dry run passed', run().stdout)
            self.assertEqual(target.read_text(encoding='utf-8'), self.original)
            self.assertIn('Amended only', run('--apply').stdout)
            backups = list(Path(temp).glob('*.before-mobile-*'))
            self.assertEqual(len(backups), 1)
            self.assertEqual(backups[0].read_text(encoding='utf-8'), self.original)
            self.assertIn('Already amended', run('--apply').stdout)
            self.assertEqual(len(list(Path(temp).glob('*.before-mobile-*'))), 1)


if __name__ == '__main__': unittest.main()
