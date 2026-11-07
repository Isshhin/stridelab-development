"""Invented-only authority state machine; no cryptographic or TPM guarantees."""
from copy import deepcopy
import threading


class Denied(ValueError):
    pass


def need(ok, reason):
    if not ok:
        raise Denied(reason)


def owned(value):
    """Copy supported synthetic state without retaining custom mutable aliases."""
    kind = type(value)
    if kind in (str, int, bool, float, bytes, type(None)):
        return value
    if isinstance(value, str): return str.__str__(value)
    if isinstance(value, int): return int.__int__(value)
    if kind is tuple: return tuple(owned(item) for item in value)
    if kind is list: return [owned(item) for item in value]
    if kind is dict: return {owned(key): owned(item) for key, item in value.items()}
    raise Denied('unsupported synthetic state value')


class StateCoordinator:
    """In-process ordering for synthetic mutation attempts."""
    def __init__(self):
        self._lock = threading.RLock()
        self._epoch = 0
        self._withdrawn = []
        self._brokers = []

    @property
    def lock(self): return self._lock

    @property
    def epoch(self):
        with self.lock:
            return self._epoch

    def mutate(self, action):
        with self.lock:
            self._epoch += 1
            return action()

    def _receive_withdrawal(self, scope):
        def perform():
            item = owned(scope)
            if item not in self._withdrawn:
                self._withdrawn.append(item)
        self.mutate(perform)

    def withdrawn(self, scope):
        with self.lock:
            return owned(scope) in self._withdrawn


class Anchor:
    def __init__(self, identity, *, coordinator=None):
        self._coordinator = coordinator or StateCoordinator()
        self._identity = owned(identity)
        self._generation = 0
        self._fault = None

    @property
    def coordinator(self): return self._coordinator

    @property
    def identity(self): return deepcopy(self._identity)

    @property
    def generation(self): return deepcopy(self._generation)

    @property
    def fault(self): return deepcopy(self._fault)

    def set_fault(self, fault):
        self.coordinator.mutate(lambda: setattr(self, '_fault', owned(fault)))

    def set_identity(self, identity):
        self.coordinator.mutate(lambda: setattr(self, '_identity', owned(identity)))

    def set_generation(self, generation):
        self.coordinator.mutate(lambda: setattr(self, '_generation', owned(generation)))

    def read(self):
        with self.coordinator.lock:
            need(self.fault not in ('missing', 'unavailable', 'corrupt', 'wrong_identity'), 'anchor unavailable or invalid')
            return self.generation

    def verify(self, expected):
        need(self.read() == expected, 'anchor generation mismatch')
        return True

    def advance(self, expected):
        def perform():
            self.verify(expected)
            if self.fault == 'ambiguous_before':
                raise Denied('ambiguous anchor result')
            self._generation += 1
            if self.fault in ('ambiguous_after', 'lost_result'):
                raise Denied('ambiguous anchor result')
            return self.generation
        return self.coordinator.mutate(perform)


class Issuer:
    """An idealized oracle, not proof of a real non-equivocating issuer."""
    def __init__(self, *, coordinator=None):
        self._coordinator = coordinator
        self._available = True
        self._detected_fork = False
        self._hidden_fork = False
        self._commitments = {}
        self._intents = {}

    @property
    def coordinator(self): return self._coordinator
    @property
    def available(self): return self._available
    @property
    def detected_fork(self): return self._detected_fork
    @property
    def hidden_fork(self): return self._hidden_fork
    @property
    def commitments(self):
        with self.coordinator.lock: return deepcopy(self._commitments)
    @property
    def intents(self):
        with self.coordinator.lock: return deepcopy(self._intents)

    def set_fault(self, name, value):
        def perform():
            need(name in ('available', 'detected_fork', 'hidden_fork') and type(value) is bool, 'invalid issuer fault')
            setattr(self, '_' + name, value)
        self.coordinator.mutate(perform)

    def set_intent(self, operation, intent):
        self.coordinator.mutate(lambda: self._intents.__setitem__(owned(operation), owned(intent)))

    def delete_intent(self, operation):
        self.coordinator.mutate(lambda: self._intents.__delitem__(operation))

    def set_commitment(self, generation, commitment):
        self.coordinator.mutate(lambda: self._commitments.__setitem__(owned(generation), owned(commitment)))

    def delete_commitment(self, generation):
        self.coordinator.mutate(lambda: self._commitments.__delitem__(generation))

    def check(self, head):
        with self.coordinator.lock:
            need(self.available and not self.detected_fork, 'issuer unavailable or fork')
            need(self.commitments.get(head['generation']) == head['commitment'], 'issuer commitment mismatch')

    def assumption_status(self):
        return 'violated' if self.hidden_fork else 'assumed'

    def issue(self, head):
        def perform():
            need(self.available and not self.detected_fork and not self.hidden_fork, 'issuer unavailable or fork')
            generation = head['generation']
            need(generation not in self._commitments or self._commitments[generation] == head['commitment'], 'issuer duplicate generation')
            self._commitments[owned(generation)] = owned(head['commitment'])
        self.coordinator.mutate(perform)


