"""Deterministic invented-only broker; not encryption, custody or private intake."""
from copy import deepcopy
from collections.abc import MutableMapping
import threading
from p2f_private_authority import AuditLog, Denied, Inventory, Ledger, StateCoordinator, need, owned


class FakeKey:
    def __init__(self):
        self._events = []
        self._output = []
        self._fault = None
        self._coordinator = None
        self._owner = None

    @property
    def output(self): return list(self._output)

    @property
    def events(self): return list(self._events)

    @property
    def fault(self): return self._fault

    def set_fault(self, fault):
        def perform():
            value = owned(fault)
            need(value is None or type(value) is str, 'invalid key fault')
            self._fault = value
        self._coordinator.mutate(perform)

    def step(self, name):
        list.append(self._events, name)
        need(self.fault != name, name + ' failure')


class Versions(MutableMapping):
    def __init__(self, coordinator, values=()):
        self._coordinator = coordinator
        self._values = owned(dict(values))

    def __getitem__(self, key):
        with self._coordinator.lock:
            return deepcopy(self._values[key])

    def __setitem__(self, key, value):
        self._coordinator.mutate(lambda: self._values.__setitem__(owned(key), owned(value)))

    def __delitem__(self, key):
        self._coordinator.mutate(lambda: self._values.__delitem__(key))

    def __iter__(self):
        with self._coordinator.lock:
            return iter(tuple(deepcopy(self._values)))

    def __len__(self):
        with self._coordinator.lock:
            return len(self._values)

    def snapshot(self):
        with self._coordinator.lock:
            return deepcopy(self._values)

    def replace(self, values):
        def assign():
            self._values = owned(dict(values))
        self._coordinator.mutate(assign)

    def __deepcopy__(self, memo):
        with self._coordinator.lock:
            return deepcopy(self._values, memo)


