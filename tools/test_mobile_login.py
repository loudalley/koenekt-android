"""Synthetic mobile sign-in contract against both real ASGI services; no live servers."""
from pathlib import Path
import importlib.util
import json
import os
import sys
import tempfile
import time
import unittest
from datetime import datetime, timedelta, timezone
from http.cookies import SimpleCookie
from unittest.mock import patch

PROJECT = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get('KOENEKT_PEOPLE_REPO', str(PROJECT.parent / 'koenekt-people')))
if not (ROOT / 'console/adms_console.py').exists():
    if __name__ == '__main__':
        print('Skipped: optional integration tests require KOENEKT_PEOPLE_REPO pointing to the separate People source checkout.')
        raise SystemExit(0)
    raise unittest.SkipTest('Optional integration tests require KOENEKT_PEOPLE_REPO pointing to the separate People source checkout.')
sys.path[:0] = [str(ROOT / 'console'), str(ROOT / 'licence_server')]

# Disable network/background integrations before importing the server.
os.environ['ADMS_LOCAL_LEDGER'] = '0'
os.environ['ADMS_LICENCE_CHECKIN'] = '0'
os.environ['ADMS_TAX_TABLE_CHECK'] = '0'

from fastapi.testclient import TestClient
import console_auth as auth
import adms_console as people


