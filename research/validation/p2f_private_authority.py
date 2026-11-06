"""Invented-only authority state machine; no cryptographic or TPM guarantees."""
from copy import deepcopy


class Denied(ValueError):
    pass


def need(ok, reason):
    if not ok:
        raise Denied(reason)


class Anchor:
    def __init__(self, identity):
        self.identity = identity
        self.generation = 0
        self.fault = None

    def read(self):
        need(self.fault not in ('missing', 'unavailable', 'corrupt', 'wrong_identity'), 'anchor unavailable or invalid')
        return self.generation

    def verify(self, expected):
        need(self.read() == expected, 'anchor generation mismatch')
        return True

    def advance(self, expected):
        self.verify(expected)
        if self.fault == 'ambiguous_before':
            raise Denied('ambiguous anchor result')
        self.generation += 1
        if self.fault in ('ambiguous_after', 'lost_result'):
            raise Denied('ambiguous anchor result')
        return self.generation


class Issuer:
    """An idealized oracle, not proof of a real non-equivocating issuer."""
    def __init__(self):
        self.available = True
        self.detected_fork = False
        self.hidden_fork = False
        self.commitments = {}
        self.intents = {}

    def check(self, head):
        need(self.available and not self.detected_fork, 'issuer unavailable or fork')
        need(self.commitments.get(head['generation']) == head['commitment'], 'issuer commitment mismatch')

    def assumption_status(self):
        return 'violated' if self.hidden_fork else 'assumed'

    def issue(self, head):
        need(self.available and not self.detected_fork and not self.hidden_fork, 'issuer unavailable or fork')
        generation = head['generation']
        need(generation not in self.commitments or self.commitments[generation] == head['commitment'], 'issuer duplicate generation')
        self.commitments[generation] = head['commitment']


class Ledger:
    def __init__(self, anchor, issuer):
        self.anchor, self.issuer = anchor, issuer
        self.head = None
        self.intent = None
        self.operations = {}
        self.audit = []

    def validate(self, anchor_identity):
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
        self.head = head


class Inventory:
    def __init__(self, generation, objects):
        self.generation = generation
        self.objects = deepcopy(objects)
        self.fault = None

    def verify(self, generation, scope, obj, version):
        need(self.fault is None and self.generation == generation, 'inventory unavailable or generation mismatch')
        need(self.objects.get(obj) == (scope, version, 'active'), 'inventory object ineligible')

    def suppress(self, scope, generation):
        self.generation = generation
        self.objects = {key: (owner, version, 'withdrawn' if owner == scope else state)
                        for key, (owner, version, state) in self.objects.items()}
