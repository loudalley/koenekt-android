"""Synthetic contract for the native Reporter updates screen against the real licence-site ASGI app.

The update service itself is replaced by a recorder, so nothing leaves this process. Needs a People checkout
whose licence site has the Updates routes with ticked PCs (KOENEKT_PEOPLE_REPO, default ../koenekt-people).
The payloads below are exactly the ones UpdatesScreen.java sends.
"""
from pathlib import Path
import importlib.util
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

PROJECT = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get('KOENEKT_PEOPLE_REPO', str(PROJECT.parent / 'koenekt-people')))
SITE = ROOT / 'licence_server'
if not (SITE / 'updates_client.py').exists() or 'device_ids' not in (SITE / 'app.py').read_text(encoding='utf-8'):
    reason = 'Optional integration tests require KOENEKT_PEOPLE_REPO pointing to a People checkout with the ticked-PC Updates routes.'
    if __name__ == '__main__':
        print('Skipped: ' + reason)
        raise SystemExit(0)
    raise unittest.SkipTest(reason)
sys.path[:0] = [str(ROOT / 'console'), str(SITE)]

from fastapi.testclient import TestClient

API = '/licences/api/updates/'
PCS = [
    {'device_id': 'pc-alpha-0001', 'shop': 'Synthetic Bakery', 'hostname': 'BAKERY-TILL', 'ip': '198.51.100.7',
     'enrolled_at': '2026-10-01T08:00:00Z', 'current_version': '1.9.93', 'offered_version': '1.9.94',
     'offered_release': 'reporter-1.9.94', 'phase': 'deferred', 'error': 'outside the maintenance window',
     'updater_version': '1.3.0', 'last_contact': '2026-10-27T05:00:00Z', 'paused': False},
    {'device_id': 'pc-beta-0002', 'shop': 'Synthetic Butchery', 'current_version': '1.9.94', 'phase': 'idle',
     'updater_version': '1.0.9', 'last_contact': '2026-10-27T05:01:00Z', 'paused': True},
]