class AuditLog:
    def __init__(self, coordinator):
        self._coordinator = coordinator
        self._records = []

    def append(self, record):
        self._coordinator.mutate(lambda: self._records.append(owned(record)))

    def __getitem__(self, index):
        with self._coordinator.lock: return deepcopy(self._records[index])

    def __iter__(self):
        with self._coordinator.lock: return iter(deepcopy(self._records))

    def __repr__(self):
        with self._coordinator.lock: return repr(self._records)


class Ledger:
    def __init__(self, anchor, issuer, *, coordinator=None):
        need(type(anchor) is Anchor and type(issuer) is Issuer, 'unsupported synthetic authority')
        self._coordinator = coordinator or anchor.coordinator
        need(anchor.coordinator is self.coordinator and issuer.coordinator in (None, self.coordinator), 'mixed coordinator')
        issuer._coordinator = self.coordinator
        self._anchor, self._issuer = anchor, issuer
        self._head = None
        self._intent = None
        self._operations = {}
        self._audit = AuditLog(self.coordinator)

    @property
    def coordinator(self): return self._coordinator
    @property
    def audit(self): return self._audit
    @property
    def anchor(self): return self._anchor
    @property
    def issuer(self): return self._issuer
    @property
    def head(self):
        with self.coordinator.lock: return deepcopy(self._head)
    @property
    def intent(self):
        with self.coordinator.lock: return deepcopy(self._intent)
    @property
    def operations(self):
        with self.coordinator.lock: return deepcopy(self._operations)

    def set_anchor(self, anchor):
        def perform():
            need(anchor is None or (type(anchor) is Anchor and anchor.coordinator is self.coordinator), 'mixed coordinator')
            self._anchor = anchor
        self.coordinator.mutate(perform)

    def set_head(self, head):
        self.coordinator.mutate(lambda: setattr(self, '_head', owned(head)))

    def set_intent(self, intent):
        self.coordinator.mutate(lambda: setattr(self, '_intent', owned(intent)))

    def set_operation(self, operation, value):
        self.coordinator.mutate(lambda: self._operations.__setitem__(owned(operation), owned(value)))

    def delete_operation(self, operation):
        self.coordinator.mutate(lambda: self._operations.__delitem__(operation))

    def set_issuer(self, issuer):
        def perform():
            need(type(issuer) is Issuer and issuer.coordinator is self.coordinator, 'mixed coordinator')
            self._issuer = issuer
        self.coordinator.mutate(perform)

    def validate(self, anchor_identity):
        with self.coordinator.lock:
            need(self.anchor is not None and self.anchor.identity == anchor_identity, 'anchor identity')
            generation = self.anchor.read()
            need(isinstance(self.head, dict) and self.head.get('authenticated') is True, 'authority corrupt')
            need(self.head.get('anchor') == anchor_identity and self.head.get('generation') == generation, 'authority generation mismatch')
            head = self.head
            need(head.get('commitment') == (head.get('scope'), generation, head.get('state'), head.get('operation'), anchor_identity, head.get('time_bound')), 'authority commitment mismatch')
            self.issuer.check(head)
            return self.head

    def commit(self, scope, generation, state, operation, time_bound=False):
        head = {'scope': scope, 'generation': generation, 'state': state, 'operation': operation,
                'anchor': self.anchor.identity, 'authenticated': True, 'time_bound': time_bound,
                'commitment': (scope, generation, state, operation, self.anchor.identity, time_bound)}
        self.issuer.issue(head)
        self.set_head(head)


class Inventory:
    def __init__(self, generation, objects, *, coordinator=None):
        self._coordinator = coordinator
        self._generation = owned(generation)
        self._objects = owned(objects)
        self._fault = None

    @property
    def coordinator(self): return self._coordinator
    @property
    def generation(self): return deepcopy(self._generation)
    @property
    def objects(self):
        with self.coordinator.lock: return deepcopy(self._objects)
    @property
    def fault(self): return deepcopy(self._fault)

    def set_object(self, obj, value):
        self.coordinator.mutate(lambda: self._objects.__setitem__(owned(obj), owned(value)))

    def delete_object(self, obj):
        self.coordinator.mutate(lambda: self._objects.__delitem__(obj))

    def set_generation(self, generation):
        self.coordinator.mutate(lambda: setattr(self, '_generation', owned(generation)))

    def set_fault(self, fault):
        self.coordinator.mutate(lambda: setattr(self, '_fault', owned(fault)))

    def verify(self, generation, scope, obj, version):
        with self.coordinator.lock:
            need(self.fault is None and self.generation == generation, 'inventory unavailable or generation mismatch')
            need(self._objects.get(obj) == (scope, version, 'active'), 'inventory object ineligible')

    def suppress(self, scope, generation):
        def perform():
            self._generation = owned(generation)
            self._objects = {key: (owner, version, 'withdrawn' if owner == scope else state)
                             for key, (owner, version, state) in self._objects.items()}
        self.coordinator.mutate(perform)
