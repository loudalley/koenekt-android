from pathlib import Path
import importlib.util
import subprocess
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
        # Synthetic source fixture: the public app repo does not need private server history.
        cls.original = '''SESSION_COOKIE_MAX_AGE = 30 * 60

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
        result, changed = amendment.amend(self.original)
        self.assertTrue(changed)
        self.assertIn('payload.get("remember_device") is True and https_session(request)', result)
        self.assertIn('cauth.issue_session(base_dir(), user, seconds=duration)', result)
        self.assertIn(amendment.NEW_AGE, result)
        self.assertEqual(result.count(amendment.MARKER), 1)
        compile(result, '<synthetic amended source>', 'exec')
        self.assertEqual(amendment.amend(result), (result, False))

    def test_other_runtime_edits_are_preserved(self):
        extra = '\n# Synthetic unrelated change must survive the amendment.\ndef unrelated_synthetic():\n    return 42\n'
        result, changed = amendment.amend(self.original + extra)
        self.assertTrue(changed)
        self.assertTrue(result.endswith(extra))

    def test_unexpected_or_partial_login_source_is_refused(self):
        with self.assertRaises(ValueError): amendment.amend(self.original.replace('def _set_session_cookie(', 'def other_cookie('))
        with self.assertRaises(ValueError): amendment.amend(amendment.MARKER+'\n'+self.original)

    def test_dry_run_apply_backup_and_repeat_are_safe(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp)/'adms_console.py'
            target.write_text(self.original, encoding='utf-8')
            import sys
            run = lambda *args: subprocess.run([sys.executable, str(SCRIPT), '--console', str(target), *args], capture_output=True, text=True, check=True)
            self.assertIn('Dry run passed', run().stdout)
            self.assertEqual(target.read_text(encoding='utf-8'), self.original)
            self.assertIn('Amended only', run('--apply').stdout)
            backups = list(Path(temp).glob('*.before-mobile-*'))
            self.assertEqual(len(backups), 1)
            self.assertEqual(backups[0].read_text(encoding='utf-8'), self.original)
            self.assertIn('Already amended', run('--apply').stdout)
            self.assertEqual(len(list(Path(temp).glob('*.before-mobile-*'))), 1)


if __name__ == '__main__': unittest.main()
