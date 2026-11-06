"""Deterministic invented-only broker; not encryption, custody or private intake."""
from copy import deepcopy
import threading
from p2f_private_authority import Denied, need


class FakeKey:
    def __init__(self):
        self.events = []
        self.output = []
        self.fault = None

    def step(self, name):
        self.events.append(name)
        need(self.fault != name, name + ' failure')


class Broker:
    def __init__(self, ledger, inventory, key):
        self.ledger, self.inventory, self.key = ledger, inventory, key
        self.closed = True
        self.busy = False
        self.withdrawal = False
        self.enrolled = False
        self.escaped = []
        self.audit = ledger.audit
        self.scope = self.anchor_identity = None
        self._gate = threading.Lock()
        self._state = threading.RLock()
        self.data_versions = {}
        self.restored_head = None
        self._violation = False

    def enroll(self, scope, anchor_identity, obj, version, *, approved=False, time_bound=False):
        need(type(time_bound) is bool, 'invalid time policy')
        need(approved is True and not self.enrolled and self.ledger.head is None and self.ledger.anchor is not None, 'enroll denied')
        need(self.ledger.anchor.identity == anchor_identity and self.ledger.anchor.read() == 0, 'anchor identity')
        self.scope, self.anchor_identity = scope, anchor_identity
        self.inventory.verify(0, scope, obj, version)
        self.ledger.commit(scope, 0, 'active', 'enrollment', time_bound)
        self.data_versions[obj] = version
        self.enrolled, self.closed = True, True

    def attach(self, scope, anchor_identity, data_versions, *, approved):
        need(approved is True and not self.enrolled and self.ledger.head is not None, 'enroll denied')
        need(self.ledger.anchor is not None and self.ledger.anchor.identity == anchor_identity, 'anchor identity')
        self.scope, self.anchor_identity = scope, anchor_identity
        self.data_versions = deepcopy(data_versions)
        self.enrolled, self.closed = True, True

    def current(self, scope):
        need(self.enrolled, 'enroll required')
        need(not self.closed, 'closed')
        need(scope == self.scope and not self.withdrawal, 'scope denied')
        try:
            head = self.ledger.validate(self.anchor_identity)
        except Denied:
            self.closed = True
            raise
        need(head['scope'] == scope and head['state'] == 'active', 'scope denied')
        return head

    def read(self, scope, obj, version, *, hook=None, time_required=False, trusted_time=None, chunks=('published',)):
        need(self._gate.acquire(blocking=False), 'contention')
        self.busy = True
        self._violation = False
        start_output = len(self.key.output)
        try:
            head = deepcopy(self.current(scope))
            self.inventory.verify(head['generation'], scope, obj, version)
            need(self.data_versions.get(obj) == version, 'inventory restored object mismatch')
            need(not (time_required or head['time_bound']) or trusted_time == 'trusted', 'time unavailable')
            for phase in ('authorize', 'release', 'decrypt', 'audit', 'publish'):
                with self._state:
                    need(not self.withdrawal, 'withdrawal pending')
                    need(not self._violation, 'reentrant mutation')
                    need(self.current(scope) == head, 'authority changed')
                    self.inventory.verify(head['generation'], scope, obj, version)
                    self.key.step(phase)
                    if phase == 'audit': self.audit.append({'kind': 'read_allowed', 'generation': head['generation']})
                if hook: hook(phase)
                need(not self._violation, 'reentrant mutation')
                need(not self.withdrawal, 'withdrawal pending')
            for chunk in chunks:
                with self._state:
                    need(not self.withdrawal, 'withdrawal pending')
                    need(self.current(scope) == head, 'authority changed')
                    self.inventory.verify(head['generation'], scope, obj, version)
                    self.key.output.append(chunk)
                if hook: hook('published_chunk')
                need(not self._violation, 'reentrant mutation')
                need(not self.withdrawal, 'withdrawal pending')
            return 'published'
        except BaseException as error:
            with self._state:
                escaped = self.key.output[start_output:]
                if escaped:
                    if not self.withdrawal: self.escaped.extend(escaped)
                    self.audit.append({'kind': 'partial_exposure', 'count': len(escaped)})
                if isinstance(error, Denied) and self.key.fault != 'audit':
                    self.audit.append({'kind': 'denied', 'reason': str(error)})
            raise
        finally:
            self.busy = False
            self._gate.release()

    def receive_withdrawal(self, scope):
        need(scope == self.scope, 'scope denied')
        with self._state:
            self.withdrawal = True
            self.escaped.extend(self.key.output)

    def finish_withdrawal(self, operation):
        need(self.withdrawal and not self.busy, 'withdrawal in flight')
        prior = self.ledger.operations.get(operation)
        expected = prior[1] if prior is not None else self.ledger.anchor.read()
        return self.transition(self.scope, expected, 'revoke', operation, pending=True)

    def transition(self, scope, expected, intent, operation, fault=None, pending=False):
        if self.busy:
            self._violation = True
            raise Denied('reentrant or contention')
        need(self._gate.acquire(blocking=False), 'contention')
        try:
            return self._transition(scope, expected, intent, operation, fault, pending)
        finally:
            self._gate.release()

    def _transition(self, scope, expected, intent, operation, fault, pending):
        need(self.enrolled and scope == self.scope, 'scope denied')
        prior = self.ledger.operations.get(operation)
        if prior:
            need(prior == (scope, expected, intent), 'operation collision')
            need(not self.closed, 'closed')
            head = self.ledger.validate(self.anchor_identity)
            need(self.inventory.generation == head['generation'] and head['operation'] == operation, 'operation not current')
            return 'acknowledged'
        if pending:
            need(self.withdrawal and not self.closed, 'scope denied')
            self.ledger.validate(self.anchor_identity)
        else:
            self.current(scope)
        need(intent in ('revoke','delete'), 'intent denied')
        self.ledger.anchor.verify(expected)
        self.busy = True
        try:
            if fault == 'before_intent': raise Denied('crash before intent')
            self.ledger.intent = (scope, expected, intent, operation)
            self.ledger.issuer.intents[operation] = self.ledger.intent
            for phase in ('intent', 'before_advance'):
                if fault == phase: raise Denied('crash '+phase)
            generation = self.ledger.anchor.advance(expected)
            for phase in ('after_advance', 'readback'):
                if fault == phase: raise Denied('crash '+phase)
            self.ledger.anchor.verify(generation)
            self.ledger.commit(scope, generation, 'suppressed', operation, self.ledger.head['time_bound'])
            if fault == 'ledger': raise Denied('crash ledger')
            self.inventory.suppress(scope, generation)
            if fault == 'inventory': raise Denied('crash inventory')
            if fault == 'audit' or self.key.fault == 'audit': raise Denied('crash audit')
            self.audit.append({'kind': 'suppressed', 'generation': generation})
            self.ledger.operations[operation] = (scope, expected, intent)
            if fault == 'ack': raise Denied('crash ack')
            self.ledger.intent = None
            return 'acknowledged'
        except Exception:
            self.closed = True
            raise
        finally:
            self.busy = False

    def checkpoint(self):
        if not self._gate.acquire(blocking=False):
            self._violation = True
            raise Denied('reentrant or contention')
        try:
            return {'head': deepcopy(self.ledger.head), 'inventory': deepcopy(self.inventory.objects),
                    'inventory_generation': self.inventory.generation, 'data_versions': deepcopy(self.data_versions)}
        finally:
            self._gate.release()

    def restore_closed(self, snapshot):
        if not self._gate.acquire(blocking=False):
            self._violation = True
            raise Denied('reentrant or contention')
        try:
            self.closed = True
            self.restored_head = deepcopy(snapshot['head'])
            self.data_versions = deepcopy(snapshot['data_versions'])
        finally:
            self._gate.release()

    def restart(self):
        with self._state:
            self.closed = True
            if self.busy: self._violation = True

    def reconcile(self, *, channel_clear):
        if not self._gate.acquire(blocking=False):
            self._violation = True
            raise Denied('reentrant or contention')
        try:
            return self._reconcile(channel_clear=channel_clear)
        finally:
            self._gate.release()

    def _reconcile(self, *, channel_clear):
        need(self.enrolled, 'enroll required')
        need(self.closed, 'already open')
        need(channel_clear, 'reconcile withdrawal channel')
        need(self.ledger.anchor is not None and self.ledger.anchor.identity == self.anchor_identity, 'anchor identity')
        intent = self.ledger.intent
        unresolved = set(self.ledger.issuer.intents) - set(self.ledger.operations)
        if intent is None:
            need(not unresolved, 'intent missing')
        else:
            need(isinstance(intent, tuple) and len(intent) == 4 and
                 isinstance(intent[1], int) and isinstance(intent[3], str), 'intent unverified')
            need(unresolved <= {intent[3]}, 'intent ambiguity')
        if intent:
            scope, expected, kind, operation = intent
            need(self.ledger.issuer.available and self.ledger.issuer.intents.get(operation) == intent
                 and kind in ('revoke', 'delete') and scope == self.scope, 'intent unverified')
            current = self.ledger.anchor.read()
            need(current in (expected, expected + 1), 'authority generation mismatch')
            if current == expected:
                current = self.ledger.anchor.advance(expected)
            if self.ledger.head['generation'] != current:
                self.ledger.commit(scope, current, 'suppressed', operation, self.ledger.head['time_bound'])
            self.inventory.suppress(scope, current)
            need(self.key.fault != 'audit', 'audit unavailable')
            if not any(row['kind'] == 'suppressed' and row['generation'] == current for row in self.audit):
                self.audit.append({'kind': 'suppressed', 'generation': current})
            self.ledger.operations[operation] = (scope, expected, kind)
            self.ledger.intent = None
        self.ledger.validate(self.anchor_identity)
        if self.restored_head is not None:
            need(self.restored_head == self.ledger.head, 'authority generation mismatch')
            self.restored_head = None
        need(self.inventory.generation == self.ledger.head['generation'], 'inventory generation mismatch')
        self.closed = False