class MobileLoginContract(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        home = Path(temp.name)
        self.home = home
        self.secret = auth.new_totp_secret()
        self.password = 'synthetic-mobile-master-password'
        env = patch.dict(os.environ, {
            'ADMS_HOME': str(home / 'people'), 'ADMS_ADMIN_TOKEN': '',
            'KLS_DATA': str(home / 'licences'), 'KLS_KEYS': str(home / 'keys'),
            'KOENEKT_MASTER_PASSWORD_HASH': auth.hash_password(self.password),
            'KOENEKT_MASTER_TOTP': self.secret,
        })
        env.start()
        self.addCleanup(env.stop)
        (home / 'people').mkdir()
        (home / 'people' / 'devices.json').write_text('{}')
        (home / 'people' / 'people_sor.json').write_text(json.dumps({'mode': 'local'}))
        people.init_db()
        auth.init_users(people.base_dir())
        people._login_failures.clear()
        people._master_last_counter['value'] = -1
        spec = importlib.util.spec_from_file_location('mobile_licence_app', ROOT / 'licence_server' / 'app.py')
        self.licence = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.licence)
        self.p = TestClient(people.app, base_url='https://people.koenekt.co.za', client=('203.0.113.17', 50000))
        self.l = TestClient(self.licence.app, base_url='https://admin.koenekt.co.za', client=('203.0.113.17', 50000))
        self.addCleanup(self.p.close)
        self.addCleanup(self.l.close)

    def test_one_form_code_authenticates_both_independent_services(self):
        code = auth.totp_code(self.secret, int(time.time() // 30))
        payload = {'username': 'master', 'password': self.password, 'code': code}
        self.assertEqual(self.l.post('/licences/api/login', json=payload).status_code, 200)
        self.assertEqual(self.p.post('/api/login', json=payload).status_code, 200)
        self.assertEqual(self.p.get('/api/me').json()['role'], 'master')
        self.assertEqual(self.l.get('/licences/api/overview').status_code, 200)
        # Preserve each service's OTP replay protection.
        self.assertEqual(self.l.post('/licences/api/login', json=payload).status_code, 401)
        self.assertEqual(self.p.post('/api/login', json=payload).status_code, 401)

    def test_client_session_cannot_authorise_operator_actions(self):
        auth.register_user(people.base_dir(), 'customer', 'synthetic-customer-password', 'Synthetic customer')
        auth.set_caps(people.base_dir(), 'customer', ['people_read', 'people_hire', 'people_leave', 'reports'])
        result = self.p.post('/api/login', json={'username': 'customer', 'password': 'synthetic-customer-password'})
        self.assertEqual(result.status_code, 200)
        self.assertEqual(set(self.p.get('/api/me').json()['caps']), {'people_read', 'people_hire', 'people_leave', 'reports'})
        self.assertEqual(self.p.get('/api/admin/users').status_code, 403)
        self.assertEqual(self.l.get('/licences/api/overview').status_code, 401)
        self.assertEqual(self.l.get('/licences/api/updates/devices').status_code, 401)
        self.assertEqual(self.l.post('/licences/api/updates/force', json={'all': True}).status_code, 401)

    def test_native_edit_and_leaver_payloads(self):
        """The app's EditPersonFlow and PeopleScreen.leave() bodies, built from a directory row."""
        import employee_master as em
        import people_extras
        url = 'https://synthetic-bakery.example'
        (self.home / 'people' / 'devices.json').write_text(json.dumps({'SYN1': {'company': 'Synthetic Bakery', 'frappe_url': url}}))
        company = em.ensure_company(name='Synthetic Bakery', frappe_url=url)
        em.ensure_department(company['id'], 'Kitchen')
        hired = em.upsert_employee({'company_id': company['id'], 'device_pin': '41', 'display_name': 'Sam Example',
                                    'department': 'Kitchen', 'status': 'Active', 'hired_on': '2026-02-01', 'privilege': 14})
        people_extras.set_extras(people.base_dir(), hired['name'], {'id_number': 'A1234567'})
        for name, caps in (('editor', ['people_read', 'people_edit']), ('leaver', ['people_read', 'people_leave'])):
            auth.register_user(people.base_dir(), name, 'synthetic-password-123', name)
            auth.set_caps(people.base_dir(), name, caps)
            auth.set_allowed_sites(people.base_dir(), name, [url], actor_is_master=True)

        def login(name):
            self.p.cookies.clear()
            self.assertEqual(self.p.post('/api/login', json={'username': name, 'password': 'synthetic-password-123'}).status_code, 200)
            rows = self.p.get('/api/people/directory', params={'url': url, 'status': 'all'}).json()['employees']
            return next(r for r in rows if r['pin'] == '41')

        row = login('editor')
        edit = {'frappe_url': url, 'company': 'Synthetic Bakery', 'employee': row['name'], 'pin': row['pin'],
                'first_name': 'Sam', 'last_name': 'Renamed', 'department': '', 'job_title': 'Baker',
                'hire_date': '2026-03-01', 'status': row['status'], 'privilege': row.get('privilege', 0),
                'card_no': row.get('card_no', ''), 'push_device': False, 'push_all_company': True}
        result = self.p.post('/api/employees', json=edit)
        self.assertEqual(result.status_code, 200, result.text)
        self.assertFalse(result.json()['created'])
        self.assertEqual(self.p.post('/api/employees', json={**edit, 'pin': '42'}).status_code, 400)   # PINs are permanent
        leave = {'frappe_url': url, 'company': 'Synthetic Bakery', 'employee': row['name'], 'pin': row['pin']}
        self.assertEqual(self.p.post('/api/employees/deactivate', json=leave).status_code, 403)
        row = login('editor')
        self.assertEqual((row['employee_name'], row['job_title'], row['hire_date'], row['department'], row['privilege']),
                         ('Sam Renamed', 'Baker', '2026-03-01', '', 14))
        self.assertEqual(people_extras.get_extras(people.base_dir(), row['name']).get('id_number'), 'A1234567')

        login('leaver')
        result = self.p.post('/api/employees/deactivate', json=leave)
        self.assertEqual(result.status_code, 200, result.text)
        self.assertEqual(len(result.json()['devices']), 1)   # removal queued for the company's clock
        row = login('leaver')
        self.assertEqual(row['status'], 'Inactive')
        self.assertTrue(row['left_on'])

    def register_customer(self):
        auth.register_user(people.base_dir(), 'remembered', 'synthetic-password-123', 'Remembered customer')
        return {'username': 'remembered', 'password': 'synthetic-password-123'}

    def test_remembered_https_session_survives_two_hours_but_expires_after_thirty_days(self):
        payload = self.register_customer()
        result = self.p.post('/api/login', json={**payload, 'remember_device': True})
        self.assertEqual(result.status_code, 200)
        cookie = SimpleCookie(result.headers['set-cookie'])['adms_session']
        self.assertEqual(cookie['max-age'], str(30*24*3600))
        expiry = json.loads(cookie.value.rsplit('.', 1)[0])['e']
        self.assertAlmostEqual(expiry-time.time(), 30*24*3600, delta=3)
        class Later(datetime):
            offset = timedelta(hours=2)
            @classmethod
            def now(cls, tz=None): return datetime.now(tz) + cls.offset
        with patch.object(auth, 'datetime', Later):
            self.assertEqual(self.p.get('/api/me').status_code, 200)
            Later.offset = timedelta(days=31)
            self.assertEqual(self.p.get('/api/me').status_code, 401)

    def test_remembering_does_not_bypass_live_user_disabling(self):
        payload = self.register_customer()
        self.assertEqual(self.p.post('/api/login', json={**payload, 'remember_device': True}).status_code, 200)
        auth.set_status(people.base_dir(), 'remembered', 'disabled')
        self.assertEqual(self.p.get('/api/me').status_code, 401)

    def test_default_false_string_and_http_keep_the_web_session_lifetime(self):
        payload = self.register_customer()
        for opt in ({}, {'remember_device':False}, {'remember_device':'true'}, {'remember_device':1}):
            result = self.p.post('/api/login', json={**payload, **opt})
            self.assertEqual(SimpleCookie(result.headers['set-cookie'])['adms_session']['max-age'], '1800')
        with TestClient(people.app, base_url='http://people.koenekt.co.za', client=('203.0.113.17',50000)) as plain:
            result = plain.post('/api/login', json={**payload, 'remember_device':True})
            self.assertEqual(SimpleCookie(result.headers['set-cookie'])['adms_session']['max-age'], '1800')

    def test_remembered_master_still_requires_password_and_authenticator(self):
        payload = {'username':'master', 'password':self.password, 'remember_device':True}
        self.assertEqual(self.p.post('/api/login', json=payload).status_code, 401)
        payload['code'] = auth.totp_code(self.secret, int(time.time() // 30))
        result = self.p.post('/api/login', json=payload)
        self.assertEqual(result.status_code, 200)
        self.assertEqual(SimpleCookie(result.headers['set-cookie'])['adms_session']['max-age'], str(30*24*3600))
        self.assertEqual(self.p.post('/api/login', json=payload).status_code, 401)


if __name__ == '__main__': unittest.main()