class NativeUpdatesContract(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        home = Path(temp.name)
        (home / 'keys').mkdir()
        env = patch.dict(os.environ, {
            'KLS_DATA': str(home / 'data'), 'KLS_KEYS': str(home / 'keys'), 'KLS_ADMIN_PASSWORD': 'synthetic-operator-password',
            'KLS_UPDATES_TOKEN_FILE': str(home / 'operator.token'), 'KLS_UPDATES_URL': 'http://127.0.0.1:9',
        })
        env.start()
        self.addCleanup(env.stop)
        for name in ('KOENEKT_MASTER_PASSWORD_HASH', 'KOENEKT_MASTER_TOTP'):
            os.environ.pop(name, None)
        spec = importlib.util.spec_from_file_location('mobile_updates_licence_app', SITE / 'app.py')
        self.site = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.site)
        self.calls = []
        recorder = patch.object(self.site.uc, 'call', side_effect=self.service)
        recorder.start()
        self.addCleanup(recorder.stop)
        self.l = TestClient(self.site.app, base_url='https://admin.koenekt.co.za')
        self.addCleanup(self.l.close)

    def service(self, method, path, body=None):
        """Stands in for the update service: records the call and answers like it."""
        self.calls.append((method, path, body))
        if path == '/v1/devices':
            return {'devices': PCS}
        if path.endswith('/pause'):
            return {'device_id': path.split('/')[3], 'shop': 'Synthetic Bakery', 'paused': body['paused']}
        dry = bool(body.get('dry_run'))
        ids = [d['device_id'] for d in PCS] if body.get('all') else [body['device_id']]
        if path == '/v1/force-update':
            result = 'would_queue' if dry else 'queued'
        else:
            result = 'would_cancel' if dry else 'cancelled'
        return {'devices': [{'device_id': pc, 'shop': 'Synthetic', 'result': result} for pc in ids], 'dry_run': dry}

    def sign_in(self):
        # The app's management sign-in: the same JSON as the website, no browser headers.
        self.assertEqual(self.l.post('/licences/api/login', json={'password': 'synthetic-operator-password', 'code': ''}).status_code, 200)

    def test_every_native_call_needs_the_management_session(self):
        self.assertEqual(self.l.get(API + 'devices').status_code, 401)
        for path, body in (('preview', {'action': 'force', 'device_id': 'pc-alpha-0001'}), ('force', {'device_id': 'pc-alpha-0001'}),
                           ('cancel', {'device_id': 'pc-alpha-0001'}), ('devices/pc-alpha-0001/pause', {'paused': True})):
            self.assertEqual(self.l.post(API + path, json=body).status_code, 401, path)
        self.assertEqual(self.calls, [])

    def test_list_carries_the_pc_name_address_and_install_date(self):
        self.sign_in()
        devices = self.l.get(API + 'devices').json()['devices']
        self.assertEqual([d['device_id'] for d in devices], ['pc-alpha-0001', 'pc-beta-0002'])
        for key in ('hostname', 'ip', 'enrolled_at', 'phase', 'error', 'offered_release'):
            self.assertIn(key, devices[0])

    def test_one_pc_preview_then_request(self):
        self.sign_in()
        r = self.l.post(API + 'preview', json={'action': 'force', 'device_id': 'pc-alpha-0001'})
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(r.json()['devices'][0]['result'], 'would_queue')
        r = self.l.post(API + 'force', json={'device_id': 'pc-alpha-0001'})
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(self.calls, [('POST', '/v1/force-update', {'device_id': 'pc-alpha-0001', 'dry_run': True}),
                                      ('POST', '/v1/force-update', {'device_id': 'pc-alpha-0001'})])

    def test_ticked_pcs_need_the_selected_confirmation(self):
        self.sign_in()
        ticked = ['pc-alpha-0001', 'pc-beta-0002']
        r = self.l.post(API + 'preview', json={'action': 'force', 'device_ids': ticked, 'release_id': 'reporter-1.9.94'})
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual([row['result'] for row in r.json()['devices']], ['would_queue', 'would_queue'])
        self.calls.clear()
        self.assertEqual(self.l.post(API + 'force', json={'device_ids': ticked}).status_code, 400)
        self.assertEqual(self.calls, [])
        r = self.l.post(API + 'force', json={'device_ids': ticked, 'release_id': 'reporter-1.9.94', 'confirm': 'selected'})
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual([c[2] for c in self.calls], [{'device_id': pc, 'release_id': 'reporter-1.9.94'} for pc in ticked])
        r = self.l.post(API + 'cancel', json={'device_ids': ticked, 'confirm': 'selected'})
        self.assertEqual([row['result'] for row in r.json()['devices']], ['cancelled', 'cancelled'])

    def test_every_pc_needs_the_all_confirmation(self):
        self.sign_in()
        self.assertEqual(self.l.post(API + 'preview', json={'action': 'cancel', 'all': True}).status_code, 200)
        self.assertEqual(self.l.post(API + 'cancel', json={'all': True}).status_code, 400)
        r = self.l.post(API + 'force', json={'all': True, 'confirm': 'all'})
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(self.calls[-1], ('POST', '/v1/force-update', {'all': True}))
        activity = [e for e in self.l.get('/licences/api/overview').json()['events'] if e.get('kind', '').startswith('updates.')]
        self.assertTrue(activity, 'operator actions from the app are in the licence activity log')

    def test_pause_and_resume_one_pc(self):
        self.sign_in()
        for paused in (True, False):
            r = self.l.post(API + 'devices/pc-alpha-0001/pause', json={'paused': paused})
            self.assertEqual(r.status_code, 200, r.text)
            self.assertEqual(self.calls[-1], ('POST', '/v1/devices/pc-alpha-0001/pause', {'paused': paused}))


if __name__ == '__main__':
    unittest.main()