class Broker:
    def __init__(self, ledger, inventory, key):
        need(type(self) is Broker, 'unsupported synthetic broker')
        need(type(key) is FakeKey, 'unsupported synthetic key')
        need(type(ledger) is Ledger and type(inventory) is Inventory, 'unsupported synthetic state')
        self._ledger, self._inventory, self._key = ledger, inventory, key
        self._closed = True
        self.busy = False
        self._withdrawal = False
        self._enrolled = False
        self._escaped = []
        self._scope = self._anchor_identity = None
        self._gate = threading.Lock()
        self._coordinator = ledger.coordinator
        need(type(self.coordinator) is StateCoordinator, 'unsupported coordinator')
        need(type(ledger.audit) is AuditLog, 'unsupported synthetic audit')
        self._audit = ledger.audit
        with self.coordinator.lock:
            need(inventory.coordinator in (None, self.coordinator), 'mixed coordinator')
            need(key._coordinator in (None, self.coordinator), 'mixed coordinator')
            need(key._owner is None or (key._owner.closed and not key._owner._gate.locked()), 'key in use')
            inventory._coordinator = self.coordinator
            key._coordinator = self.coordinator
            self._key_start = len(key._output)
            self._key_end = None
            def transfer():
                if key._owner is not None: key._owner._key_end = self._key_start
                key._owner = self
            self.coordinator.mutate(transfer)
        self._state = self.coordinator.lock
        self._data_versions = Versions(self.coordinator)
        self._restored_head = None
        self._violation = False
        self._read_owner = None
        self._escaped_indices = set()
        self.coordinator.mutate(lambda: self.coordinator._brokers.append(self))

    @property
    def coordinator(self): return self._coordinator

    @property
    def key(self): return self._key

    @property
    def escaped(self): return list(self._escaped)

    @property
    def closed(self): return self._closed

    @property
    def withdrawal(self): return self._withdrawal

    @property
    def enrolled(self): return self._enrolled

    @property
    def scope(self): return deepcopy(self._scope)

    @property
    def anchor_identity(self): return deepcopy(self._anchor_identity)

    @property
    def restored_head(self): return deepcopy(self._restored_head)

    def _change(self, name, value):
        self.coordinator.mutate(lambda: setattr(self, name, value))

    @property
    def ledger(self): return self._ledger

    @property
    def audit(self): return self._audit

    @property
    def inventory(self): return self._inventory

    @property
    def data_versions(self): return self._data_versions

    def enroll(self, scope, anchor_identity, obj, version, *, approved=False, time_bound=False):
        need(type(time_bound) is bool, 'invalid time policy')
        need(approved is True and not self.enrolled and self.ledger.head is None and self.ledger.anchor is not None, 'enroll denied')
        need(self.ledger.anchor.identity == anchor_identity and self.ledger.anchor.read() == 0, 'anchor identity')
        self._change('_scope', owned(scope)); self._change('_anchor_identity', owned(anchor_identity))
        self.inventory.verify(0, scope, obj, version)
        self.ledger.commit(scope, 0, 'active', 'enrollment', time_bound)
        self.data_versions[obj] = version
        self._change('_enrolled', True); self._change('_closed', True)

    def attach(self, scope, anchor_identity, data_versions, *, approved):
        need(approved is True and not self.enrolled and self.ledger.head is not None, 'enroll denied')
        need(self.ledger.anchor is not None and self.ledger.anchor.identity == anchor_identity, 'anchor identity')
        self._change('_scope', owned(scope)); self._change('_anchor_identity', owned(anchor_identity))
        self.data_versions.replace(data_versions)
        self._change('_enrolled', True); self._change('_closed', True)

    def current(self, scope):
        with self._state:
            token = self.coordinator.epoch
            need(self.enrolled, 'enroll required')
            need(not self.closed, 'closed')
            need(self.key._owner is self, 'key ownership changed')
            need(scope == self.scope and not self.withdrawal and not self.coordinator.withdrawn(self._scope), 'scope denied')
            try:
                head = self.ledger.validate(self.anchor_identity)
            except Denied:
                self._change('_closed', True)
                raise
            need(head['scope'] == scope and head['state'] == 'active', 'scope denied')
            need(self.coordinator.epoch == token, 'state epoch changed')
            return head

    def read(self, scope, obj, version, *, hook=None, time_required=False, trusted_time=None, chunks=('published',)):
        if not self._gate.acquire(blocking=False):
            if self._read_owner == threading.get_ident(): self._change('_violation', True)
            raise Denied('contention')
        self._read_owner = threading.get_ident()
        self.busy = True
        self._change('_violation', False)
        start_output = len(self.key.output)
        try:
            with self._state:
                token = self.coordinator.epoch
                head = deepcopy(self.current(scope))
                self.inventory.verify(head['generation'], scope, obj, version)
                need(self.data_versions.get(obj) == version, 'inventory restored object mismatch')
                need(not (time_required or head['time_bound']) or trusted_time == 'trusted', 'time unavailable')
                need(self.coordinator.epoch == token, 'state epoch changed')
            for phase in ('authorize', 'release', 'decrypt', 'audit', 'publish'):
                with self._state:
                    need(not self.withdrawal, 'withdrawal pending')
                    need(not self._violation, 'reentrant mutation')
                    need(self.coordinator.epoch == token, 'state epoch changed')
                    need(self.current(scope) == head, 'authority changed')
                    self.inventory.verify(head['generation'], scope, obj, version)
                    need(self.data_versions.get(obj) == version, 'inventory restored object mismatch')
                    need(self.coordinator.epoch == token, 'state epoch changed')
                    FakeKey.step(self._key, phase)
                    if phase == 'audit':
                        AuditLog.append(self._audit, {'kind': 'read_allowed', 'generation': head['generation']})
                        need(self.coordinator.epoch == token + 1, 'state epoch changed')
                        token += 1
                if hook: hook(phase)
                need(not self._violation, 'reentrant mutation')
                need(not self.withdrawal, 'withdrawal pending')
            for chunk in chunks:
                with self._state:
                    need(not self.withdrawal, 'withdrawal pending')
                    need(not self._violation, 'reentrant mutation')
                    need(self.coordinator.epoch == token, 'state epoch changed')
                    need(self.current(scope) == head, 'authority changed')
                    self.inventory.verify(head['generation'], scope, obj, version)
                    need(self.data_versions.get(obj) == version, 'inventory restored object mismatch')
                    need(not self.withdrawal, 'withdrawal pending')
                    need(not self._violation, 'reentrant mutation')
                    state, sink = self._coordinator, self._key._output
                    need(type(sink) is list, 'unsupported synthetic output')
                    need(state._epoch == token, 'state epoch changed')
                    list.append(sink, chunk)
                if hook: hook('published_chunk')
                need(not self._violation, 'reentrant mutation')
                need(not self.withdrawal, 'withdrawal pending')
            return 'published'
        except BaseException as error:
            with self._state:
                escaped = self.key.output[start_output:]
                if escaped:
                    self._record_escaped(start_output)
                    self.audit.append({'kind': 'partial_exposure', 'count': len(escaped)})
                if isinstance(error, Denied) and self.key.fault != 'audit':
                    self.audit.append({'kind': 'denied', 'reason': str(error)})
            raise
        finally:
            self.busy = False
            self._read_owner = None
            self._gate.release()

    def _record_escaped(self, start):
        end = len(self.key.output) if self._key_end is None else self._key_end
        self._escaped_indices.update(range(max(start, self._key_start), end))
        self._escaped[:] = [self.key.output[i] for i in sorted(self._escaped_indices)]

    def receive_withdrawal(self, scope):
        need(scope == self.scope, 'scope denied')
        with self._state:
            self._change('_withdrawal', True)
            self.coordinator._receive_withdrawal(self._scope)
            for broker in self.coordinator._brokers:
                if broker._scope == self._scope:
                    broker._record_escaped(0)

    def finish_withdrawal(self, operation):
        need(self.withdrawal and not self.busy, 'withdrawal in flight')
        prior = self.ledger.operations.get(operation)
        expected = prior[1] if prior is not None else self.ledger.anchor.read()
        return self.transition(self.scope, expected, 'revoke', operation, pending=True)

    def transition(self, scope, expected, intent, operation, fault=None, pending=False):
        if self.busy:
            if self._read_owner == threading.get_ident(): self._change('_violation', True)
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
            self.ledger.set_intent((scope, expected, intent, operation))
            self.ledger.issuer.set_intent(operation, self.ledger.intent)
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
            self.ledger.set_operation(operation, (scope, expected, intent))
            if fault == 'ack': raise Denied('crash ack')
            self.ledger.set_intent(None)
            return 'acknowledged'
        except BaseException:
            self._change('_closed', True)
            raise
        finally:
            self.busy = False

    def checkpoint(self):
        if not self._gate.acquire(blocking=False):
            if self._read_owner == threading.get_ident(): self._change('_violation', True)
            raise Denied('reentrant or contention')
        try:
            return {'head': deepcopy(self.ledger.head), 'inventory': deepcopy(self.inventory.objects),
                    'inventory_generation': self.inventory.generation, 'data_versions': deepcopy(self.data_versions)}
        finally:
            self._gate.release()

    def restore_closed(self, snapshot):
        if not self._gate.acquire(blocking=False):
            if self._read_owner == threading.get_ident(): self._change('_violation', True)
            raise Denied('reentrant or contention')
        try:
            self._change('_closed', True)
            self._change('_restored_head', owned(snapshot['head']))
            self.data_versions.replace(snapshot['data_versions'])
        finally:
            self._gate.release()

    def restart(self):
        with self._state:
            self._change('_closed', True)
            if self.busy: self._change('_violation', True)

    def reconcile(self, *, channel_clear):
        if not self._gate.acquire(blocking=False):
            if self._read_owner == threading.get_ident(): self._change('_violation', True)
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
            self.ledger.set_operation(operation, (scope, expected, kind))
            self.ledger.set_intent(None)
        self.ledger.validate(self.anchor_identity)
        if self.restored_head is not None:
            need(self.restored_head == self.ledger.head, 'authority generation mismatch')
            self._change('_restored_head', None)
        need(self.inventory.generation == self.ledger.head['generation'], 'inventory generation mismatch')
        self._change('_closed', False)
