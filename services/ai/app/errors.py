class PermanentProcessingError(ValueError):
    """The request cannot succeed without changing its input."""


class DocumentConflictError(PermanentProcessingError):
    """The requested document operation conflicts with a retained tombstone."""


class ServiceBusyError(RuntimeError):
    """A short-lived service coordination failure that callers may retry."""
